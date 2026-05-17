package com.jwebmp.plugins.angular.maven;

import com.guicedee.client.IGuiceContext;
import com.jwebmp.core.base.angular.client.services.interfaces.INgApp;
import com.jwebmp.core.base.angular.services.compiler.TypeScriptCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.vertx.core.Vertx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Watches for compiled class file changes and regenerates TypeScript, while also
 * running {@code npm run watch} in the Angular app directory for live rebuilds.
 *
 * <p>Usage: {@code mvn jwebmp-angular:watch}</p>
 *
 * <p>This goal is intended for development use. It will block until interrupted (Ctrl+C).</p>
 */
@Mojo(
        name = "watch",
        defaultPhase = LifecyclePhase.NONE,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST,
        requiresProject = true
)
public class AngularWatchMojo extends AbstractMojo {

    @Parameter(property = "jwebmp.angular.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jwebmp.angular.watch.pollInterval", defaultValue = "2000")
    private long pollIntervalMs;

    @Parameter(property = "jwebmp.angular.watch.npmScript", defaultValue = "watch")
    private String npmWatchScript;

    @Parameter(property = "jwebmp.angular.watch.debounce", defaultValue = "500")
    private long debounceMs;

    @Parameter(property = "jwebmp.angular.apps")
    private List<String> appClasses;

    @Parameter(property = "jwebmp.angular.outputDirectory")
    private String outputDirectory;

    @Parameter(property = "jwebmp.angular.classpathScope", defaultValue = "runtime")
    private String classpathScope;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    private String projectOutputDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true, required = true)
    private String testOutputDirectory;

    @Parameter(defaultValue = "${project.runtimeClasspathElements}", readonly = true, required = true)
    private List<String> runtimeClasspathElements;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> compileClasspathElements;

    @Parameter(defaultValue = "${project.testClasspathElements}", readonly = true, required = true)
    private List<String> testClasspathElements;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private Vertx vertx;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping JWebMP Angular watch (jwebmp.angular.skip=true).");
            return;
        }

        configureOutputDirectory();

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader projectClassLoader;
        try {
            projectClassLoader = buildProjectClassLoader();
            Thread.currentThread().setContextClassLoader(projectClassLoader);
        } catch (Exception e) {
            throw new MojoFailureException("Failed to build project classloader for watch mode", e);
        }

        vertx = Vertx.vertx();
        try {
            // Initial TypeScript generation
            Set<INgApp<?>> apps = resolveApps(projectClassLoader);
            if (apps.isEmpty()) {
                getLog().warn("No @NgApp implementations found; nothing to watch.");
                return;
            }

            getLog().info("Performing initial TypeScript generation...");
            regenerateTypeScript(apps);

            // Start npm run watch for each app
            List<Process> npmProcesses = startNpmWatch(apps);

            // Watch for class file changes
            Path classesDir = Path.of(projectOutputDirectory);
            if (!Files.isDirectory(classesDir)) {
                getLog().warn("Output directory does not exist yet: " + classesDir + " — waiting for it to appear...");
                while (running.get() && !Files.isDirectory(classesDir)) {
                    Thread.sleep(pollIntervalMs);
                }
            }

            getLog().info("Watching for .class file changes in: " + classesDir);
            getLog().info("Press Ctrl+C to stop.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                for (Process p : npmProcesses) {
                    p.destroyForcibly();
                }
            }));

            watchForChanges(classesDir, apps);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLog().info("Watch interrupted.");
        } catch (Exception e) {
            throw new MojoExecutionException("Error during watch mode", e);
        } finally {
            if (vertx != null) {
                CountDownLatch closeLatch = new CountDownLatch(1);
                vertx.close().onComplete(ar -> closeLatch.countDown());
                try { closeLatch.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            }
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private void watchForChanges(Path classesDir, Set<INgApp<?>> apps) throws IOException, InterruptedException {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            registerRecursive(classesDir, watchService);

            while (running.get()) {
                WatchKey key = watchService.poll(pollIntervalMs, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }

                boolean classChanged = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        classChanged = true;
                        break;
                    }
                    Path changed = ((WatchEvent<Path>) event).context();
                    if (changed != null && changed.toString().endsWith(".class")) {
                        classChanged = true;
                        getLog().debug("Detected change: " + changed);
                    }
                    // Register new directories
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path dir = ((Path) key.watchable()).resolve(changed);
                        if (Files.isDirectory(dir)) {
                            registerRecursive(dir, watchService);
                        }
                    }
                }
                key.reset();

                if (classChanged) {
                    // Debounce — drain any additional events
                    Thread.sleep(debounceMs);
                    // Drain remaining events
                    WatchKey extra;
                    while ((extra = watchService.poll()) != null) {
                        extra.pollEvents();
                        extra.reset();
                    }

                    getLog().info("Class file change detected — regenerating TypeScript...");
                    try {
                        regenerateTypeScript(apps);
                        getLog().info("TypeScript regeneration complete.");
                    } catch (Exception e) {
                        getLog().error("TypeScript regeneration failed: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private void registerRecursive(Path root, WatchService watchService) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void regenerateTypeScript(Set<INgApp<?>> apps) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        vertx.runOnContext(v -> {
            try {
                for (INgApp<?> app : apps) {
                    TypeScriptCompiler compiler = new TypeScriptCompiler(app);
                    compiler.compileApp();
                }
            } catch (Throwable e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout during TypeScript regeneration");
        }
        if (error.get() != null) {
            throw new Exception("TypeScript regeneration failed", error.get());
        }
    }

    private List<Process> startNpmWatch(Set<INgApp<?>> apps) {
        List<Process> processes = new ArrayList<>();
        for (INgApp<?> app : apps) {
            File appDir = com.jwebmp.core.base.angular.client.AppUtils
                    .getAppPath((Class<? extends INgApp<?>>) app.getClass());
            if (appDir == null || !appDir.isDirectory()) {
                getLog().warn("App directory not found for " + app.getClass().getName() + "; skipping npm watch.");
                continue;
            }

            File packageJson = new File(appDir, "package.json");
            if (!packageJson.isFile()) {
                getLog().info("No package.json in " + appDir + "; skipping npm watch for this app.");
                continue;
            }

            getLog().info("Starting 'npm run " + npmWatchScript + "' in " + appDir.getAbsolutePath());
            try {
                boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
                ProcessBuilder pb;
                if (isWindows) {
                    pb = new ProcessBuilder("cmd", "/c", "npm", "run", npmWatchScript);
                } else {
                    pb = new ProcessBuilder("npm", "run", npmWatchScript);
                }
                pb.directory(appDir);
                pb.environment().putAll(System.getenv());
                Process process = pb.start();
                streamAsync(process.getInputStream(), false, app.name());
                streamAsync(process.getErrorStream(), true, app.name());
                processes.add(process);
            } catch (IOException e) {
                getLog().error("Failed to start npm watch for " + app.name() + ": " + e.getMessage(), e);
            }
        }
        return processes;
    }

    private void streamAsync(InputStream stream, boolean error, String appName) {
        String prefix = "[npm:" + appName + "] ";
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (error) {
                        getLog().warn(prefix + line);
                    } else {
                        getLog().info(prefix + line);
                    }
                }
            } catch (IOException e) {
                // stream closed
            }
        }, "npm-watch-" + appName + (error ? "-stderr" : "-stdout"));
        thread.setDaemon(true);
        thread.start();
    }

    // ── Classpath and configuration methods (shared logic with build mojo) ────

    private void configureOutputDirectory() {
        if (outputDirectory != null && !outputDirectory.isBlank()) {
            File resolved = new File(outputDirectory);
            if (!resolved.isAbsolute() && project != null && project.getBasedir() != null) {
                resolved = new File(project.getBasedir(), outputDirectory);
            }
            System.setProperty("jwebmp.outputDirectory", resolved.getAbsolutePath());
            if (System.getProperty("jwebmp") == null || System.getProperty("jwebmp").isBlank()) {
                System.setProperty("jwebmp", resolved.getAbsolutePath());
            }
            return;
        }
        if (System.getProperty("jwebmp.outputDirectory") == null || System.getProperty("jwebmp.outputDirectory").isBlank()) {
            String defaultDir = project != null && project.getBuild() != null ? project.getBuild().getDirectory() : null;
            if (defaultDir != null && !defaultDir.isBlank()) {
                File resolved = new File(defaultDir);
                System.setProperty("jwebmp.outputDirectory", resolved.getAbsolutePath());
                if (System.getProperty("jwebmp") == null || System.getProperty("jwebmp").isBlank()) {
                    System.setProperty("jwebmp", resolved.getAbsolutePath());
                }
            }
        }
    }

    private ClassLoader buildProjectClassLoader() throws Exception {
        LinkedHashSet<URL> urls = new LinkedHashSet<>();
        String scope = classpathScope != null ? classpathScope.trim().toLowerCase() : "runtime";

        switch (scope) {
            case "compile" -> {
                addOutputDirectory(urls, projectOutputDirectory);
                addClasspathElements(urls, compileClasspathElements);
            }
            case "test" -> {
                addOutputDirectory(urls, projectOutputDirectory);
                addOutputDirectory(urls, testOutputDirectory);
                addClasspathElements(urls, testClasspathElements);
            }
            default -> {
                addOutputDirectory(urls, projectOutputDirectory);
                addClasspathElements(urls, runtimeClasspathElements);
            }
        }

        ClassLoader pluginClassLoader = getClass().getClassLoader();
        Set<String> pluginPaths = collectClassloaderPaths(pluginClassLoader);
        urls.removeIf(url -> {
            try {
                return pluginPaths.contains(Path.of(url.toURI()).normalize().toString());
            } catch (URISyntaxException e) {
                return false;
            }
        });

        return new URLClassLoader(urls.toArray(new URL[0]), pluginClassLoader);
    }

    private Set<String> collectClassloaderPaths(ClassLoader loader) {
        Set<String> paths = new LinkedHashSet<>();
        for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL url : ucl.getURLs()) {
                    try {
                        paths.add(Path.of(url.toURI()).normalize().toString());
                    } catch (URISyntaxException e) {
                        // skip
                    }
                }
            }
        }
        return paths;
    }

    private void addClasspathElements(LinkedHashSet<URL> urls, List<String> elements) throws Exception {
        if (elements == null) return;
        for (String element : elements) {
            if (element != null && !element.isBlank()) {
                urls.add(new File(element).toURI().toURL());
            }
        }
    }

    private void addOutputDirectory(LinkedHashSet<URL> urls, String directory) throws Exception {
        if (directory != null && !directory.isBlank()) {
            File dir = new File(directory);
            if (dir.exists()) {
                urls.add(dir.toURI().toURL());
            }
        }
    }

    private Set<INgApp<?>> resolveApps(ClassLoader projectClassLoader) throws Exception {
        List<String> resolvedApps = new ArrayList<>();
        if (appClasses != null && !appClasses.isEmpty()) {
            resolvedApps.addAll(appClasses);
        } else {
            String appClassesCsv = System.getProperty("jwebmp.angular.apps");
            if (appClassesCsv != null && !appClassesCsv.isBlank()) {
                for (String entry : appClassesCsv.split(",")) {
                    String trimmed = entry.trim();
                    if (!trimmed.isEmpty()) {
                        resolvedApps.add(trimmed);
                    }
                }
            } else {
                return TypeScriptCompiler.getAllApps();
            }
        }

        Set<INgApp<?>> apps = new LinkedHashSet<>();
        for (String className : resolvedApps) {
            Class<?> appClass = Class.forName(className, true, projectClassLoader);
            Object instance = IGuiceContext.get(appClass);
            if (instance instanceof INgApp<?>) {
                apps.add((INgApp<?>) instance);
            } else {
                getLog().warn("Configured app class does not implement INgApp: " + className);
            }
        }
        return apps;
    }
}







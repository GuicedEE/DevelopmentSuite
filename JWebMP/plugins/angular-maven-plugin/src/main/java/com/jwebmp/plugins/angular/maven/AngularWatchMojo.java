package com.jwebmp.plugins.angular.maven;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches for source file changes (Java, resources) and compiled class file changes,
 * triggering recompilation and TypeScript regeneration as needed, while also
 * running {@code npm run watch} in the Angular app directory for live rebuilds.
 *
 * <p>Full development loop:</p>
 * <ol>
 *   <li>Edit Java source files</li>
 *   <li>Source watcher detects changes → triggers Maven compile</li>
 *   <li>Class file watcher detects new .class files → regenerates TypeScript</li>
 *   <li>npm watch picks up TypeScript changes → rebuilds Angular app</li>
 * </ol>
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

    /**
     * When true, the plugin watches Java source directories for changes and automatically
     * triggers a Maven compile when source files are modified. The resulting .class file
     * changes will then trigger TypeScript regeneration.
     * <p>Defaults to {@code true}.</p>
     */
    @Parameter(property = "jwebmp.angular.watch.sources", defaultValue = "true")
    private boolean watchSources;

    /**
     * Additional source directories to watch beyond the project's configured compile source roots.
     * Paths are resolved relative to the project base directory if not absolute.
     */
    @Parameter(property = "jwebmp.angular.watch.sourceDirectories")
    private List<String> additionalSourceDirectories;

    /**
     * File extensions to monitor in source directories. Changes to files with these extensions
     * trigger a recompilation.
     * <p>Defaults to: java, kt, groovy, scala, properties, xml, yml, yaml, json</p>
     */
    @Parameter(property = "jwebmp.angular.watch.sourceExtensions")
    private List<String> sourceExtensions;

    /**
     * The Maven goals to execute when source file changes are detected.
     * <p>Defaults to {@code compile}.</p>
     */
    @Parameter(property = "jwebmp.angular.watch.compileGoals", defaultValue = "compile")
    private String compileGoals;

    /**
     * Additional Maven arguments to pass when triggering a compile on source changes.
     * For example: {@code -DskipTests -q}
     */
    @Parameter(property = "jwebmp.angular.watch.compileArgs")
    private List<String> compileArgs;

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

    /**
     * Cooldown period after a generation completes, during which class file changes are ignored.
     * This prevents the loop: generate TS → npm rebuild → IDE recompile → detect .class change → regenerate.
     */
    @Parameter(property = "jwebmp.angular.watch.cooldown", defaultValue = "5000")
    private long cooldownMs;

    /**
     * Cooldown period after a source-triggered compile completes, during which source file
     * changes are ignored. Prevents rapid recompilation if the IDE writes multiple files.
     */
    @Parameter(property = "jwebmp.angular.watch.compileCooldown", defaultValue = "3000")
    private long compileCooldownMs;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean generating = new AtomicBoolean(false);
    private final AtomicBoolean compiling = new AtomicBoolean(false);
    private volatile long lastGenerationEndTime = 0;
    private volatile long lastCompileEndTime = 0;

    /**
     * Thread-safe list of all npm watch processes started by this mojo.
     * Used to ensure cleanup in all exit paths.
     */
    private final List<Process> npmProcesses = new CopyOnWriteArrayList<>();

    /**
     * The resolved NgApp instances — set once during execute() and read from the source watcher thread
     * to trigger TypeScript regeneration after a successful compile.
     */
    private volatile Set<INgApp<?>> resolvedApps;

    private static final Set<String> DEFAULT_SOURCE_EXTENSIONS = Set.of(
            "java"
    );

    /**
     * Tracks which watched directories are resource directories.
     * Any file change in a resource directory triggers a recompile regardless of extension.
     */
    private final Set<Path> resourceDirectories = new LinkedHashSet<>();

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

        // Register the shutdown hook early so it covers all exit paths
        Thread shutdownHook = new Thread(() -> {
            running.set(false);
            destroyAllNpmProcesses();
        }, "jwebmp-watch-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            // Initial TypeScript generation — use the same pattern as the build mojo:
            // single classloader, direct compilation without rebuilding the classloader.
            Set<INgApp<?>> apps = resolveApps(projectClassLoader);
            if (apps.isEmpty()) {
                getLog().warn("No @NgApp implementations found; nothing to watch.");
                return;
            }
            this.resolvedApps = apps;

            getLog().info("Performing initial TypeScript generation...");
            for (INgApp<?> app : apps) {
                try {
                    TypeScriptCompiler compiler = new TypeScriptCompiler(app);
                    compiler.compileApp();
                } catch (NoClassDefFoundError e) {
                    throw new MojoExecutionException(
                            "Failed to load a required class while building Angular TypeScript for app: " + app.getClass().getName()
                            + ". Missing class: " + e.getMessage()
                            + ". This usually means a dependency is not on the resolved classpath."
                            + " Try setting jwebmp.angular.classpathScope=compile or adding the missing dependency.",
                            e);
                }
            }
            lastGenerationEndTime = System.currentTimeMillis();
            getLog().info("Initial generation complete. Waiting for cooldown before starting watch...");

            // Validate essential generated files exist before starting npm
            for (INgApp<?> appInstance : resolvedApps) {
                File appDir = com.jwebmp.core.base.angular.client.AppUtils
                        .getAppPath((Class<? extends INgApp<?>>) appInstance.getClass());
                if (appDir != null) {
                    File indexHtml = new File(appDir, "src/index.html");
                    if (!indexHtml.isFile()) {
                        throw new MojoFailureException(
                                "TypeScript generation did not produce src/index.html at " + indexHtml.getAbsolutePath()
                                + ". Check the build log above for errors during Angular compilation.");
                    }
                    File angularJson = new File(appDir, "angular.json");
                    if (!angularJson.isFile()) {
                        throw new MojoFailureException(
                                "TypeScript generation did not produce angular.json at " + angularJson.getAbsolutePath()
                                + ". Check the build log above for errors during Angular compilation.");
                    }
                }
            }

            // Start npm run watch for each app (after cooldown so npm doesn't immediately re-trigger)
            Thread.sleep(cooldownMs);
            startNpmWatch(apps);

            // Start source file watcher thread if enabled
            Thread sourceWatcherThread = null;
            if (watchSources) {
                List<Path> sourceDirs = resolveSourceDirectories();
                if (!sourceDirs.isEmpty()) {
                    sourceWatcherThread = new Thread(() -> {
                        try {
                            watchSourceFiles(sourceDirs);
                        } catch (IOException e) {
                            getLog().error("Source file watcher failed: " + e.getMessage(), e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "jwebmp-source-watcher");
                    sourceWatcherThread.setDaemon(true);
                    sourceWatcherThread.start();
                    getLog().info("Source file watcher started for " + sourceDirs.size() + " director" + (sourceDirs.size() == 1 ? "y" : "ies") + ".");
                } else {
                    getLog().info("No source directories found to watch.");
                }
            }

            // Watch for class file changes
            Path classesDir = Path.of(projectOutputDirectory);
            if (!Files.isDirectory(classesDir)) {
                getLog().warn("Output directory does not exist yet: " + classesDir + " — waiting for it to appear...");
                while (running.get() && !Files.isDirectory(classesDir)) {
                    Thread.sleep(pollIntervalMs);
                }
            }

            getLog().info("Watching for .class file changes in: " + classesDir);
            if (watchSources) {
                getLog().info("Watching for source file changes in project source roots.");
            }
            getLog().info("Press Ctrl+C to stop.");

            watchForChanges(classesDir, apps);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLog().info("Watch interrupted.");
        } catch (Exception e) {
            throw new MojoExecutionException("Error during watch mode", e);
        } finally {
            running.set(false);
            destroyAllNpmProcesses();
            Thread.currentThread().setContextClassLoader(original);
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down
            }
        }
    }

    // ── Source file watching ──────────────────────────────────────────────────

    /**
     * Resolves the source directories to watch. Includes the project's compile source roots
     * and any additionally configured directories.
     */
    private List<Path> resolveSourceDirectories() {
        List<Path> dirs = new ArrayList<>();

        // Add Maven compile source roots (typically src/main/java)
        if (project != null && project.getCompileSourceRoots() != null) {
            for (String root : project.getCompileSourceRoots()) {
                if (root != null && !root.isBlank()) {
                    Path p = Path.of(root);
                    if (Files.isDirectory(p)) {
                        dirs.add(p);
                        getLog().debug("Watching source root: " + p);
                    }
                }
            }
        }

        // Add resource directories
        if (project != null && project.getResources() != null) {
            for (var resource : project.getResources()) {
                String dir = resource.getDirectory();
                if (dir != null && !dir.isBlank()) {
                    Path p = Path.of(dir);
                    if (Files.isDirectory(p)) {
                        dirs.add(p);
                        resourceDirectories.add(p);
                        getLog().debug("Watching resource directory: " + p);
                    }
                }
            }
        }

        // Add user-configured additional source directories
        if (additionalSourceDirectories != null) {
            for (String additional : additionalSourceDirectories) {
                if (additional == null || additional.isBlank()) continue;
                File resolved = new File(additional);
                if (!resolved.isAbsolute() && project != null && project.getBasedir() != null) {
                    resolved = new File(project.getBasedir(), additional);
                }
                Path p = resolved.toPath();
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                    getLog().debug("Watching additional source directory: " + p);
                } else {
                    getLog().warn("Configured additional source directory does not exist: " + resolved.getAbsolutePath());
                }
            }
        }

        return dirs;
    }

    /**
     * Returns the set of file extensions that trigger a recompile when changed.
     */
    private Set<String> getWatchedSourceExtensions() {
        if (sourceExtensions != null && !sourceExtensions.isEmpty()) {
            Set<String> exts = new LinkedHashSet<>();
            for (String ext : sourceExtensions) {
                if (ext != null && !ext.isBlank()) {
                    exts.add(ext.trim().toLowerCase());
                }
            }
            return exts;
        }
        return DEFAULT_SOURCE_EXTENSIONS;
    }

    /**
     * Watches source directories for file changes. When a relevant file changes, triggers
     * a Maven compile.
     */
    private void watchSourceFiles(List<Path> sourceDirs) throws IOException, InterruptedException {
        Set<String> extensions = getWatchedSourceExtensions();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            for (Path dir : sourceDirs) {
                registerRecursive(dir, watchService);
            }

            getLog().info("Source watcher monitoring extensions: " + extensions);

            while (running.get()) {
                WatchKey key = watchService.poll(pollIntervalMs, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }

                boolean sourceChanged = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        sourceChanged = true;
                        break;
                    }
                    Path changed = ((WatchEvent<Path>) event).context();
                    Path watchedDir = (Path) key.watchable();
                    if (changed != null) {
                        // Any file in a resource directory triggers recompile
                        if (isUnderResourceDirectory(watchedDir)) {
                            sourceChanged = true;
                            getLog().debug("Resource file changed: " + changed);
                        } else if (isWatchedSourceFile(changed.toString(), extensions)) {
                            sourceChanged = true;
                            getLog().debug("Source file changed: " + changed);
                        }
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

                if (sourceChanged) {
                    // Skip if still within compile cooldown
                    long elapsed = System.currentTimeMillis() - lastCompileEndTime;
                    if (elapsed < compileCooldownMs) {
                        getLog().debug("Ignoring source change — within compile cooldown (" + elapsed + "ms < " + compileCooldownMs + "ms)");
                        continue;
                    }

                    // Skip if within generation cooldown (generation just produced files)
                    long elapsedGen = System.currentTimeMillis() - lastGenerationEndTime;
                    if (elapsedGen < cooldownMs) {
                        getLog().debug("Ignoring source change — within generation cooldown (" + elapsedGen + "ms < " + cooldownMs + "ms)");
                        continue;
                    }

                    // Skip if a compile is already in progress
                    if (!compiling.compareAndSet(false, true)) {
                        getLog().debug("Ignoring source change — compile already in progress");
                        continue;
                    }

                    // Debounce — wait for IDE to finish writing multiple files
                    Thread.sleep(debounceMs);
                    // Drain remaining events
                    WatchKey extra;
                    while ((extra = watchService.poll()) != null) {
                        extra.pollEvents();
                        extra.reset();
                    }

                    getLog().info("Source file change detected — triggering Maven compile...");
                    try {
                        int exitCode = runMavenCompile();
                        if (exitCode == 0) {
                            getLog().info("Maven compile completed successfully. Regenerating TypeScript...");
                            // Explicitly regenerate TypeScript after successful compile
                            // rather than relying solely on the class file watcher
                            if (resolvedApps != null && !resolvedApps.isEmpty()) {
                                if (generating.compareAndSet(false, true)) {
                                    try {
                                        regenerateTypeScript(resolvedApps);
                                        getLog().info("TypeScript regeneration complete.");
                                    } catch (Exception ex) {
                                        getLog().error("TypeScript regeneration failed: " + ex.getMessage(), ex);
                                    } finally {
                                        lastGenerationEndTime = System.currentTimeMillis();
                                        generating.set(false);
                                    }
                                } else {
                                    getLog().debug("Skipping TypeScript regeneration — already in progress.");
                                }
                            }
                        } else {
                            getLog().warn("Maven compile exited with code " + exitCode + " — check output above for errors.");
                        }
                    } catch (Exception e) {
                        getLog().error("Maven compile failed: " + e.getMessage(), e);
                    } finally {
                        lastCompileEndTime = System.currentTimeMillis();
                        compiling.set(false);
                    }
                }
            }
        }
    }

    /**
     * Checks if a filename has a watched extension.
     */
    private boolean isWatchedSourceFile(String fileName, Set<String> extensions) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return extensions.contains(ext);
    }

    /**
     * Checks if the given directory is under (or is) a resource directory.
     * Any file change in a resource directory triggers a recompile regardless of extension.
     */
    private boolean isUnderResourceDirectory(Path dir) {
        for (Path resourceDir : resourceDirectories) {
            if (dir.startsWith(resourceDir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs a Maven compile using the Maven wrapper (if available) or the system Maven command.
     * Inherits the project's base directory and environment.
     *
     * @return the process exit code
     */
    private int runMavenCompile() {
        File baseDir = project != null ? project.getBasedir() : new File(".");
        String mvnCmd = resolveMavenCommand(baseDir);
        String goals = (compileGoals != null && !compileGoals.isBlank()) ? compileGoals : "compile";

        List<String> command = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows) {
            command.add("cmd");
            command.add("/c");
        }
        command.add(mvnCmd);

        // Explicitly point Maven at this module's pom.xml so the wrapper doesn't
        // walk up to a parent .mvn directory and compile the entire reactor
        File modulePom = new File(baseDir, "pom.xml");
        if (modulePom.isFile()) {
            command.add("-f");
            command.add(modulePom.getAbsolutePath());
        }

        // Split goals on whitespace/comma
        for (String goal : goals.split("[,\\s]+")) {
            String trimmed = goal.trim();
            if (!trimmed.isEmpty()) {
                command.add(trimmed);
            }
        }

        // Add user-configured extra arguments
        if (compileArgs != null) {
            for (String arg : compileArgs) {
                if (arg != null && !arg.isBlank()) {
                    command.add(arg.trim());
                }
            }
        }

        // Always skip this plugin during the child compile to avoid infinite recursion
        command.add("-Djwebmp.angular.skip=true");
        // Prevent the wrapper from resolving to a parent project base directory
        command.add("-Dmaven.multiModuleProjectDirectory=" + baseDir.getAbsolutePath());

        getLog().info("Running: " + String.join(" ", command) + " in " + baseDir.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(baseDir);
            pb.environment().putAll(System.getenv());
            Process process = pb.start();
            streamAsync(process.getInputStream(), false, "mvn");
            streamAsync(process.getErrorStream(), true, "mvn");

            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                getLog().error("Maven compile timed out after 5 minutes.");
                return -1;
            }
            return process.exitValue();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            getLog().error("Error running Maven compile: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Resolves the Maven command to use. Prefers the Maven wrapper (mvnw/mvnw.cmd) in the
     * project directory or parent directories, falls back to system 'mvn'.
     */
    private String resolveMavenCommand(File baseDir) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String wrapperName = isWindows ? "mvn.cmd" : "mvn";

        // Search up from project base dir
        File dir = baseDir;
        while (dir != null) {
            File wrapper = new File(dir, wrapperName);
            if (wrapper.isFile()) {
                return wrapper.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }

        return isWindows ? "mvn.cmd" : "mvn";
    }

    // ── Class file watching ──────────────────────────────────────────────────

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
                    // Skip if still within cooldown period from last generation
                    long elapsed = System.currentTimeMillis() - lastGenerationEndTime;
                    if (elapsed < cooldownMs) {
                        getLog().debug("Ignoring .class change — within cooldown (" + elapsed + "ms < " + cooldownMs + "ms)");
                        continue;
                    }

                    // Skip if a generation is already in progress
                    if (!generating.compareAndSet(false, true)) {
                        getLog().debug("Ignoring .class change — generation already in progress");
                        continue;
                    }

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
                    } finally {
                        lastGenerationEndTime = System.currentTimeMillis();
                        generating.set(false);
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
        // Rebuild the classloader to pick up newly compiled .class files
        // The URLClassLoader caches classes, so we must create a fresh one
        ClassLoader freshClassLoader = buildProjectClassLoader();
        ClassLoader previousCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(freshClassLoader);
        try {
            // Re-resolve apps with the fresh classloader so TypeScriptCompiler sees updated code
            Set<INgApp<?>> freshApps = resolveApps(freshClassLoader);
            if (freshApps.isEmpty()) {
                getLog().warn("No @NgApp implementations found after recompile; using original apps.");
                freshApps = apps;
            }
            // Update the resolvedApps reference for future cycles
            this.resolvedApps = freshApps;

            for (INgApp<?> app : freshApps) {
                TypeScriptCompiler compiler = new TypeScriptCompiler(app);
                compiler.compileApp();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousCL);
        }
    }

    private void startNpmWatch(Set<INgApp<?>> apps) {
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
                npmProcesses.add(process);
                streamAsync(process.getInputStream(), false, app.name());
                streamAsync(process.getErrorStream(), true, app.name());

                // Monitor thread: remove dead processes from the list
                Thread monitor = new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        npmProcesses.remove(process);
                        if (running.get()) {
                            getLog().warn("npm watch process for '" + app.name() + "' exited with code " + process.exitValue());
                        }
                    }
                }, "jwebmp-npm-monitor-" + app.name());
                monitor.setDaemon(true);
                monitor.start();
            } catch (IOException e) {
                getLog().error("Failed to start npm watch for " + app.name() + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Destroys all npm watch processes and their entire process trees.
     * This method is safe to call multiple times and from any thread.
     */
    private void destroyAllNpmProcesses() {
        List<Process> snapshot = new ArrayList<>(npmProcesses);
        if (snapshot.isEmpty()) {
            return;
        }
        getLog().info("Terminating " + snapshot.size() + " npm watch process(es)...");

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        for (Process process : snapshot) {
            if (!process.isAlive()) {
                npmProcesses.remove(process);
                continue;
            }

            try {
                // Destroy the entire process tree (descendants first, then root)
                long pid = process.pid();
                if (isWindows) {
                    // On Windows, use taskkill /T to kill the entire process tree including
                    // grandchild node.exe processes that cmd.exe spawns
                    try {
                        new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                                .redirectErrorStream(true)
                                .start()
                                .waitFor(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        getLog().debug("taskkill failed for PID " + pid + ", falling back to destroyForcibly: " + e.getMessage());
                        destroyProcessTree(process);
                    }
                } else {
                    // On Unix, destroy descendants then the process itself
                    destroyProcessTree(process);
                }
            } catch (Exception e) {
                getLog().debug("Error during process cleanup: " + e.getMessage());
                // Last resort
                process.destroyForcibly();
            }

            // Wait briefly for termination
            try {
                boolean exited = process.waitFor(5, TimeUnit.SECONDS);
                if (!exited) {
                    getLog().warn("npm process (PID " + process.pid() + ") did not terminate within 5 seconds; forcing kill.");
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            npmProcesses.remove(process);
        }
        getLog().info("All npm watch processes terminated.");
    }

    /**
     * Destroys a process and all its descendants using the Java ProcessHandle API.
     */
    private void destroyProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        // Destroy descendants first (depth-first)
        handle.descendants().forEach(child -> {
            try {
                child.destroyForcibly();
            } catch (Exception e) {
                // ignore individual failures
            }
        });
        // Then destroy the root process
        handle.destroyForcibly();
    }

    private void streamAsync(InputStream stream, boolean error, String prefix) {
        String logPrefix = "[" + prefix + "] ";
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (error) {
                        getLog().warn(logPrefix + line);
                    } else {
                        getLog().info(logPrefix + line);
                    }
                }
            } catch (IOException e) {
                // stream closed
            }
        }, "jwebmp-watch-" + prefix + (error ? "-stderr" : "-stdout"));
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
            setOutputDirectoryProperty(resolved.getAbsolutePath(), "configured");
            return;
        }

        String existingOutput = System.getProperty("jwebmp.outputDirectory");
        if (existingOutput != null && !existingOutput.isBlank()) {
            getLog().info("Using JWebMP output directory from system property: " + existingOutput);
            return;
        }

        String envOutput = System.getenv("jwebmp.outputDirectory");
        if (envOutput != null && !envOutput.isBlank()) {
            getLog().info("Using JWebMP output directory from environment: " + envOutput);
            return;
        }

        String existing = System.getProperty("jwebmp");
        if (existing != null && !existing.isBlank()) {
            getLog().info("Using JWebMP base directory from system property: " + existing);
            return;
        }

        String env = System.getenv("jwebmp");
        if (env != null && !env.isBlank()) {
            getLog().info("Using JWebMP base directory from environment: " + env);
            return;
        }

        String defaultDir = project != null && project.getBuild() != null ? project.getBuild().getDirectory() : null;
        if (defaultDir != null && !defaultDir.isBlank()) {
            File resolved = new File(defaultDir);
            if (!resolved.isAbsolute() && project != null && project.getBasedir() != null) {
                resolved = new File(project.getBasedir(), defaultDir);
            }
            setOutputDirectoryProperty(resolved.getAbsolutePath(), "defaulted");
        }
    }

    private void setOutputDirectoryProperty(String resolved, String source) {
        System.setProperty("jwebmp.outputDirectory", resolved);
        String existingBase = System.getProperty("jwebmp");
        if (existingBase == null || existingBase.isBlank()) {
            System.setProperty("jwebmp", resolved);
        }
        getLog().info("JWebMP output directory " + source + " to " + resolved);
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
        // Also include paths from the boot module layer (JDK modules + any --module-path entries)
        for (Module m : ModuleLayer.boot().modules()) {
            m.getLayer().configuration().modules().forEach(rm -> {
                rm.reference().location().ifPresent(uri -> {
                    try {
                        paths.add(Path.of(uri).normalize().toString());
                    } catch (Exception e) {
                        // jrt:/ or non-file URI; skip
                    }
                });
            });
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
            Object instance = appClass.getDeclaredConstructor().newInstance();
            if (instance instanceof INgApp<?>) {
                apps.add((INgApp<?>) instance);
            } else {
                getLog().warn("Configured app class does not implement INgApp: " + className);
            }
        }
        return apps;
    }
}



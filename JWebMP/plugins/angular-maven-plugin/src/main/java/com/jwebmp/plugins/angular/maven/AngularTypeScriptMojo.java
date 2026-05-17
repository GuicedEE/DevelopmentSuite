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

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Mojo(
        name = "build",
        defaultPhase = LifecyclePhase.INSTALL,
        threadSafe = true,
        // Use TEST to ensure we can assemble the full classpath for any selected scope
        // (test is a superset that includes compile and runtime). This avoids missing
        // classes when users request test/compile/runtime scopes.
        requiresDependencyResolution = ResolutionScope.TEST,
        requiresProject = true
)
public class AngularTypeScriptMojo extends AbstractMojo {
    @Parameter(property = "jwebmp.angular.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jwebmp.angular.install", defaultValue = "false")
    private boolean installDependencies;

    @Parameter(property = "jwebmp.angular.install.force", defaultValue = "false")
    private boolean installForce;

    @Parameter(property = "jwebmp.angular.ensureToolchain", defaultValue = "false")
    private boolean ensureToolchain;

    @Parameter(property = "jwebmp.angular.downloadNpm", defaultValue = "false")
    private boolean downloadNpm;

    @Parameter(property = "jwebmp.angular.nodeVersion", defaultValue = "20.18.1")
    private String nodeVersion;

    @Parameter(property = "jwebmp.angular.angularCliVersion")
    private String angularCliVersion;

    @Parameter(property = "jwebmp.angular.build", defaultValue = "false")
    private boolean buildAngular;

    /**
     * When true the plugin will generate a Dockerfile (if one does not already exist) and run
     * {@code docker build} to produce an OCI image containing the Angular production build output
     * served by nginx. Requires the Angular build to have completed first — enable
     * {@code buildAngular} together with this flag, or run this goal after the Angular build output
     * already exists on disk.
     */
    @Parameter(property = "jwebmp.angular.docker", defaultValue = "false")
    private boolean buildDockerImage;

    /**
     * Docker image name (repository[:tag]). When not set the plugin derives a name from the Maven
     * project: {@code ${project.artifactId}:${project.version}}.
     */
    @Parameter(property = "jwebmp.angular.docker.imageName")
    private String dockerImageName;

    /**
     * Base Docker image for the generated Dockerfile.
     * Defaults to {@code nginx:alpine}.
     */
    @Parameter(property = "jwebmp.angular.docker.baseImage", defaultValue = "nginx:alpine")
    private String dockerBaseImage;

    /**
     * Path to an existing Dockerfile. When set the plugin will use this file instead of generating
     * one. The path is resolved relative to the project base directory when not absolute.
     */
    @Parameter(property = "jwebmp.angular.docker.dockerfile")
    private String dockerfilePath;

    /**
     * Additional arguments to pass to {@code docker build}. Each element is passed as a separate
     * argument. For example: {@code --no-cache}, {@code --build-arg FOO=bar}.
     */
    @Parameter(property = "jwebmp.angular.docker.args")
    private List<String> dockerBuildArgs;

    /**
     * When true the generated nginx configuration includes an HTTPS server block listening on
     * port 443 with TLS. The certificate and key must be provided via {@code nginxSslCertificate}
     * and {@code nginxSslCertificateKey}. An HTTP-to-HTTPS redirect is added automatically
     * unless {@code nginxHttpRedirect} is set to {@code false}.
     */
    @Parameter(property = "jwebmp.angular.nginx.https", defaultValue = "false")
    private boolean nginxHttps;

    /**
     * HTTP port exposed by the generated Docker image and used by the generated nginx HTTP server
     * block. Defaults to {@code 80}.
     */
    @Parameter(property = "jwebmp.angular.nginx.httpPort", defaultValue = "80")
    private int nginxHttpPort;

    /**
     * When true the generated image installs certbot and uses Let's Encrypt certificates for the
     * configured domain. This implies {@code nginxHttps}; certificate paths are generated as
     * {@code /etc/letsencrypt/live/<primary-domain>/fullchain.pem} and
     * {@code /etc/letsencrypt/live/<primary-domain>/privkey.pem}.
     */
    @Parameter(property = "jwebmp.angular.nginx.letsencrypt", defaultValue = "false")
    private boolean nginxLetsEncrypt;

    /**
     * Domain names to request from Let's Encrypt. When unset, {@code nginxServerName} is split on
     * commas/whitespace and used as the domain list.
     */
    @Parameter(property = "jwebmp.angular.nginx.letsencrypt.domains")
    private List<String> nginxLetsEncryptDomains;

    /**
     * Email address used for Let's Encrypt registration and expiry notices. When unset certbot is
     * run with {@code --register-unsafely-without-email}.
     */
    @Parameter(property = "jwebmp.angular.nginx.letsencrypt.email")
    private String nginxLetsEncryptEmail;

    /**
     * Use the Let's Encrypt staging endpoint. Useful for testing container startup without hitting
     * production rate limits.
     */
    @Parameter(property = "jwebmp.angular.nginx.letsencrypt.staging", defaultValue = "false")
    private boolean nginxLetsEncryptStaging;

    /**
     * Path to the SSL certificate <em>inside the container</em>.
     * Only used when {@code nginxHttps} is {@code true}.
     * Defaults to {@code /etc/nginx/ssl/server.crt}.
     */
    @Parameter(property = "jwebmp.angular.nginx.sslCertificate", defaultValue = "/etc/nginx/ssl/server.crt")
    private String nginxSslCertificate;

    /**
     * Path to the SSL private key <em>inside the container</em>.
     * Only used when {@code nginxHttps} is {@code true}.
     * Defaults to {@code /etc/nginx/ssl/server.key}.
     */
    @Parameter(property = "jwebmp.angular.nginx.sslCertificateKey", defaultValue = "/etc/nginx/ssl/server.key")
    private String nginxSslCertificateKey;

    /**
     * When {@code nginxHttps} is enabled, controls whether the generated HTTP server
     * block performs a 301 redirect to HTTPS. Defaults to {@code true}.
     * Let's Encrypt always redirects HTTP application traffic to HTTPS while leaving ACME challenge
     * paths available.
     */
    @Parameter(property = "jwebmp.angular.nginx.httpRedirect", defaultValue = "true")
    private boolean nginxHttpRedirect;

    /**
     * The {@code server_name} directive in the generated nginx configuration.
     * Defaults to {@code localhost}. Set to a domain name or {@code _} for a catch-all.
     */
    @Parameter(property = "jwebmp.angular.nginx.serverName", defaultValue = "localhost")
    private String nginxServerName;

    /**
     * Additional raw lines injected into the nginx {@code server} block (HTTPS block when HTTPS
     * is enabled, HTTP block otherwise). Each entry is written as-is, so full nginx directive
     * syntax is expected.
     *
     * <p>Example:</p>
     * <pre>{@code
     * <nginxCustomEntries>
     *   <entry>client_max_body_size 50m;</entry>
     *   <entry>proxy_pass http://backend:8080;</entry>
     * </nginxCustomEntries>
     * }</pre>
     */
    @Parameter(property = "jwebmp.angular.nginx.customEntries")
    private List<String> nginxCustomEntries;

    /**
     * Path to an existing nginx configuration file. When set the plugin uses this file verbatim
     * instead of generating one. The path is resolved relative to the project base directory
     * when not absolute.
     */
    @Parameter(property = "jwebmp.angular.nginx.configFile")
    private String nginxConfigFile;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping JWebMP Angular TypeScript build (jwebmp.angular.skip=true).");
            return;
        }

        configureOutputDirectory();

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader projectClassLoader = null;
        try {
            projectClassLoader = buildProjectClassLoader();
            Thread.currentThread().setContextClassLoader(projectClassLoader);

            Set<INgApp<?>> apps = resolveApps(projectClassLoader);
            if (apps.isEmpty()) {
                getLog().info("No @NgApp implementations found; skipping TypeScript build.");
                return;
            }

            // Run compilation on a Vert.x context to satisfy CallScoper requirements
            Vertx vertx = Vertx.vertx();
            try {
                for (INgApp<?> app : apps) {
                    CountDownLatch latch = new CountDownLatch(1);
                    AtomicReference<Throwable> error = new AtomicReference<>();
                    vertx.runOnContext(v -> {
                        try {
                            TypeScriptCompiler compiler = new TypeScriptCompiler(app);
                            compiler.compileApp();
                            if (ensureToolchain) {
                                compiler.ensureToolchain(downloadNpm, nodeVersion, angularCliVersion, installForce);
                            }
                            if (installDependencies) {
                                compiler.installDependencies(installForce);
                            }
                            if (buildAngular) {
                                compiler.buildAngularApp();
                            }
                            if (buildDockerImage) {
                                buildDockerImage(app);
                            }
                        } catch (Throwable e) {
                            error.set(e);
                        } finally {
                            latch.countDown();
                        }
                    });
                    if (!latch.await(30, TimeUnit.MINUTES)) {
                        throw new MojoExecutionException("Timeout waiting for Angular TypeScript build for app: " + app.getClass().getName());
                    }
                    Throwable t = error.get();
                    if (t != null) {
                        if (t instanceof NoClassDefFoundError e) {
                            throw new MojoExecutionException(
                                    "Failed to load a required class while building Angular TypeScript for app: " + app.getClass().getName()
                                    + ". Missing class: " + e.getMessage()
                                    + ". This usually means a dependency (e.g. log4j-api for @Log4j2) is not on the resolved classpath."
                                    + " Try setting jwebmp.angular.classpathScope=compile or adding the missing dependency to this project.",
                                    e);
                        } else if (t instanceof Exception e) {
                            throw new MojoExecutionException("Failed to build Angular TypeScript for app: " + app.getClass().getName(), e);
                        } else {
                            throw new MojoExecutionException("Failed to build Angular TypeScript for app: " + app.getClass().getName(), new RuntimeException(t));
                        }
                    }
                }
            } finally {
                CountDownLatch closeLatch = new CountDownLatch(1);
                vertx.close().onComplete(ar -> closeLatch.countDown());
                closeLatch.await(10, TimeUnit.SECONDS);
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoFailureException("Unexpected error during JWebMP Angular TypeScript build", e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private ClassLoader buildProjectClassLoader() throws Exception {
        ClasspathScope scope = resolveClasspathScope();
        LinkedHashSet<URL> urls = new LinkedHashSet<>();

        if (scope == ClasspathScope.PACKAGED) {
            File packaged = resolvePackagedArtifact();
            if (packaged == null || !packaged.isFile()) {
                throw new MojoExecutionException("Packaged artifact not found. Run this goal in the package phase or set jwebmp.angular.classpathScope=runtime/compile/test.");
            }
            urls.add(packaged.toURI().toURL());
            addClasspathElements(urls, runtimeClasspathElements);
        } else if (scope == ClasspathScope.TEST) {
            addOutputDirectory(urls, projectOutputDirectory);
            addOutputDirectory(urls, testOutputDirectory);
            addClasspathElements(urls, testClasspathElements);
        } else if (scope == ClasspathScope.COMPILE) {
            addOutputDirectory(urls, projectOutputDirectory);
            addClasspathElements(urls, compileClasspathElements);
        } else {
            addOutputDirectory(urls, projectOutputDirectory);
            addClasspathElements(urls, runtimeClasspathElements);
        }

        // Use the plugin's own classloader as the parent so that framework classes
        // (TypeScriptCompiler, ClassGraph, IGuiceContext, log4j, guava, etc.) are visible to
        // classes loaded from the project's target/classes and dependencies.
        //
        // Parent-first delegation in URLClassLoader means: when the child needs a class that
        // exists on the parent (plugin) classloader, the parent's copy is used. This prevents
        // the same class being loaded by two different classloaders (the root cause of the
        // Log4j ClassCastException in Interpolator).
        ClassLoader pluginClassLoader = getClass().getClassLoader();

        // Remove JARs/directories already reachable from the plugin classloader hierarchy.
        // Because URLClassLoader uses parent-first delegation, duplicate entries are harmless
        // for class loading correctness — but they waste time during classpath scanning
        // (ClassGraph, ServiceLoader) and can cause subtle issues with resources that appear
        // in multiple locations. Removing them keeps the child lean.
        Set<String> pluginClasspathPaths = collectClassloaderPaths(pluginClassLoader);

        int beforeSize = urls.size();
        urls.removeIf(url -> {
            try {
                String normalizedPath = Path.of(url.toURI()).normalize().toString();
                return pluginClasspathPaths.contains(normalizedPath);
            } catch (URISyntaxException e) {
                return false;
            }
        });
        int removed = beforeSize - urls.size();

        if (getLog().isDebugEnabled()) {
            getLog().debug("Project classpath scope: " + scope);
            getLog().debug("Removed " + removed + " duplicate entries already on plugin classloader");
            getLog().debug("Project classpath URLs (" + urls.size() + " after dedup):");
            for (URL url : urls) {
                getLog().debug("  " + url);
            }
        }
        if (removed > 0) {
            getLog().info("Filtered " + removed + " classpath entries already provided by the plugin classloader");
        }

        // We intentionally do NOT use a JPMS ModuleLayer here. In a Maven plugin context the
        // plugin's dependencies (log4j, guava, guicedee, etc.) are loaded via Plexus ClassRealm
        // (a plain URLClassLoader), NOT as JPMS modules. When we de-duplicate the project's
        // classpath against the plugin's, the removed JARs are not in any ModuleLayer — so any
        // project module that `requires` them (e.g. `requires com.google.common`) will fail
        // during Configuration.resolve(). A flat URLClassLoader with parent-first delegation
        // handles this correctly: the parent classloader provides the shared classes without
        // needing JPMS module resolution.
        getLog().info("Using flat classpath URLClassLoader for Angular TypeScript build (" + urls.size() + " project-only entries, " + removed + " shared with plugin)");
        return new URLClassLoader(urls.toArray(new URL[0]), pluginClassLoader);
    }

    /**
     * Walks the classloader hierarchy collecting all JAR/directory paths that are already
     * accessible. This covers URLClassLoader instances (including Maven's ClassRealm) and
     * the boot module layer.
     *
     * @return a set of normalised filesystem paths already on the given classloader chain
     */
    private Set<String> collectClassloaderPaths(ClassLoader loader) {
        Set<String> paths = new LinkedHashSet<>();

        // Walk the URLClassLoader chain (handles Plexus ClassRealm and standard URLClassLoaders)
        for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader ucl) {
                for (URL url : ucl.getURLs()) {
                    try {
                        paths.add(Path.of(url.toURI()).normalize().toString());
                    } catch (URISyntaxException e) {
                        // non-file URL; skip
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

    private void configureOutputDirectory() {
        String resolved = resolveOutputDirectory(outputDirectory);
        if (resolved != null) {
            setOutputDirectoryProperty(resolved, "configured");
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

        String defaultDirectory = project != null && project.getBuild() != null ? project.getBuild().getDirectory() : null;
        String resolvedDefault = resolveOutputDirectory(defaultDirectory);
        if (resolvedDefault != null) {
            setOutputDirectoryProperty(resolvedDefault, "defaulted");
        }
    }

    private String resolveOutputDirectory(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        File resolved = new File(configured);
        if (!resolved.isAbsolute() && project != null && project.getBasedir() != null) {
            resolved = new File(project.getBasedir(), configured);
        }
        return resolved.getAbsolutePath();
    }

    private ClasspathScope resolveClasspathScope() {
        if (classpathScope == null || classpathScope.isBlank()) {
            return ClasspathScope.RUNTIME;
        }
        String normalized = classpathScope.trim()
                                          .toLowerCase();
        if ("compile".equals(normalized)) {
            return ClasspathScope.COMPILE;
        }
        if ("test".equals(normalized)) {
            return ClasspathScope.TEST;
        }
        if ("packaged".equals(normalized) || "package".equals(normalized) || "artifact".equals(normalized)) {
            return ClasspathScope.PACKAGED;
        }
        if ("runtime".equals(normalized)) {
            return ClasspathScope.RUNTIME;
        }
        getLog().warn("Unknown classpathScope '" + classpathScope + "', defaulting to runtime.");
        return ClasspathScope.RUNTIME;
    }

    private void addClasspathElements(LinkedHashSet<URL> urls, List<String> elements) throws Exception {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        for (String element : elements) {
            if (element == null || element.isBlank()) {
                continue;
            }
            urls.add(new File(element).toURI().toURL());
        }
    }

    private void addOutputDirectory(LinkedHashSet<URL> urls, String directory) throws Exception {
        if (directory == null || directory.isBlank()) {
            return;
        }
        File outputDir = new File(directory);
        if (outputDir.exists()) {
            urls.add(outputDir.toURI().toURL());
        }
    }

    private File resolvePackagedArtifact() {
        if (project != null && project.getArtifact() != null && project.getArtifact().getFile() != null) {
            File artifactFile = project.getArtifact().getFile();
            if (artifactFile.isFile()) {
                return artifactFile;
            }
        }
        if (project != null && project.getBuild() != null) {
            String finalName = project.getBuild().getFinalName();
            String packaging = project.getPackaging();
            if (packaging == null || packaging.isBlank()) {
                packaging = "jar";
            }
            if (finalName != null && !finalName.isBlank()) {
                File candidate = new File(project.getBuild().getDirectory(), finalName + "." + packaging);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private enum ClasspathScope {
        RUNTIME,
        COMPILE,
        TEST,
        PACKAGED
    }

    private void setOutputDirectoryProperty(String resolved, String source) {
        System.setProperty("jwebmp.outputDirectory", resolved);
        String existingBase = System.getProperty("jwebmp");
        if (existingBase == null || existingBase.isBlank()) {
            System.setProperty("jwebmp", resolved);
        }
        getLog().info("JWebMP output directory " + source + " to " + resolved);
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

    // ── Docker image generation ──────────────────────────────────────────────

    /**
     * Builds a Docker image for the given Angular application. The image contains the production
     * build output served by nginx.
     *
     * <p>If no custom {@code dockerfilePath} is configured, the plugin generates a Dockerfile and a
     * minimal {@code nginx.conf} inside the app's output directory. If a Dockerfile already exists
     * at that location it is left untouched.</p>
     */
    private void buildDockerImage(INgApp<?> app) throws MojoExecutionException {
        File appDir = com.jwebmp.core.base.angular.client.AppUtils
                .getAppPath((Class<? extends INgApp<?>>) app.getClass());

        // Verify build output exists
        File distDir = resolveAngularBrowserDistDir(appDir);
        if (!distDir.isDirectory()) {
            throw new MojoExecutionException(
                    "Angular build output not found under " + new File(appDir, "dist").getAbsolutePath() +
                    ". Ensure buildAngular is enabled and the Angular build completed successfully.");
        }
        getLog().info("Using Angular browser output: " + distDir.getAbsolutePath());

        // Resolve the Dockerfile to use
        File dockerfile = resolveDockerfile(appDir, distDir);
        if (dockerfile == null) {
            throw new MojoExecutionException("Could not resolve or generate a Dockerfile for app: " + app.name());
        }

        String imageName = resolveDockerImageName(app);
        getLog().info("Building Docker image '" + imageName + "' from " + dockerfile.getAbsolutePath());

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("build");
        command.add("-t");
        command.add(imageName);
        command.add("-f");
        command.add(dockerfile.getAbsolutePath());

        if (dockerBuildArgs != null) {
            for (String arg : dockerBuildArgs) {
                if (arg != null && !arg.isBlank()) {
                    command.add(arg);
                }
            }
        }

        // Context is the app directory so COPY instructions resolve correctly
        command.add(appDir.getAbsolutePath());

        int exitCode = runProcess(command, appDir);
        if (exitCode != 0) {
            throw new MojoExecutionException("Docker build failed with exit code " + exitCode + " for image: " + imageName);
        }
        getLog().info("Docker image built successfully: " + imageName);
    }

    /**
     * Resolves the Dockerfile to use. If {@code dockerfilePath} is configured and points to an
     * existing file it is returned directly. Otherwise a Dockerfile (and supporting nginx.conf)
     * is generated inside the app output directory.
     */
    private File resolveDockerfile(File appDir, File distDir) {
        // User-supplied Dockerfile
        if (dockerfilePath != null && !dockerfilePath.isBlank()) {
            File custom = new File(dockerfilePath);
            if (!custom.isAbsolute() && project != null && project.getBasedir() != null) {
                custom = new File(project.getBasedir(), dockerfilePath);
            }
            if (custom.isFile()) {
                getLog().info("Using custom Dockerfile: " + custom.getAbsolutePath());
                return custom;
            }
            getLog().warn("Configured dockerfilePath does not exist: " + custom.getAbsolutePath() + "; generating default Dockerfile.");
        }

        // Generate default Dockerfile
        File dockerfile = new File(appDir, "Dockerfile");
        File nginxConf = new File(appDir, "nginx.conf");
        File dockerEntrypoint = new File(appDir, "docker-entrypoint.sh");

        try {
            if (!nginxConf.exists() || isGeneratedByPlugin(nginxConf)) {
                String nginxContent = resolveNginxConf();
                Files.writeString(nginxConf.toPath(), nginxContent, StandardCharsets.UTF_8);
                getLog().info("Generated nginx.conf at " + nginxConf.getAbsolutePath());
            }
            if (nginxLetsEncrypt && (!dockerEntrypoint.exists() || isGeneratedEntrypoint(dockerEntrypoint))) {
                Files.writeString(dockerEntrypoint.toPath(), generateLetsEncryptEntrypoint(), StandardCharsets.UTF_8);
                getLog().info("Generated docker-entrypoint.sh at " + dockerEntrypoint.getAbsolutePath());
            }
            if (!dockerfile.exists() || isGeneratedByPlugin(dockerfile)) {
                Files.writeString(dockerfile.toPath(), generateDockerfile(appDir, distDir), StandardCharsets.UTF_8);
                getLog().info("Generated Dockerfile at " + dockerfile.getAbsolutePath());
            }
        } catch (IOException e) {
            getLog().error("Failed to generate Docker files: " + e.getMessage(), e);
            return null;
        }
        return dockerfile;
    }

    /**
     * Resolves the Docker image name. Uses the configured {@code dockerImageName} when set,
     * otherwise derives one from the Maven project coordinates.
     */
    private String resolveDockerImageName(INgApp<?> app) {
        if (dockerImageName != null && !dockerImageName.isBlank()) {
            return dockerImageName;
        }
        String artifactId = project != null ? project.getArtifactId() : app.name();
        String version = project != null ? project.getVersion() : "latest";
        return artifactId + ":" + version;
    }

    private File resolveAngularBrowserDistDir(File appDir) {
        File distRoot = new File(appDir, "dist");
        List<File> candidates = new ArrayList<>();

        String outputPath = readAngularOutputPath(appDir);
        if (outputPath != null && !outputPath.isBlank()) {
            File configured = new File(appDir, outputPath);
            candidates.add(new File(configured, "browser"));
            candidates.add(configured);
        }

        candidates.add(new File(appDir, "dist/jwebmp/browser"));
        candidates.add(new File(appDir, "dist/JWebMP/browser"));
        candidates.add(new File(appDir, "dist/" + appDir.getName() + "/browser"));
        candidates.add(new File(appDir, "dist/" + appDir.getName()));

        for (File candidate : candidates) {
            if (hasIndexHtml(candidate)) {
                return candidate;
            }
        }

        if (distRoot.isDirectory()) {
            try (var paths = Files.walk(distRoot.toPath(), 4)) {
                return paths
                        .filter(path -> path.getFileName() != null && "index.html".equals(path.getFileName().toString()))
                        .map(path -> path.getParent().toFile())
                        .findFirst()
                        .orElse(candidates.get(0));
            } catch (IOException e) {
                getLog().warn("Unable to scan Angular dist output under " + distRoot.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        return candidates.get(0);
    }

    private String readAngularOutputPath(File appDir) {
        File angularJson = new File(appDir, "angular.json");
        if (!angularJson.isFile()) {
            return null;
        }
        try {
            String json = Files.readString(angularJson.toPath(), StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"outputPath\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException e) {
            getLog().warn("Unable to read angular.json for Docker output detection: " + e.getMessage());
        }
        return null;
    }

    private boolean hasIndexHtml(File directory) {
        return directory != null && new File(directory, "index.html").isFile();
    }

    private String toDockerCopyPath(File appDir, File distDir) {
        try {
            Path relative = appDir.toPath().toAbsolutePath().normalize()
                    .relativize(distDir.toPath().toAbsolutePath().normalize());
            return relative.toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException e) {
            return "dist";
        }
    }

    private boolean isGeneratedByPlugin(File file) {
        if (!file.isFile()) {
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            return firstLine != null && firstLine.contains("Generated by jwebmp-angular:build");
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isGeneratedEntrypoint(File file) {
        if (!file.isFile()) {
            return false;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return content.contains("LETSENCRYPT_DOMAINS") && content.contains("certbot certonly");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Generates a Dockerfile that copies the Angular production build output into an nginx
     * container. When {@code nginxHttps} is enabled, port 443 is also exposed.
     */
    private String generateDockerfile(File appDir, File distDir) {
        String baseImage = (dockerBaseImage != null && !dockerBaseImage.isBlank()) ? dockerBaseImage : "nginx:alpine";
        String distPath = toDockerCopyPath(appDir, distDir);
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by jwebmp-angular:build — do not edit manually\n");
        sb.append("FROM ").append(baseImage).append("\n\n");
        if (nginxLetsEncrypt) {
            sb.append("RUN apk add --no-cache certbot openssl\n\n");
            sb.append("RUN mkdir -p /var/www/certbot\n\n");
        }
        sb.append("# Remove default nginx website\n");
        sb.append("RUN rm -rf /usr/share/nginx/html/*\n\n");
        sb.append("# Copy custom nginx configuration\n");
        sb.append("COPY nginx.conf /etc/nginx/conf.d/default.conf\n\n");
        sb.append("# Copy Angular production build output\n");
        sb.append("COPY ").append(distPath).append("/ /usr/share/nginx/html/\n\n");
        if (nginxLetsEncrypt) {
            sb.append("COPY docker-entrypoint.sh /docker-entrypoint.sh\n");
            sb.append("RUN chmod +x /docker-entrypoint.sh\n\n");
            sb.append("ENV LETSENCRYPT_DOMAINS=\"").append(String.join(" ", resolveLetsEncryptDomains())).append("\"\n");
            sb.append("ENV LETSENCRYPT_EMAIL=\"").append(nginxLetsEncryptEmail == null ? "" : nginxLetsEncryptEmail).append("\"\n");
            sb.append("ENV LETSENCRYPT_STAGING=\"").append(nginxLetsEncryptStaging).append("\"\n\n");
        }
        if (nginxHttps || nginxLetsEncrypt) {
            sb.append("EXPOSE ").append(nginxHttpPort).append(" 443\n\n");
        } else {
            sb.append("EXPOSE ").append(nginxHttpPort).append("\n\n");
        }
        if (nginxLetsEncrypt) {
            sb.append("CMD [\"/docker-entrypoint.sh\"]\n");
        } else {
            sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
        }
        return sb.toString();
    }

    /**
     * Resolves the nginx configuration content. If {@code nginxConfigFile} points to an existing
     * file its content is returned verbatim. Otherwise a configuration is generated honouring the
     * {@code nginxHttps}, {@code nginxServerName}, {@code nginxCustomEntries}, and related settings.
     */
    private String resolveNginxConf() throws IOException {
        if (nginxConfigFile != null && !nginxConfigFile.isBlank()) {
            File custom = new File(nginxConfigFile);
            if (!custom.isAbsolute() && project != null && project.getBasedir() != null) {
                custom = new File(project.getBasedir(), nginxConfigFile);
            }
            if (custom.isFile()) {
                getLog().info("Using custom nginx config: " + custom.getAbsolutePath());
                return Files.readString(custom.toPath(), StandardCharsets.UTF_8);
            }
            getLog().warn("Configured nginxConfigFile does not exist: " + custom.getAbsolutePath() + "; generating default nginx.conf.");
        }
        return generateNginxConf();
    }

    /**
     * Generates an nginx configuration suitable for serving an Angular SPA with client-side
     * routing (falls back to index.html for unknown routes).
     *
     * <p>When {@code nginxHttps} is {@code true} the primary server block listens on 443 with
     * TLS and a secondary block on the configured HTTP port performs a 301 redirect (unless
     * {@code nginxHttpRedirect} is disabled). Custom entries from {@code nginxCustomEntries} are
     * injected into the primary server block.</p>
     */
    private String generateNginxConf() {
        String serverName = (nginxServerName != null && !nginxServerName.isBlank()) ? nginxServerName : "localhost";
        String sslCertificate = resolveNginxSslCertificate();
        String sslCertificateKey = resolveNginxSslCertificateKey();
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by jwebmp-angular:build — do not edit manually\n");

        if (nginxHttps || nginxLetsEncrypt) {
            // ── HTTPS server block (primary) ─────────────────────────────────────
            sb.append("server {\n");
            sb.append("    listen 443 ssl;\n");
            sb.append("    server_name ").append(serverName).append(";\n\n");
            sb.append("    ssl_certificate     ").append(sslCertificate).append(";\n");
            sb.append("    ssl_certificate_key ").append(sslCertificateKey).append(";\n");
            sb.append("    ssl_protocols       TLSv1.2 TLSv1.3;\n");
            sb.append("    ssl_ciphers         HIGH:!aNULL:!MD5;\n\n");
            appendCommonServerBody(sb);
            appendCustomEntries(sb);
            sb.append("}\n\n");

            // ── HTTP server block (redirect or plain) ────────────────────────────
            sb.append("server {\n");
            sb.append("    listen ").append(nginxHttpPort).append(";\n");
            sb.append("    server_name ").append(serverName).append(";\n\n");
            if (nginxLetsEncrypt) {
                appendAcmeChallengeLocation(sb);
            }
            if (nginxLetsEncrypt || nginxHttpRedirect) {
                sb.append("    # Redirect all HTTP traffic to HTTPS\n");
                sb.append("    location / {\n");
                sb.append("        return 301 https://$host$request_uri;\n");
                sb.append("    }\n");
            } else {
                appendCommonServerBody(sb);
            }
            sb.append("}\n");
        } else {
            // ── HTTP-only server block ───────────────────────────────────────────
            sb.append("server {\n");
            sb.append("    listen ").append(nginxHttpPort).append(";\n");
            sb.append("    server_name ").append(serverName).append(";\n\n");
            appendCommonServerBody(sb);
            appendCustomEntries(sb);
            sb.append("}\n");
        }

        return sb.toString();
    }

    private String generateLetsEncryptEntrypoint() {
        String domains = String.join(" ", resolveLetsEncryptDomains());
        String staging = nginxLetsEncryptStaging ? "true" : "false";
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("set -eu\n\n");
        sb.append("DOMAINS=\"${LETSENCRYPT_DOMAINS:-").append(domains).append("}\"\n");
        sb.append("EMAIL=\"${LETSENCRYPT_EMAIL:-}\"\n");
        sb.append("STAGING=\"${LETSENCRYPT_STAGING:-").append(staging).append("}\"\n");
        sb.append("PRIMARY_DOMAIN=\"$(printf '%s' \"$DOMAINS\" | awk '{print $1}')\"\n");
        sb.append("CERT_PATH=\"/etc/letsencrypt/live/$PRIMARY_DOMAIN/fullchain.pem\"\n");
        sb.append("KEY_PATH=\"/etc/letsencrypt/live/$PRIMARY_DOMAIN/privkey.pem\"\n");
        sb.append("FALLBACK_MARKER=\"/etc/letsencrypt/live/$PRIMARY_DOMAIN/.self-signed-fallback\"\n\n");
        sb.append("certbot_domain_args() {\n");
        sb.append("  for domain in $DOMAINS; do\n");
        sb.append("    printf -- '-d %s ' \"$domain\"\n");
        sb.append("  done\n");
        sb.append("}\n\n");
        sb.append("certbot_email_args() {\n");
        sb.append("  if [ -n \"$EMAIL\" ]; then\n");
        sb.append("    printf -- '--email %s ' \"$EMAIL\"\n");
        sb.append("  else\n");
        sb.append("    printf -- '--register-unsafely-without-email '\n");
        sb.append("  fi\n");
        sb.append("}\n\n");
        sb.append("certbot_staging_args() {\n");
        sb.append("  if [ \"$STAGING\" = \"true\" ]; then\n");
        sb.append("    printf -- '--staging '\n");
        sb.append("  fi\n");
        sb.append("}\n\n");
        sb.append("create_self_signed_fallback() {\n");
        sb.append("  mkdir -p \"$(dirname \"$CERT_PATH\")\"\n");
        sb.append("  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \\\n");
        sb.append("    -keyout \"$KEY_PATH\" \\\n");
        sb.append("    -out \"$CERT_PATH\" \\\n");
        sb.append("    -subj \"/CN=$PRIMARY_DOMAIN\"\n");
        sb.append("  touch \"$FALLBACK_MARKER\"\n");
        sb.append("}\n\n");
        sb.append("if [ -f \"$FALLBACK_MARKER\" ]; then\n");
        sb.append("  rm -rf \"/etc/letsencrypt/live/$PRIMARY_DOMAIN\"\n");
        sb.append("fi\n\n");
        sb.append("if [ ! -f \"$CERT_PATH\" ] || [ ! -f \"$KEY_PATH\" ]; then\n");
        sb.append("  if ! sh -c \"certbot certonly --standalone --non-interactive --agree-tos $(certbot_email_args) $(certbot_staging_args) $(certbot_domain_args)\"; then\n");
        sb.append("    echo \"Let's Encrypt certificate request failed; using a temporary self-signed certificate.\" >&2\n");
        sb.append("    create_self_signed_fallback\n");
        sb.append("  fi\n");
        sb.append("fi\n\n");
        sb.append("nginx\n\n");
        sb.append("while true; do\n");
        sb.append("  sleep 12h\n");
        sb.append("  certbot renew --webroot -w /var/www/certbot --quiet && nginx -s reload || true\n");
        sb.append("done\n");
        return sb.toString();
    }

    private String resolveNginxSslCertificate() {
        if (nginxLetsEncrypt) {
            return letsEncryptCertificatePath(resolvePrimaryLetsEncryptDomain());
        }
        return nginxSslCertificate;
    }

    private String resolveNginxSslCertificateKey() {
        if (nginxLetsEncrypt) {
            return letsEncryptCertificateKeyPath(resolvePrimaryLetsEncryptDomain());
        }
        return nginxSslCertificateKey;
    }

    private String letsEncryptCertificatePath(String domain) {
        return "/etc/letsencrypt/live/" + domain + "/fullchain.pem";
    }

    private String letsEncryptCertificateKeyPath(String domain) {
        return "/etc/letsencrypt/live/" + domain + "/privkey.pem";
    }

    private String resolvePrimaryLetsEncryptDomain() {
        return resolveLetsEncryptDomains().getFirst();
    }

    private List<String> resolveLetsEncryptDomains() {
        List<String> domains = new ArrayList<>();
        if (nginxLetsEncryptDomains != null) {
            for (String domain : nginxLetsEncryptDomains) {
                addLetsEncryptDomains(domains, domain);
            }
        }
        if (domains.isEmpty()) {
            addLetsEncryptDomains(domains, nginxServerName);
        }
        if (domains.isEmpty()) {
            throw new IllegalStateException("nginxLetsEncrypt requires at least one real domain via nginxLetsEncryptDomains or nginxServerName.");
        }
        return domains;
    }

    private void addLetsEncryptDomains(List<String> domains, String configured) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        for (String domain : configured.split("[,\\s]+")) {
            String trimmed = domain.trim();
            if (trimmed.isEmpty() || "localhost".equalsIgnoreCase(trimmed) || "_".equals(trimmed)) {
                continue;
            }
            domains.add(trimmed);
        }
    }

    /**
     * Appends the common server body directives (root, index, location blocks, caching, and
     * security headers) shared between HTTP and HTTPS server blocks.
     */
    private void appendCommonServerBody(StringBuilder sb) {
        sb.append("    root /usr/share/nginx/html;\n");
        sb.append("    index index.html;\n\n");
        if (nginxLetsEncrypt) {
            appendAcmeChallengeLocation(sb);
        }
        sb.append("    # Serve static files directly; fall back to index.html for Angular routes\n");
        sb.append("    location / {\n");
        sb.append("        try_files $uri $uri/ /index.html;\n");
        sb.append("        add_header Cache-Control \"no-store, no-cache, must-revalidate, max-age=0\" always;\n");
        sb.append("        add_header Pragma \"no-cache\" always;\n");
        sb.append("        add_header Expires \"0\" always;\n");
        sb.append("    }\n\n");
        sb.append("    # Never cache the Angular app shell\n");
        sb.append("    location = /index.html {\n");
        sb.append("        add_header Cache-Control \"no-store, no-cache, must-revalidate, max-age=0\" always;\n");
        sb.append("        add_header Pragma \"no-cache\" always;\n");
        sb.append("        add_header Expires \"0\" always;\n");
        sb.append("    }\n\n");
        sb.append("    # Cache static assets aggressively\n");
        sb.append("    location ~* \\.(js|mjs|css|map|json|txt|xml|png|jpg|jpeg|gif|webp|avif|ico|svg|woff|woff2|ttf|eot|otf)$ {\n");
        sb.append("        expires 1y;\n");
        sb.append("        add_header Cache-Control \"public, max-age=31536000, immutable\" always;\n");
        sb.append("    }\n\n");
        sb.append("    # Security headers\n");
        sb.append("    add_header X-Frame-Options \"SAMEORIGIN\" always;\n");
        sb.append("    add_header X-Content-Type-Options \"nosniff\" always;\n");
        sb.append("    add_header Referrer-Policy \"strict-origin-when-cross-origin\" always;\n\n");
    }

    private void appendAcmeChallengeLocation(StringBuilder sb) {
        sb.append("    # ACME HTTP-01 challenge files for Let's Encrypt\n");
        sb.append("    location /.well-known/acme-challenge/ {\n");
        sb.append("        root /var/www/certbot;\n");
        sb.append("    }\n\n");
    }

    /**
     * Appends user-supplied custom nginx directives into the current server block.
     */
    private void appendCustomEntries(StringBuilder sb) {
        if (nginxCustomEntries == null || nginxCustomEntries.isEmpty()) {
            return;
        }
        sb.append("    # Custom entries\n");
        for (String entry : nginxCustomEntries) {
            if (entry != null && !entry.isBlank()) {
                sb.append("    ").append(entry.trim()).append("\n");
            }
        }
        sb.append("\n");
    }

    /**
     * Runs an external process and forwards stdout/stderr through the Maven logger.
     *
     * @return the process exit code, or -1 on timeout / error
     */
    private int runProcess(List<String> command, File workingDirectory) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workingDirectory);
            pb.environment().putAll(System.getenv());
            getLog().info("Running command in " + workingDirectory.getAbsolutePath() + ": " + command);
            Process process = pb.start();
            Thread stdout = streamProcessOutput(process.getInputStream(), false);
            Thread stderr = streamProcessOutput(process.getErrorStream(), true);
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                getLog().error("Docker build timed out after 10 minutes.");
                return -1;
            }
            stdout.join(TimeUnit.SECONDS.toMillis(5));
            stderr.join(TimeUnit.SECONDS.toMillis(5));
            return process.exitValue();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            getLog().error("Error running docker build: " + e.getMessage(), e);
            return -1;
        }
    }

    private Thread streamProcessOutput(InputStream stream, boolean error) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (error) {
                        getLog().error(line);
                    } else {
                        getLog().info(line);
                    }
                }
            } catch (IOException e) {
                getLog().debug("Unable to read process output: " + e.getMessage());
            }
        }, error ? "jwebmp-angular-process-stderr" : "jwebmp-angular-process-stdout");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}

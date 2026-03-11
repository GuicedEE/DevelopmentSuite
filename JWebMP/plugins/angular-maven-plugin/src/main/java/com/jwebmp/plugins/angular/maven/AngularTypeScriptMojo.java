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

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
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
import java.util.concurrent.TimeUnit;

@Mojo(
        name = "build",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
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
     * When {@code nginxHttps} is enabled, controls whether the generated HTTP (port 80) server
     * block performs a 301 redirect to HTTPS. Defaults to {@code true}.
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

    /**
     * When true (default), the plugin will attempt to build a JPMS ModuleLayer using the resolved
     * classpath elements when it detects one or more modules. This enables ServiceLoader/provider
     * discovery for providers declared via module-info instead of META-INF/services and generally
     * matches application runtime when using the module-path.
     */
    @Parameter(property = "jwebmp.angular.jpms.enabled", defaultValue = "true")
    private boolean jpmsEnabled;

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

            for (INgApp<?> app : apps) {
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
                } catch (Exception e) {
                    throw new MojoExecutionException("Failed to build Angular TypeScript for app: " + app.getClass().getName(), e);
                }
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

        // Prefer JPMS module-path when enabled and modules are present. Fall back to flat URLClassLoader.
        if (jpmsEnabled) {
            try {
                ClassLoader loader = tryBuildModuleLayer(urls);
                if (loader != null) {
                    getLog().info("Using JPMS module-path loader for Angular TypeScript build (jwebmp.angular.jpms.enabled=true)");
                    return loader;
                }
            } catch (Exception ex) {
                getLog().warn("Failed to initialize JPMS ModuleLayer; falling back to URLClassLoader: " + ex.getMessage());
                if (getLog().isDebugEnabled()) {
                    getLog().debug("JPMS initialization failure", ex);
                }
            }
        }

        getLog().info("Using flat classpath URLClassLoader for Angular TypeScript build");
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    /**
     * Attempts to create a single-loader ModuleLayer for the provided classpath URLs. Returns null
     * when no modules are detected among the inputs.
     */
    private ClassLoader tryBuildModuleLayer(Set<URL> urls) throws Exception {
        if (urls == null || urls.isEmpty()) {
            return null;
        }

        // Convert to Paths for ModuleFinder
        Path[] paths = urls.stream()
                .map(u -> {
                    try {
                        return Path.of(u.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(Path[]::new);

        ModuleFinder finder = ModuleFinder.of(paths);
        Set<ModuleReference> allRefs = finder.findAll();
        if (allRefs.isEmpty()) {
            // No named/automatic modules detected; skip JPMS path
            return null;
        }

        Set<String> roots = new LinkedHashSet<>();
        for (ModuleReference mr : allRefs) {
            roots.add(mr.descriptor().name());
        }

        if (roots.isEmpty()) {
            return null;
        }

        // Resolve against the boot layer
        ModuleLayer parent = ModuleLayer.boot();
        Configuration cf = parent.configuration().resolve(finder, ModuleFinder.of(), roots);

        // Use one loader for all modules so TCCL-based discovery works as expected
        ModuleLayer layer = parent.defineModulesWithOneLoader(cf, ClassLoader.getPlatformClassLoader());

        // Any module's loader is fine; defineModulesWithOneLoader uses a single loader for all
        String anyRoot = roots.iterator().next();
        ClassLoader loader = layer.findLoader(anyRoot);

        if (loader == null) {
            return null;
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("JPMS modules resolved: " + String.join(", ", roots));
        }
        return loader;
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
     * build output (from {@code dist/jwebmp/browser/}) served by nginx.
     *
     * <p>If no custom {@code dockerfilePath} is configured, the plugin generates a Dockerfile and a
     * minimal {@code nginx.conf} inside the app's output directory. If a Dockerfile already exists
     * at that location it is left untouched.</p>
     */
    private void buildDockerImage(INgApp<?> app) throws MojoExecutionException {
        File appDir = com.jwebmp.core.base.angular.client.AppUtils
                .getAppPath((Class<? extends INgApp<?>>) app.getClass());

        // Resolve the Dockerfile to use
        File dockerfile = resolveDockerfile(appDir);
        if (dockerfile == null) {
            throw new MojoExecutionException("Could not resolve or generate a Dockerfile for app: " + app.name());
        }

        // Verify build output exists
        File distDir = new File(appDir, "dist/jwebmp/browser");
        if (!distDir.isDirectory()) {
            throw new MojoExecutionException(
                    "Angular build output not found at " + distDir.getAbsolutePath() +
                    ". Ensure buildAngular is enabled and the Angular build completed successfully.");
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
    private File resolveDockerfile(File appDir) {
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

        try {
            if (!nginxConf.exists()) {
                String nginxContent = resolveNginxConf();
                Files.writeString(nginxConf.toPath(), nginxContent, StandardCharsets.UTF_8);
                getLog().info("Generated nginx.conf at " + nginxConf.getAbsolutePath());
            }
            if (!dockerfile.exists()) {
                Files.writeString(dockerfile.toPath(), generateDockerfile(), StandardCharsets.UTF_8);
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

    /**
     * Generates a Dockerfile that copies the Angular production build output into an nginx
     * container. When {@code nginxHttps} is enabled, port 443 is also exposed.
     */
    private String generateDockerfile() {
        String baseImage = (dockerBaseImage != null && !dockerBaseImage.isBlank()) ? dockerBaseImage : "nginx:alpine";
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by jwebmp-angular:build — do not edit manually\n");
        sb.append("FROM ").append(baseImage).append("\n\n");
        sb.append("# Remove default nginx website\n");
        sb.append("RUN rm -rf /usr/share/nginx/html/*\n\n");
        sb.append("# Copy custom nginx configuration\n");
        sb.append("COPY nginx.conf /etc/nginx/conf.d/default.conf\n\n");
        sb.append("# Copy Angular production build output\n");
        sb.append("COPY dist/jwebmp/browser/ /usr/share/nginx/html/\n\n");
        if (nginxHttps) {
            sb.append("EXPOSE 80 443\n\n");
        } else {
            sb.append("EXPOSE 80\n\n");
        }
        sb.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
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
     * TLS and a secondary block on port 80 performs a 301 redirect (unless
     * {@code nginxHttpRedirect} is disabled). Custom entries from {@code nginxCustomEntries} are
     * injected into the primary server block.</p>
     */
    private String generateNginxConf() {
        String serverName = (nginxServerName != null && !nginxServerName.isBlank()) ? nginxServerName : "localhost";
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by jwebmp-angular:build — do not edit manually\n");

        if (nginxHttps) {
            // ── HTTPS server block (primary) ─────────────────────────────────────
            sb.append("server {\n");
            sb.append("    listen 443 ssl;\n");
            sb.append("    server_name ").append(serverName).append(";\n\n");
            sb.append("    ssl_certificate     ").append(nginxSslCertificate).append(";\n");
            sb.append("    ssl_certificate_key ").append(nginxSslCertificateKey).append(";\n");
            sb.append("    ssl_protocols       TLSv1.2 TLSv1.3;\n");
            sb.append("    ssl_ciphers         HIGH:!aNULL:!MD5;\n\n");
            appendCommonServerBody(sb);
            appendCustomEntries(sb);
            sb.append("}\n\n");

            // ── HTTP server block (redirect or plain) ────────────────────────────
            sb.append("server {\n");
            sb.append("    listen 80;\n");
            sb.append("    server_name ").append(serverName).append(";\n\n");
            if (nginxHttpRedirect) {
                sb.append("    # Redirect all HTTP traffic to HTTPS\n");
                sb.append("    return 301 https://$host$request_uri;\n");
            } else {
                appendCommonServerBody(sb);
            }
            sb.append("}\n");
        } else {
            // ── HTTP-only server block ───────────────────────────────────────────
            sb.append("server {\n");
            sb.append("    listen 80;\n");
            sb.append("    server_name ").append(serverName).append(";\n\n");
            appendCommonServerBody(sb);
            appendCustomEntries(sb);
            sb.append("}\n");
        }

        return sb.toString();
    }

    /**
     * Appends the common server body directives (root, index, location blocks, caching, and
     * security headers) shared between HTTP and HTTPS server blocks.
     */
    private void appendCommonServerBody(StringBuilder sb) {
        sb.append("    root /usr/share/nginx/html;\n");
        sb.append("    index index.html;\n\n");
        sb.append("    # Serve static files directly; fall back to index.html for Angular routes\n");
        sb.append("    location / {\n");
        sb.append("        try_files $uri $uri/ /index.html;\n");
        sb.append("    }\n\n");
        sb.append("    # Cache static assets aggressively\n");
        sb.append("    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {\n");
        sb.append("        expires 1y;\n");
        sb.append("        add_header Cache-Control \"public, immutable\";\n");
        sb.append("    }\n\n");
        sb.append("    # Security headers\n");
        sb.append("    add_header X-Frame-Options \"SAMEORIGIN\" always;\n");
        sb.append("    add_header X-Content-Type-Options \"nosniff\" always;\n");
        sb.append("    add_header Referrer-Policy \"strict-origin-when-cross-origin\" always;\n\n");
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
     * Runs an external process with inherited I/O, waiting up to 10 minutes.
     *
     * @return the process exit code, or -1 on timeout / error
     */
    private int runProcess(List<String> command, File workingDirectory) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(workingDirectory)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
            pb.environment().putAll(System.getenv());
            Process process = pb.start();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                getLog().error("Docker build timed out after 10 minutes.");
                return -1;
            }
            return process.exitValue();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            getLog().error("Error running docker build: " + e.getMessage(), e);
            return -1;
        }
    }
}

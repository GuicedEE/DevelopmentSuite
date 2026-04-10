# JWebMP Angular Maven Plugin

A Maven plugin that drives the JWebMP Angular TypeScript compiler and build pipeline directly from your Maven build.  
It discovers `@NgApp` implementations on the project classpath, generates TypeScript sources, and can optionally install npm dependencies, provision the Node/Angular CLI toolchain, and execute the full Angular production build — all without leaving Maven.

## Coordinates

```xml
<groupId>com.jwebmp.plugins</groupId>
<artifactId>angular-maven-plugin</artifactId>
<version>2.0.0-RC1</version>
<packaging>maven-plugin</packaging>
```

## Goals

| Goal | Default Phase | Description |
|---|---|---|
| `jwebmp-angular:build` | `process-classes` | Compiles TypeScript sources for every discovered `@NgApp`, optionally installs dependencies and runs the Angular build. |
| `jwebmp-angular:help` | — | Displays help information about the plugin and its parameters. |

## Requirements

* **Java 25+** (source and release level configured in the plugin POM)
* **Maven 3.9.6+** (aligned with the `maven-plugin-api` version used)
* The project must have the `com.jwebmp.plugins:angular` library on its classpath so that `@NgApp` implementations and the `TypeScriptCompiler` are available.

## Quick Start

Add the plugin to your project's `<build>` section:

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals>
        <goal>build</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

This is the minimal configuration. With defaults the plugin will:

1. Run during the `process-classes` phase.
2. Build a project classloader from the **runtime** classpath scope.
3. Attempt JPMS `ModuleLayer` resolution (falls back to a flat `URLClassLoader`).
4. Discover all `@NgApp` implementations via classpath scanning.
5. Invoke `TypeScriptCompiler.compileApp()` for each app (generates TypeScript sources, config files, `package.json`, `angular.json`, etc.).

Dependency installation, toolchain provisioning, and the Angular production build are **disabled by default** and must be enabled explicitly.

## Full Configuration Reference

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals>
        <goal>build</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <!-- Skip the plugin entirely (default: false) -->
    <skip>false</skip>

    <!-- Base output directory for generated TypeScript sources.
         Defaults to ${project.build.directory} when not set.
         Sets the system property jwebmp.outputDirectory at runtime. -->
    <outputDirectory>${project.build.directory}</outputDirectory>

    <!-- Which classpath elements to use when building the project classloader.
         Values: runtime (default) | compile | test | packaged | package | artifact -->
    <classpathScope>runtime</classpathScope>

    <!-- Run npm install after TypeScript compilation (default: false) -->
    <installDependencies>false</installDependencies>

    <!-- Pass --force to npm install (default: false, only applies when installDependencies is true) -->
    <installForce>false</installForce>

    <!-- Ensure Node/npm and @angular/cli are available before building (default: false) -->
    <ensureToolchain>false</ensureToolchain>

    <!-- Download Node/npm automatically when missing (default: false, only applies when ensureToolchain is true) -->
    <downloadNpm>false</downloadNpm>

    <!-- Node.js version to download when downloadNpm is enabled (default: 20.18.1) -->
    <nodeVersion>20.18.1</nodeVersion>

    <!-- Override the @angular/cli major version.
         When omitted, the version from package.json or a built-in default is used. -->
    <!-- <angularCliVersion>20</angularCliVersion> -->

    <!-- Run the full Angular production build via ng build (default: false) -->
    <buildAngular>false</buildAngular>

    <!-- Build a Docker image containing the Angular production build served by nginx (default: false).
         Requires buildAngular=true or a pre-existing dist/jwebmp/browser/ directory. -->
    <buildDockerImage>false</buildDockerImage>

    <!-- Docker image name (repository[:tag]).
         Defaults to ${project.artifactId}:${project.version} when not set. -->
    <!-- <dockerImageName>my-app:1.0.0</dockerImageName> -->

    <!-- Base Docker image for the generated Dockerfile (default: nginx:alpine) -->
    <dockerBaseImage>nginx:alpine</dockerBaseImage>

    <!-- Path to a custom Dockerfile. When set the plugin uses this file instead of
         generating one. Resolved relative to the project base directory. -->
    <!-- <dockerfilePath>src/main/docker/Dockerfile</dockerfilePath> -->

    <!-- Additional arguments passed to 'docker build'.
         For example: - -no-cache, - -build-arg FOO=bar -->
    <!--
    <dockerBuildArgs>
      <arg>--no-cache</arg>
    </dockerBuildArgs>
    -->

    <!-- ── Nginx Configuration ── -->

    <!-- Enable HTTPS in the generated nginx.conf (default: false).
         When true, an SSL server block on port 443 is generated and
         HTTP (port 80) redirects to HTTPS by default. -->
    <nginxHttps>false</nginxHttps>

    <!-- SSL certificate path inside the container (default: /etc/nginx/ssl/server.crt).
         Only used when nginxHttps is true. -->
    <!-- <nginxSslCertificate>/etc/nginx/ssl/server.crt</nginxSslCertificate> -->

    <!-- SSL private key path inside the container (default: /etc/nginx/ssl/server.key).
         Only used when nginxHttps is true. -->
    <!-- <nginxSslCertificateKey>/etc/nginx/ssl/server.key</nginxSslCertificateKey> -->

    <!-- Redirect HTTP to HTTPS when nginxHttps is true (default: true).
         Set to false to serve the app over both HTTP and HTTPS. -->
    <!-- <nginxHttpRedirect>true</nginxHttpRedirect> -->

    <!-- The server_name directive in the generated nginx.conf (default: localhost).
         Set to your domain or _ for a catch-all. -->
    <!-- <nginxServerName>example.com</nginxServerName> -->

    <!-- Additional raw nginx directives injected into the server block.
         Each entry is written as-is with full nginx syntax. -->
    <!--
    <nginxCustomEntries>
      <entry>client_max_body_size 50m;</entry>
      <entry>proxy_read_timeout 300s;</entry>
    </nginxCustomEntries>
    -->

    <!-- Path to an existing nginx.conf file to use verbatim instead of generating one.
         Resolved relative to the project base directory. -->
    <!-- <nginxConfigFile>src/main/docker/nginx.conf</nginxConfigFile> -->

    <!-- Enable JPMS ModuleLayer classloading (default: true).
         When true and named/automatic modules are detected, the plugin builds a
         ModuleLayer so that ServiceLoader/provider discovery works for providers
         declared in module-info. Falls back to a flat URLClassLoader on failure. -->
    <jpmsEnabled>true</jpmsEnabled>

    <!-- Restrict compilation to specific @NgApp implementation classes.
         When omitted, all @NgApp implementations are discovered automatically. -->
    <!--
    <appClasses>
      <appClass>com.example.MyNgApp</appClass>
    </appClasses>
    -->
  </configuration>
</plugin>
```

## Configuration Parameters

| Parameter | Property | Type | Default | Description |
|---|---|---|---|---|
| `skip` | `jwebmp.angular.skip` | `boolean` | `false` | Skip plugin execution entirely. |
| `outputDirectory` | `jwebmp.angular.outputDirectory` | `String` | `${project.build.directory}` | Base directory for generated output. Relative paths are resolved against the project base directory. |
| `classpathScope` | `jwebmp.angular.classpathScope` | `String` | `runtime` | Classpath scope for classloader construction. Accepted values: `runtime`, `compile`, `test`, `packaged` / `package` / `artifact`. |
| `installDependencies` | `jwebmp.angular.install` | `boolean` | `false` | Run `npm install` after TypeScript compilation. |
| `installForce` | `jwebmp.angular.install.force` | `boolean` | `false` | Pass `--force` to `npm install`. Only effective when `installDependencies` is `true`. |
| `ensureToolchain` | `jwebmp.angular.ensureToolchain` | `boolean` | `false` | Verify and optionally install Node/npm and `@angular/cli`. |
| `downloadNpm` | `jwebmp.angular.downloadNpm` | `boolean` | `false` | Download Node/npm when not found locally. Only effective when `ensureToolchain` is `true`. |
| `nodeVersion` | `jwebmp.angular.nodeVersion` | `String` | `20.18.1` | Node.js version to download (used only when `downloadNpm` is `true`). |
| `angularCliVersion` | `jwebmp.angular.angularCliVersion` | `String` | *(none)* | Override the `@angular/cli` version. When unset, the existing `package.json` value or built-in default is used. |
| `buildAngular` | `jwebmp.angular.build` | `boolean` | `false` | Run the Angular production build (`ng build`) after compilation. |
| `buildDockerImage` | `jwebmp.angular.docker` | `boolean` | `false` | Build a Docker image containing the Angular production build served by nginx. Requires the Angular build output to exist. |
| `dockerImageName` | `jwebmp.angular.docker.imageName` | `String` | `${artifactId}:${version}` | Docker image name (repository[:tag]). Derived from Maven project coordinates when not set. |
| `dockerBaseImage` | `jwebmp.angular.docker.baseImage` | `String` | `nginx:alpine` | Base image for the generated Dockerfile. |
| `dockerfilePath` | `jwebmp.angular.docker.dockerfile` | `String` | *(none)* | Path to a custom Dockerfile. When set, the plugin uses this file instead of generating one. Relative paths are resolved against the project base directory. |
| `dockerBuildArgs` | `jwebmp.angular.docker.args` | `List<String>` | *(none)* | Additional arguments passed to `docker build` (e.g. `--no-cache`, `--build-arg KEY=VAL`). |
| `nginxHttps` | `jwebmp.angular.nginx.https` | `boolean` | `false` | Enable HTTPS in the generated nginx configuration. Adds an SSL server block on port 443. |
| `nginxSslCertificate` | `jwebmp.angular.nginx.sslCertificate` | `String` | `/etc/nginx/ssl/server.crt` | Path to the SSL certificate *inside the container*. Only used when `nginxHttps` is `true`. |
| `nginxSslCertificateKey` | `jwebmp.angular.nginx.sslCertificateKey` | `String` | `/etc/nginx/ssl/server.key` | Path to the SSL private key *inside the container*. Only used when `nginxHttps` is `true`. |
| `nginxHttpRedirect` | `jwebmp.angular.nginx.httpRedirect` | `boolean` | `true` | When `nginxHttps` is enabled, redirect HTTP (port 80) to HTTPS with a 301. Set to `false` to serve over both. |
| `nginxServerName` | `jwebmp.angular.nginx.serverName` | `String` | `localhost` | The `server_name` directive in the generated nginx configuration. Use a domain name or `_` for a catch-all. |
| `nginxCustomEntries` | `jwebmp.angular.nginx.customEntries` | `List<String>` | *(none)* | Additional raw nginx directives injected into the primary server block. Each entry is written as-is. |
| `nginxConfigFile` | `jwebmp.angular.nginx.configFile` | `String` | *(none)* | Path to an existing nginx configuration file used verbatim instead of generating one. Relative paths are resolved against the project base directory. |
| `jpmsEnabled` | `jwebmp.angular.jpms.enabled` | `boolean` | `true` | Attempt JPMS `ModuleLayer` classloading. When modules are detected on the classpath, a `ModuleLayer` is built so `ServiceLoader` / provider discovery works for `module-info` declarations. Falls back to a flat `URLClassLoader` when no modules are found or initialisation fails. |
| `appClasses` | `jwebmp.angular.apps` | `List<String>` | *(auto-discover)* | Fully qualified class names of `@NgApp` implementations to compile. When empty, all `@NgApp` classes are discovered via classpath scanning. |

## System / Command-Line Properties

Every configuration parameter can be set via `-D` on the command line:

```shell
mvn process-classes \
  -Djwebmp.angular.skip=false \
  -Djwebmp.angular.install=true \
  -Djwebmp.angular.install.force=true \
  -Djwebmp.angular.ensureToolchain=true \
  -Djwebmp.angular.downloadNpm=true \
  -Djwebmp.angular.nodeVersion=20.18.1 \
  -Djwebmp.angular.angularCliVersion=20 \
  -Djwebmp.angular.build=true \
  -Djwebmp.angular.docker=true \
  -Djwebmp.angular.docker.imageName=my-app:1.0.0 \
  -Djwebmp.angular.docker.baseImage=nginx:alpine \
  -Djwebmp.angular.docker.dockerfile=src/main/docker/Dockerfile \
  -Djwebmp.angular.nginx.https=true \
  -Djwebmp.angular.nginx.sslCertificate=/etc/nginx/ssl/server.crt \
  -Djwebmp.angular.nginx.sslCertificateKey=/etc/nginx/ssl/server.key \
  -Djwebmp.angular.nginx.httpRedirect=true \
  -Djwebmp.angular.nginx.serverName=example.com \
  -Djwebmp.angular.nginx.configFile=src/main/docker/nginx.conf \
  -Djwebmp.angular.apps=com.example.App1,com.example.App2 \
  -Djwebmp.angular.outputDirectory=/path/to/output \
  -Djwebmp.angular.classpathScope=compile \
  -Djwebmp.angular.jpms.enabled=true
```

## Execution Lifecycle

For each discovered (or configured) `@NgApp` implementation the plugin executes these steps **in order**:

```
1. compileApp()          — always runs (generates TypeScript, config files, angular.json, package.json, etc.)
2. ensureToolchain()     — only when ensureToolchain=true
3. installDependencies() — only when installDependencies=true
4. buildAngularApp()     — only when buildAngular=true
5. buildDockerImage()    — only when buildDockerImage=true
```

A typical CI pipeline might enable all five steps:

```xml
<configuration>
  <ensureToolchain>true</ensureToolchain>
  <downloadNpm>true</downloadNpm>
  <installDependencies>true</installDependencies>
  <buildAngular>true</buildAngular>
  <buildDockerImage>true</buildDockerImage>
</configuration>
```

While local development usually only needs the default (step 1) to regenerate TypeScript sources.

## Output Directory Resolution

The plugin resolves the output directory using the following precedence chain (first match wins):

| Priority | Source | Description |
|---|---|---|
| 1 | `<outputDirectory>` | Plugin configuration value (resolved against project basedir if relative). |
| 2 | `-Djwebmp.outputDirectory` | JVM system property. |
| 3 | `jwebmp.outputDirectory` env var | Environment variable. |
| 4 | `-Djwebmp` | JVM system property (base directory). |
| 5 | `jwebmp` env var | Environment variable (base directory). |
| 6 | `${project.build.directory}` | Maven build directory (typically `target/`). |

Once resolved, the value is published as the `jwebmp.outputDirectory` system property so downstream code (including `AppUtils`) can locate it.

The final on-disk layout for each app is:

```
<outputDirectory>/webroot/<appName>/
├── angular.json
├── package.json
├── tsconfig.json
├── tsconfig.app.json
├── .npmrc
├── .gitignore
├── src/
│   ├── main.ts
│   └── app/
│       └── ... (generated components, modules, services)
├── public/
│   └── ... (static assets)
└── dist/
    └── jwebmp/
        └── browser/
            └── ... (production build output)
```

## Classpath Scope Details

The plugin requires **test-scoped** dependency resolution (`requiresDependencyResolution = TEST`) so it can assemble the full classpath regardless of which scope you select:

| Scope | Classpath Elements Used | Notes |
|---|---|---|
| `runtime` *(default)* | Project output directory + runtime classpath | Standard for most use cases. |
| `compile` | Project output directory + compile classpath | Useful when runtime deps are not yet available. |
| `test` | Project output + test output + test classpath | Superset — includes all scopes. |
| `packaged` | Packaged artifact JAR/WAR + runtime classpath | Requires the `package` phase to have completed first. Also accepted as `package` or `artifact`. |

## JPMS Module-Layer Support

When `jpmsEnabled` is `true` (the default), the plugin uses `java.lang.module.ModuleFinder` to detect named and automatic modules among the resolved classpath entries. If modules are found:

1. A `Configuration` is resolved against the boot layer.
2. A single-loader `ModuleLayer` is created via `defineModulesWithOneLoader`.
3. The resulting `ClassLoader` is set as the thread context classloader.

This enables `ServiceLoader` and provider discovery for providers declared in `module-info.java` (rather than only `META-INF/services`), which matches the runtime behaviour of modular JWebMP applications.

If no modules are detected, or if `ModuleLayer` initialisation fails, the plugin logs a warning and falls back to a flat `URLClassLoader` transparently.

Disable with:

```xml
<jpmsEnabled>false</jpmsEnabled>
```

or:

```shell
-Djwebmp.angular.jpms.enabled=false
```

## Docker Image Generation

When `buildDockerImage` is `true`, the plugin produces an OCI container image for each compiled app after the Angular build completes. The image serves the production build output via nginx.

### How it works

1. **Dockerfile resolution** — If `dockerfilePath` points to an existing file, that file is used. Otherwise the plugin auto-generates a `Dockerfile` and `nginx.conf` inside the app output directory (existing files are never overwritten).
2. **Build output validation** — The plugin verifies that `dist/jwebmp/browser/` exists under the app directory. If missing, the build fails with a clear error.
3. **Image build** — `docker build` is executed with the app directory as the build context. The image is tagged with the resolved `dockerImageName`.

### Generated Dockerfile

When no custom Dockerfile is provided, the plugin generates:

```dockerfile
FROM nginx:alpine

# Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy Angular production build output
COPY dist/jwebmp/browser/ /usr/share/nginx/html/

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

When `nginxHttps` is enabled, the `EXPOSE` line becomes `EXPOSE 80 443`.

The base image can be changed with `<dockerBaseImage>`.

### Generated nginx.conf

The generated nginx configuration supports Angular client-side routing (all unknown routes fall back to `index.html`) and includes aggressive caching for hashed static assets plus basic security headers.

#### HTTP-only (default)

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
}
```

#### HTTPS enabled (`nginxHttps=true`)

When HTTPS is enabled, the primary server block listens on 443 with TLS and a secondary block on port 80 performs a 301 redirect (unless `nginxHttpRedirect` is set to `false`):

```nginx
server {
    listen 443 ssl;
    server_name localhost;

    ssl_certificate     /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    root /usr/share/nginx/html;
    index index.html;
    # ... (same location blocks, caching, and security headers)
}

server {
    listen 80;
    server_name localhost;
    return 301 https://$host$request_uri;
}
```

Mount your certificates into the container at runtime:

```shell
docker run -p 443:443 -p 80:80 \
  -v /path/to/certs/server.crt:/etc/nginx/ssl/server.crt:ro \
  -v /path/to/certs/server.key:/etc/nginx/ssl/server.key:ro \
  my-app:1.0.0
```

Or use custom paths via plugin configuration:

```xml
<configuration>
  <buildDockerImage>true</buildDockerImage>
  <nginxHttps>true</nginxHttps>
  <nginxSslCertificate>/certs/tls.crt</nginxSslCertificate>
  <nginxSslCertificateKey>/certs/tls.key</nginxSslCertificateKey>
  <nginxServerName>app.example.com</nginxServerName>
</configuration>
```

### Custom nginx entries

Inject arbitrary nginx directives into the primary server block:

```xml
<nginxCustomEntries>
  <entry>client_max_body_size 50m;</entry>
  <entry>proxy_read_timeout 300s;</entry>
  <entry>add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;</entry>
</nginxCustomEntries>
```

These entries are appended after the standard location blocks and security headers.

### Custom nginx.conf file

Supply your own nginx configuration file when the generated one is not sufficient:

```xml
<configuration>
  <buildDockerImage>true</buildDockerImage>
  <nginxConfigFile>src/main/docker/nginx.conf</nginxConfigFile>
</configuration>
```

When `nginxConfigFile` is set and the file exists, the plugin copies it verbatim — all `nginxHttps`, `nginxServerName`, and `nginxCustomEntries` settings are ignored.

### Custom Dockerfile

Point to your own Dockerfile when you need a different server, multi-stage build, or additional layers:

```xml
<configuration>
  <buildAngular>true</buildAngular>
  <buildDockerImage>true</buildDockerImage>
  <dockerfilePath>src/main/docker/Dockerfile</dockerfilePath>
  <dockerImageName>registry.example.com/my-app:${project.version}</dockerImageName>
</configuration>
```

### Docker build arguments

Pass additional flags to `docker build`:

```xml
<dockerBuildArgs>
  <arg>--no-cache</arg>
  <arg>--build-arg</arg>
  <arg>API_URL=https://api.example.com</arg>
</dockerBuildArgs>
```

## App Discovery

The plugin resolves which `@NgApp` implementations to compile using this strategy:

1. **Explicit configuration** — If `<appClasses>` is provided in the plugin configuration, only those classes are compiled.
2. **System property** — If `-Djwebmp.angular.apps=com.example.App1,com.example.App2` is set, those classes are used (comma-separated).
3. **Auto-discovery** — If neither is specified, the plugin scans the classpath for all classes annotated with `@NgApp` and compiles each one.

Each resolved class must implement `INgApp<?>`. Classes that do not are logged as warnings and skipped.

## Dependencies

The plugin itself depends on:

| Dependency | Scope | Purpose |
|---|---|---|
| `com.jwebmp.plugins:angular` | compile | TypeScript compiler, code generators, and Angular setup logic. |
| `org.apache.maven:maven-plugin-api` | provided | Maven plugin API. |
| `org.apache.maven:maven-core` | provided | Maven project model and lifecycle. |
| `org.apache.maven.plugin-tools:maven-plugin-annotations` | provided | `@Mojo`, `@Parameter` annotations. |
| `com.guicedee:inject` | compile | Guice dependency injection context (`IGuiceContext`). |
| `com.guicedee:vertx` | compile | Vert.x integration (used by the compiler). |
| `com.guicedee:web` | compile | Web infrastructure support. |

All transitive versions are managed by the `com.guicedee:guicedee-bom` (imported in `<dependencyManagement>`).

## Examples

### Minimal — TypeScript generation only

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals><goal>build</goal></goals>
    </execution>
  </executions>
</plugin>
```

### Full CI build — toolchain + install + build

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals><goal>build</goal></goals>
    </execution>
  </executions>
  <configuration>
    <ensureToolchain>true</ensureToolchain>
    <downloadNpm>true</downloadNpm>
    <nodeVersion>20.18.1</nodeVersion>
    <installDependencies>true</installDependencies>
    <buildAngular>true</buildAngular>
    <classpathScope>compile</classpathScope>
  </configuration>
</plugin>
```

### Full CI build + Docker image

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals><goal>build</goal></goals>
    </execution>
  </executions>
  <configuration>
    <ensureToolchain>true</ensureToolchain>
    <downloadNpm>true</downloadNpm>
    <installDependencies>true</installDependencies>
    <buildAngular>true</buildAngular>
    <buildDockerImage>true</buildDockerImage>
    <dockerImageName>registry.example.com/my-app:${project.version}</dockerImageName>
  </configuration>
</plugin>
```

### Docker image with HTTPS and custom nginx

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals><goal>build</goal></goals>
    </execution>
  </executions>
  <configuration>
    <buildAngular>true</buildAngular>
    <buildDockerImage>true</buildDockerImage>
    <nginxHttps>true</nginxHttps>
    <nginxServerName>app.example.com</nginxServerName>
    <nginxCustomEntries>
      <entry>client_max_body_size 100m;</entry>
      <entry>add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;</entry>
    </nginxCustomEntries>
  </configuration>
</plugin>
```

### Specific apps with custom output

```xml
<plugin>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-maven-plugin</artifactId>
  <version>2.0.0-RC1</version>
  <executions>
    <execution>
      <goals><goal>build</goal></goals>
    </execution>
  </executions>
  <configuration>
    <outputDirectory>${project.basedir}/frontend</outputDirectory>
    <appClasses>
      <appClass>com.example.AdminApp</appClass>
      <appClass>com.example.PublicApp</appClass>
    </appClasses>
    <installDependencies>true</installDependencies>
    <buildAngular>true</buildAngular>
  </configuration>
</plugin>
```

### Skip via command line

```shell
mvn package -Djwebmp.angular.skip=true
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `No @NgApp implementations found; skipping TypeScript build.` | No `@NgApp`-annotated classes on the resolved classpath. | Ensure your app module is a dependency and the correct `classpathScope` is set. |
| `Packaged artifact not found.` | `classpathScope=packaged` but the `package` phase has not run. | Bind the goal to the `package` phase or later, or switch to `runtime`/`compile` scope. |
| `Failed to initialize JPMS ModuleLayer` | Module resolution conflict (split packages, missing requires). | Check the warning message; fix module descriptors or set `jpmsEnabled=false` to fall back. |
| `Configured app class does not implement INgApp` | The class listed in `<appClasses>` exists but does not implement `INgApp<?>`. | Verify the class implements `INgApp` and is annotated with `@NgApp`. |
| Dependency installation fails on the current OS | `DependencyManager.isOsSupported()` returned `false`. | Check the log for the unsupported OS name; run npm manually or on a supported platform. |
| `Angular build output not found at …` | `buildDockerImage=true` but the `dist/jwebmp/browser/` directory does not exist. | Enable `buildAngular=true` so the Angular build runs before the Docker step, or verify a previous build populated the dist directory. |
| `Docker build failed with exit code …` | The `docker build` command returned a non-zero exit code. | Check the build output above the error. Common causes: Docker daemon not running, Dockerfile syntax error, or network issues pulling the base image. |
| `Could not resolve or generate a Dockerfile` | The plugin could not write a Dockerfile to the app output directory. | Verify the output directory is writable and disk space is available. |

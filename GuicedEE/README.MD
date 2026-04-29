# GuicedEE

[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.guicedee/guice-injection)](https://central.sonatype.com/artifact/com.guicedee/guice-injection)

![Java 25+](https://img.shields.io/badge/Java-25%2B-green)
![Guice 7](https://img.shields.io/badge/Guice-7%2B-green)
![Vert.X 5](https://img.shields.io/badge/Vert.x-5%2B-green)
![Maven 4](https://img.shields.io/badge/Maven-4%2B-green)
![JPMS](https://img.shields.io/badge/JPMS-Level%203-green)

**A modular, Guice-first application platform for building reactive Java services on Vert.x 5.**

GuicedEE wires together [Google Guice](https://github.com/google/guice), [Vert.x 5](https://vertx.io/), and the [MicroProfile](https://microprofile.io/) specification ecosystem into a cohesive, JPMS-native platform. Every module is designed around SPI discovery, annotation-driven configuration, and environment variable overrides — so you get production-ready defaults with zero boilerplate.

> **Website:** [https://guicedee.com](https://guicedee.com)
> **Organization:** [https://github.com/GuicedEE](https://github.com/GuicedEE)

---

## ✨ Why GuicedEE?

- **Guice-first DI** — All lifecycle, routing, persistence, messaging, and telemetry are wired through a single Guice injector. `@Inject` works everywhere.
- **Vert.x 5 reactive core** — Non-blocking HTTP, WebSockets, event bus, SQL clients, and RabbitMQ — all on the Vert.x event loop with Mutiny `Uni`/`Multi` support.
- **MicroProfile standards** — Config, Health, Metrics, OpenAPI, Fault Tolerance, and Telemetry follow Eclipse MicroProfile specifications.
- **JPMS Level 3** — Every module ships a proper `module-info.java` with explicit `exports`, `requires`, `provides`, and `uses` directives.
- **SPI + ClassGraph discovery** — Modules, lifecycle hooks, consumers, REST resources, health checks, and more are discovered automatically at startup.
- **Annotation-driven, environment-overridable** — Configure everything with annotations; override anything with environment variables or system properties at deploy time.

---

## 📦 Quick Start

### 1. Import the BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.guicedee</groupId>
            <artifactId>guicedee-bom</artifactId>
            <version>2.0.1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2. Add modules you need

```xml
<dependencies>
    <dependency>
        <groupId>com.guicedee</groupId>
        <artifactId>inject</artifactId>
    </dependency>
    <dependency>
        <groupId>com.guicedee</groupId>
        <artifactId>web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.guicedee</groupId>
        <artifactId>rest</artifactId>
    </dependency>
</dependencies>
```

### 3. Bootstrap

```java
IGuiceContext.registerModuleForScanning.add("my.app");
IGuiceContext.instance();
// HTTP server is listening on :8080, REST routes are registered, DI is live
```

### 4. Write a REST endpoint

```java
@Path("/hello")
public class HelloResource {

    @Inject
    private GreetingService greeter;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello(@QueryParam("name") String name) {
        return greeter.greet(name);
    }
}
```

### 5. Declare your JPMS module

```java
module my.app {
    requires com.guicedee.guicedinjection;
    requires com.guicedee.vertx.web;
    requires com.guicedee.rest;

    provides com.guicedee.client.services.lifecycle.IGuiceModule
        with my.app.AppModule;
}
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Your Application                            │
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────────┤
│   REST   │WebSockets│Persistence│ RabbitMQ │Telemetry │  OpenAPI     │
│  (JAX-RS)│ (RFC6455)│(Hibernate)│  (AMQP)  │  (OTLP)  │ (Swagger)   │
├──────────┴──────────┴──────────┴──────────┴──────────┴──────────────┤
│                    Vert.x Web · HTTP/HTTPS · Router                 │
├─────────────────────────────────────────────────────────────────────┤
│              Guiced Vert.x · EventBus · Verticles · Codecs          │
├──────────┬──────────┬──────────┬─────────────────────────────────────┤
│  Health  │ Metrics  │  Config  │         Fault Tolerance             │
│(MP Health)│(Dropwiz.)│(SmallRye)│           (MP FT)                  │
├──────────┴──────────┴──────────┴─────────────────────────────────────┤
│        GuicedEE Inject · Guice Injector · ClassGraph · SPI          │
├─────────────────────────────────────────────────────────────────────┤
│     Client SPI · Lifecycle Hooks · CallScope · Representations      │
├─────────────────────────────────────────────────────────────────────┤
│             BOM · Parent POM · Services (JPMS wrappers)             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 📂 Module Catalog

### Foundation

| Module | Artifact | Description |
|--------|----------|-------------|
| [**Client**](client/) | `com.guicedee:client` | Client SPI — lifecycle hooks, scoping primitives, and contracts that all modules program against |
| [**Inject**](inject/) | `com.guicedee:inject` | Runtime engine — classpath scanning via ClassGraph, Guice injector creation, startup/shutdown lifecycle |
| [**Config**](config/) | `com.guicedee.microprofile:config` | MicroProfile Config via SmallRye — `@ConfigProperty` injection from env vars, system props, and properties files |

### Reactive Core

| Module | Artifact | Description |
|--------|----------|-------------|
| [**Vert.x**](vertx/) | `com.guicedee:vertx` | Guice-first Vert.x 5 integration — annotation-driven event bus consumers/publishers, verticle management, codecs |
| [**Web**](web/) | `com.guicedee:web` | HTTP/HTTPS server bootstrap — auto-start Vert.x Web with Router, BodyHandler, TLS, and SPI extension points |

### Application Layer

| Module | Artifact | Description |
|--------|----------|-------------|
| [**REST**](rest/) | `com.guicedee:rest` | Jakarta REST (JAX-RS) adapter — `@Path`/`@GET`/`@POST` routes auto-registered on Vert.x Router via ClassGraph |
| [**REST Client**](rest-client/) | `com.guicedee:rest-client` | Annotation-driven REST client — `@Endpoint` + `@Named` fields, typed `RestClient<Send, Receive>`, reactive `Uni` responses |
| [**WebSockets**](websockets/) | `com.guicedee:websockets` | RFC 6455 WebSocket support — call-scoped connections, action-based message routing, group broadcasting |
| [**Persistence**](persistence/) | `com.guicedee:persistence` | Reactive JPA — Hibernate Reactive 7, `Mutiny.SessionFactory`, multi-database support, `@EntityManager` scoping |
| [**RabbitMQ**](rabbitmq/) | `com.guicedee:rabbitmq` | Annotation-driven RabbitMQ — connections, exchanges, queues, consumers, and publishers with Vert.x RabbitMQ client |
| [**Web Services**](webservices/) | `com.guicedee:guiced-webservices` | SOAP/JAX-WS with Apache CXF conventions — `@WebService` endpoints, WS-Security, WSDL-first or code-first |

### Observability

| Module | Artifact | Description |
|--------|----------|-------------|
| [**Health**](health/) | `com.guicedee:health` | MicroProfile Health — `@Liveness`, `@Readiness`, `@Startup` checks exposed as `/health` JSON endpoints |
| [**Metrics**](metrics/) | `com.guicedee:metrics` | Dropwizard + MicroProfile Metrics — `@Counted`, `@Timed`, Prometheus scrape endpoint, Graphite reporting |
| [**Telemetry**](telemetry/) | `com.guicedee:guiced-telemetry` | OpenTelemetry distributed tracing — `@Trace`, `@SpanAttribute`, OTLP export, Log4j2 correlation |
| [**OpenAPI**](openapi/) | `com.guicedee:openapi` | OpenAPI 3.1 spec generation — scans JAX-RS resources, serves `/openapi.json` and `/openapi.yaml` |
| [**Swagger UI**](swagger-ui/) | `com.guicedee:guiced-swagger-ui` | Browsable Swagger UI — auto-mounted at `/swagger/`, reads from `/openapi.json` with zero code |

### Hardware & IoT

| Module | Artifact | Description |
|--------|----------|-------------|
| [**Cerial**](cerial/) | `com.guicedee:cerial` | Serial port connectivity — `@Named` port injection, auto-reconnect, idle monitoring, health reporting |

### AI & Integration

| Module | Artifact | Description |
|--------|----------|-------------|
| [**MCP**](mcp/) | MCP Server | Model Context Protocol server — tools, resources, prompts, and completion for AI agent integration |

### Migration & Compatibility

> These modules are **not** part of the GuicedEE foundation. They exist to assist projects migrating from other DI frameworks (Jakarta CDI, Spring, etc.) to the native Guice-first model. New projects should use Guice annotations and APIs directly.

| Module | Artifact | Description |
|--------|----------|-------------|
| [**CDI**](cdi/) | `com.guicedee:cdi` | Jakarta CDI compatibility bridge — maps `@Inject`, `@Named`, `@ApplicationScoped` to Guice equivalents with `BeanManager` adapter. Intended for migration assistance, not as a foundation layer. |

### Infrastructure

| Module | Artifact | Description |
|--------|----------|-------------|
| [**BOM**](bom/) | `com.guicedee:guicedee-bom` | Bill of Materials — centralized version management for all GuicedEE artifacts |
| [**Parent**](parent/) | `com.guicedee:guicedee-parent` | Parent POM — shared plugin management, Java toolchain, encoding, and build configuration |
| [**Services**](services/) | Various | JPMS wrappers — third-party libraries repackaged with `module-info.java` (Hibernate, Guice, Jackson, Vert.x, etc.) |
| [**Representations**](representations/) | `com.guicedee:guiced-representations` | DDD value objects and representation interfaces shared across modules |
| [**Fault Tolerance**](fault-tolerance/) | `com.guicedee:fault-tolerance` | MicroProfile Fault Tolerance implementation (retry, circuit breaker, timeout, bulkhead, fallback) |

### Web & Docs

| Module | Artifact | Description |
|--------|----------|-------------|
| [**Website**](website/) | — | Public website at [guicedee.com](https://guicedee.com) — module catalog, app builder, documentation |

---

## 🔄 Lifecycle

GuicedEE manages a deterministic startup/shutdown lifecycle through SPI hooks:

```
┌─ IGuiceConfigurator         Configure ClassGraph scan
├─ IGuicePreStartup           Pre-scan initialization (health, metrics, telemetry registration)
├─ ClassGraph Scan             Discover annotated classes, SPI providers, modules
├─ IGuiceModule                Guice module binding (configure phase)
├─ Injector Creation           Build the Guice injector
├─ IGuicePostStartup           Post-injection startup (HTTP server, persistence, consumers)
│   ├─ Vert.x Web server start
│   ├─ REST route registration
│   ├─ WebSocket handler mount
│   ├─ RabbitMQ connection & consumer setup
│   ├─ Persistence unit initialization
│   ├─ Health check registration
│   └─ Metrics endpoint registration
└─ IGuicePreDestroy            Graceful shutdown (connections, exporters, ports)
```

Override `sortOrder()` on any lifecycle hook to control execution order.

---

## ⚙️ Configuration Philosophy

Every GuicedEE module follows the same three-tier configuration pattern:

1. **Annotations** — `@TelemetryOptions`, `@MetricsOptions`, `@HealthOptions`, `@RabbitConnectionOptions`, `@Endpoint`, etc.
2. **Environment variables / system properties** — Override any annotation attribute at deploy time without code changes.
3. **SPI hooks** — Implement configurator interfaces (`VertxConfigurator`, `VertxHttpServerOptionsConfigurator`, `RestClientConfigurator`, etc.) for programmatic customization.

```java
// Annotation-driven
@TelemetryOptions(serviceName = "my-service", otlpEndpoint = "http://localhost:4318")

// Environment override (no code change needed)
// TELEMETRY_SERVICE_NAME=my-service
// TELEMETRY_OTLP_ENDPOINT=http://tempo:4318
```

---

## 🧪 Testing

Each module includes comprehensive tests. Run everything from the root:

```bash
mvn -B -ntp test
```

Or test a specific module:

```bash
cd inject && mvn -B -ntp test
cd rest && mvn -B -ntp test
cd persistence && mvn -B -ntp test
```

Modules that require external services (databases, RabbitMQ, telemetry collectors) use Testcontainers or in-memory alternatives:

```java
// Telemetry in-memory testing
@TelemetryOptions(useInMemoryExporters = true)
```

---

## 🛠️ Building from Source

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/GuicedEE/GuicedEE.git
cd GuicedEE

# Build all modules
mvn -B -ntp clean install

# Build a specific module
cd inject && mvn -B -ntp clean install
```

### Requirements

| Tool | Version |
|------|---------|
| JDK | 25+ |
| Maven | 4+ |
| Git | 2.x+ |

---

## 📚 Key Concepts

### SPI & ServiceLoader

GuicedEE modules discover each other through JPMS `provides`/`uses` directives. Register your Guice modules, lifecycle hooks, REST resources, health checks, and message consumers via `ServiceLoader`:

```java
module my.app {
    requires com.guicedee.guicedinjection;

    provides com.guicedee.client.services.lifecycle.IGuiceModule
        with my.app.AppModule;
    provides com.guicedee.client.services.lifecycle.IGuicePostStartup
        with my.app.AppStartup;
}
```

### CallScope

GuicedEE provides a `@CallScope` for request-scoped injection (WebSocket connections, REST requests). Scoped properties are available via `CallScopeProperties`.

### CRTP Fluent APIs

Configuration and builder classes use the Curiously Recurring Template Pattern for type-safe fluent chaining:

```java
connection.setBaudRate(BaudRate.$9600)
          .setDataBits(DataBits.$8)
          .setParity(Parity.None)
          .connect();
```

### @Verticle

Annotate packages with `@Verticle` to scope components to dedicated Vert.x verticles. When absent, components deploy to the default verticle managed by Guiced Vert.x.

---

## 🗺️ Ecosystem

GuicedEE is part of a larger ecosystem:

| Project | Description |
|---------|-------------|
| [**EntityAssist**](../EntityAssist/) | Reactive entity CRUD framework built on GuicedEE Persistence |
| [**JWebMP**](../JWebMP/) | Server-side web component framework with Vert.x rendering |
| [**ActivityMaster**](../ActivityMaster/) | Domain-driven application modules (profiles, tasks, payments, etc.) |
| [**AIRules**](../AIRules/) | AI agent rules, prompts, and workspace policies |

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository and create a feature branch
2. **Follow JPMS conventions** — every module must have a proper `module-info.java`
3. **Write tests** — include unit tests for new functionality
4. **Use glossary-aligned names** — check `GLOSSARY.md` in each module
5. **Update documentation** — keep READMEs and `RULES.md` current
6. **Submit a PR** — include a clear description of changes

---

## 📄 License

All GuicedEE modules are licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

```
Copyright 2025 GuicedEE Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```


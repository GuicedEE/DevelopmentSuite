# DevSuite

[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)

![Java 25+](https://img.shields.io/badge/Java-25%2B-green)
![Modular](https://img.shields.io/badge/Modular-JPMS-green)
![Guice 7](https://img.shields.io/badge/Guice-7-green)
![Vert.X 5](https://img.shields.io/badge/Vert.x-5-green)
![Maven 4](https://img.shields.io/badge/Maven-4-green)

<!-- Tech icons row -->
![Hibernate Reactive](https://img.shields.io/badge/Hibernate-Reactive_7-59666C?logo=hibernate)
![Angular](https://img.shields.io/badge/Angular-20-DD0031?logo=angular)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript)

A comprehensive, modular full-stack development suite combining reactive backend services, dynamic frontend frameworks, and enterprise-grade persistence tooling. Built on **Vert.x 5**, **Guice 7**, **Hibernate Reactive 7**, **Angular 21**, and **Java 25+** with full JPMS module system support.

Built on [Vert.x](https://vertx.io/) · [Google Guice](https://github.com/google/guice) · [Hibernate Reactive](https://hibernate.org/reactive/) · [Angular](https://angular.dev/) · [Mutiny](https://smallrye.io/smallrye-mutiny/) · JPMS-compliant · Java 25+

## 📦 Installation

DevSuite is a multi-module Maven reactor project. Clone and build the entire suite or work with individual profiles:

```bash
git clone --recursive https://github.com/your-org/DevSuite.git
cd DevSuite
mvn clean install
```

### Profile-Based Builds

DevSuite organizes modules into several Maven profiles for targeted builds:

```bash
# Build GuicedEE core modules
mvn clean install -P guicedee

# Build GuicedEE services
mvn clean install -P services

# Build JWebMP modules
mvn clean install -P jwebmp

# Build JWebMP plugins
mvn clean install -P jwebmp-plugins

# Build ActivityMaster modules
mvn clean install -P activitymaster

# Build all modules
mvn clean install -P guicedee,services,jwebmp,jwebmp-plugins,activitymaster
```

## ✨ Features

### Core Framework Capabilities

- **Reactive-First Architecture** — Built on Vert.x 5 event loop with Mutiny reactive streams for non-blocking I/O
- **Dependency Injection** — Google Guice 7 with JPMS module system integration and lifecycle management
- **Modular Design** — Full Java Platform Module System (JPMS) support with explicit module boundaries
- **Reactive Persistence** — Hibernate Reactive 7 with Mutiny for truly non-blocking database operations
- **RESTful Services** — JAX-RS endpoints with reactive extensions and OpenAPI/Swagger integration
- **WebSocket Support** — Full-duplex reactive WebSocket communication with message routing and group management
- **Frontend Integration** — Angular 21 with TypeScript 5, server-side rendering support, and reactive data binding
- **Configuration Management** — MicroProfile Config with environment-based configuration and hot-reload
- **Observability** — Integrated metrics (Prometheus), health checks, distributed tracing (OpenTelemetry), and telemetry
- **Security** — Reactive authentication/authorization, JWT tokens, OAuth2/OIDC integration
- **Message Queuing** — RabbitMQ integration for event-driven architectures and pub/sub patterns
- **Testing Framework** — Comprehensive test harness with Testcontainers, JUnit 5, and reactive test utilities

### DevSuite Components

DevSuite is organized into four primary component suites:

## 🏗️ Architecture

### Component Overview

```
DevSuite/
├── GuicedEE/           # Reactive backend framework
│   ├── bom/            # Bill of Materials and version management
│   ├── parent/         # Parent POM with common configuration
│   ├── client/         # Guice context and injection utilities
│   ├── inject/         # Core dependency injection framework
│   ├── vertx/          # Vert.x integration and reactive runtime
│   ├── persistence/    # Hibernate Reactive persistence layer
│   ├── web/            # HTTP server and routing
│   ├── websockets/     # WebSocket support
│   ├── rest/           # JAX-RS REST services
│   ├── rest-client/    # Reactive REST client
│   ├── cerial/         # Serialization and data transformation
│   ├── config/         # MicroProfile Config implementation
│   ├── metrics/        # Metrics and monitoring
│   ├── health/         # Health check endpoints
│   ├── telemetry/      # Distributed tracing and observability
│   ├── cdi/            # CDI compatibility layer
│   ├── rabbitmq/       # RabbitMQ messaging integration
│   ├── openapi/        # OpenAPI specification generation
│   ├── swagger-ui/     # Swagger UI integration
│   ├── mcp/            # Model Context Protocol support
│   ├── fault-tolerance/# Fault tolerance patterns
│   ├── workflows/      # Workflow orchestration
│   ├── webservices/    # SOAP/XML web services
│   ├── website/        # Documentation and website
│   ├── services/       # Third-party library integrations
│   └── representations/# Data representation formats (JSON, XML, Excel)
│
├── JWebMP/             # Java Web Markup Pages - Server-side Angular rendering
│   ├── bom/            # JWebMP Bill of Materials
│   ├── parent/         # Parent POM
│   ├── client/         # Client-side utilities
│   ├── core/           # Core rendering engine
│   ├── testlib/        # Testing library
│   ├── vertx/          # Vert.x integration
│   ├── rabbitcomms/    # RabbitMQ browser communication
│   └── plugins/        # UI component plugins
│       ├── tsclient/       # TypeScript client generation
│       ├── angular/        # Angular integration
│       ├── fontawesome/    # Font Awesome icons
│       ├── fontawesome-pro/# Font Awesome Pro
│       ├── easing/         # Animation easing effects
│       ├── chartjs/        # Chart.js integration
│       ├── fullcalendar/   # FullCalendar integration
│       ├── fullcalendar-pro/# FullCalendar Pro
│       ├── aggrid/         # AG Grid data tables
│       ├── aggrid-enterprise/# AG Grid Enterprise
│       ├── agcharts/       # AG Charts visualization
│       ├── agcharts-enterprise/# AG Charts Enterprise
│       ├── webawesome/     # Web Awesome components
│       ├── webawesome-pro/ # Web Awesome Pro
│       └── angular-maven-plugin/# Angular build integration
│
├── EntityAssist/       # CRTP-based reactive persistence toolkit
│   └── ...            # See EntityAssist/README.md
│
└── ActivityMaster/     # Complete activity and content management platform
    ├── bom/            # ActivityMaster BOM
    ├── parent/         # Parent POM
    ├── client/         # Client SDK
    ├── core/           # Core services
    ├── cerial/         # Cerial integration
    ├── cerial-client/  # Cerial client
    ├── profiles/       # User profile management
    ├── conversations/  # Conversation threads
    ├── documents/      # Document management
    ├── files/          # File storage and retrieval
    ├── forums/         # Forum and discussion boards
    ├── geography/      # Geographic data and services
    ├── images/         # Image processing and storage
    ├── mail/           # Email integration
    ├── notifications/  # Notification system
    ├── payments/       # Payment processing
    ├── realtor/        # Real estate management
    ├── user-sessions/  # Session management
    ├── tasks/          # Task management
    ├── todo/           # To-do list management
    └── wallet/         # Wallet and balance management
```

### Technology Stack

| Layer                | Technologies                                                  |
|----------------------|---------------------------------------------------------------|
| **Language**         | Java 25+ (preview features), TypeScript 5, Kotlin             |
| **Build**            | Maven 4, Angular CLI                                          |
| **Runtime**          | Vert.x 5 (reactive event loop)                                |
| **DI Framework**     | Google Guice 7                                                |
| **Persistence**      | Hibernate Reactive 7, Hibernate ORM 7, Hibernate Validator    |
| **Reactive Streams** | Mutiny 2.x, RxJava 3                                          |
| **Database**         | PostgreSQL (reactive driver via Vert.x SQL Client), MSSQL, H2 |
| **REST**             | JAX-RS (Jakarta REST)                                         |
| **WebServoce**       | JAX-WS (Jakarta REST) Apache CXF                              |
| **WebSocket**        | Vert.x WebSockets, reactive message routing                   |
| **Messaging**        | RabbitMQ (AMQP 0.9.1), Kafka                                  |
| **Frontend**         | Angular 21, TypeScript, Web Components, Shoelace/Web Awesome  |
| **Serialization**    | Jackson (JSON), JAXB (XML), Apache POI (Excel)                |
| **Configuration**    | MicroProfile Config, SmallRye Config                          |
| **Observability**    | OpenTelemetry, Micrometer, Prometheus, Jaeger                 |
| **Testing**          | JUnit 5, Testcontainers, Mockito, Cypress                     |
| **Security**         | Jakarta Security, JWT, OAuth2/OIDC, MSAL4J                    |
| **Module System**    | Java Platform Module System (JPMS)                            |

## 🚀 Quick Start

### Prerequisites

- **Java 25+** (with preview features enabled)
- **Maven 4.0.0+**
- **Node.js 20+** and **npm 10+** (for Angular builds)
- **Docker** (for Testcontainers and local development)
- **PostgreSQL 15+** (or use Testcontainers)

### Environment Setup

1. Clone the repository with submodules:

```bash
git clone --recursive https://github.com/your-org/DevSuite.git
cd DevSuite
```

2. Copy environment configuration:

```bash
cp .env.example .env
# Edit .env with your database credentials and configuration
```

3. Build the entire suite:

```bash
mvn clean install
```

4. Or build specific profiles:

```bash
# GuicedEE framework only
mvn clean install -P guicedee

# JWebMP with plugins
mvn clean install -P jwebmp,jwebmp-plugins

# ActivityMaster platform
mvn clean install -P activitymaster
```

### Running a Sample Application

See individual component READMEs for specific quickstart guides:

- **GuicedEE**: `GuicedEE/README.md`
- **JWebMP**: `JWebMP/README.md`
- **EntityAssist**: `EntityAssist/README.md`
- **ActivityMaster**: `ActivityMaster/README.md`

## 📐 Module Profiles

DevSuite uses Maven profiles to organize builds. Each profile compiles a specific subset of modules:

### GuicedEE Profile (`-P guicedee`)

Core reactive framework modules including dependency injection, Vert.x runtime, persistence, web services, REST, WebSockets, configuration, metrics, health, telemetry, and observability.

**Key Modules**: `bom`, `parent`, `client`, `inject`, `vertx`, `persistence`, `web`, `websockets`, `rest`, `config`, `metrics`, `health`, `telemetry`, `openapi`, `swagger-ui`

### Services Profile (`-P services`)

Third-party library integrations and service wrappers including Apache Commons, Apache CXF, Apache POI, Google Guice extensions, Hibernate modules, Jakarta libraries, JCache implementations, and database drivers.

**Categories**: Apache, Database, Google, Hibernate, Jakarta, JCache, JNI, Libraries, Logging, MicroProfile, Mutiny, RabbitMQ, Security, Swagger, Testing

### JWebMP Profile (`-P jwebmp`)

Java Web Markup Pages core modules for server-side Angular rendering, TypeScript client generation, and reactive frontend integration.

**Key Modules**: `bom`, `parent`, `client`, `core`, `testlib`, `vertx`, `rabbitcomms`

### JWebMP Plugins Profile (`-P jwebmp-plugins`)

UI component libraries and integrations including Angular, AG Grid, AG Charts, FullCalendar, Font Awesome, Web Awesome, and custom TypeScript components.

**Key Plugins**: `angular`, `tsclient`, `aggrid`, `aggrid-enterprise`, `agcharts`, `agcharts-enterprise`, `fullcalendar`, `fullcalendar-pro`, `fontawesome`, `fontawesome-pro`, `webawesome`, `webawesome-pro`, `chartjs`, `easing`

### ActivityMaster Profile (`-P activitymaster`)

Complete activity and content management platform with user profiles, conversations, documents, files, forums, geography, images, mail, notifications, payments, sessions, tasks, and wallet management.

**Key Modules**: `bom`, `parent`, `client`, `core`, `cerial`, `profiles`, `conversations`, `documents`, `files`, `forums`, `geography`, `images`, `mail`, `notifications`, `payments`, `tasks`, `todo`, `wallet`

## ⚙️ Configuration

### Environment Variables

Copy `.env.example` to `.env` for local development. Keep secrets out of version control.

| Variable | Purpose | Default |
|---|---|---|
| `DB_HOST` | Database hostname | `localhost` |
| `DB_PORT` | Database port | `5432` |
| `DB_NAME` | Database name | — |
| `DB_USER` | Database username | — |
| `DB_PASSWORD` | Database password | — |
| `ENVIRONMENT` | Runtime environment | `dev` |
| `PORT` | Application HTTP port | `8080` |
| `TRACING_ENABLED` | Enable distributed tracing | `false` |
| `ENABLE_DEBUG_LOGS` | Enable debug logging | `false` |
| `RABBITMQ_HOST` | RabbitMQ hostname | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ username | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` |

### JPMS Module Configuration

All components are fully JPMS-compliant. Example `module-info.java`:

```java
module com.myapp {
    requires com.guicedee.client;
    requires com.guicedee.vertx;
    requires com.guicedee.persistence;
    requires com.guicedee.web;
    requires com.entityassist;

    opens com.myapp.entities to org.hibernate.orm.core, com.google.guice, com.entityassist;
    opens com.myapp.services to com.google.guice;

    provides com.guicedee.client.services.lifecycle.IGuiceModule
        with com.myapp.AppModule;
}
```

## 🔌 Key Components

### GuicedEE Framework

Reactive backend framework built on Vert.x 5 and Guice 7. Provides dependency injection, persistence, web services, REST APIs, WebSockets, configuration, metrics, health checks, and telemetry.

**Documentation**: `GuicedEE/README.md`

### JWebMP

Server-side rendering framework for Angular applications with TypeScript client generation, reactive data binding, and extensive UI component library.

**Documentation**: `JWebMP/README.md`

### EntityAssist

CRTP-based reactive persistence toolkit providing fluent query builder DSL on top of Hibernate Reactive 7 and Mutiny. Supports reactive CRUD, aggregates, joins, pagination, and bulk operations.

**Documentation**: `EntityAssist/README.md`

### ActivityMaster

Enterprise-grade activity and content management platform with modules for profiles, conversations, documents, files, forums, geography, images, mail, notifications, payments, sessions, tasks, todos, and wallet management.

**Documentation**: `ActivityMaster/README.md`

## 🧪 Testing

DevSuite uses Testcontainers for integration testing and JUnit 5 for unit testing.

### Running Tests

```bash
# Run all tests
mvn clean verify

# Run tests for specific profile
mvn clean verify -P guicedee

# Skip integration tests
mvn clean verify -DskipITs

# Run specific test
mvn test -Dtest=MyTest
```

### Testcontainers Setup

Integration tests automatically spin up PostgreSQL and RabbitMQ containers:

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTest {

    private static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    static void setup() {
        postgres.start();
    }

    @AfterAll
    static void teardown() {
        postgres.stop();
    }
}
```

## 🔒 Vert.x Consumer/Publisher Processing Model

For details on the latest reactive messaging behavior (per-address consumer verticles and publisher-side throttling without message loss), see:

- **GuicedEE Vert.x module documentation**: `GuicedEE/vertx/README.md`

## 📚 Documentation

### Component Documentation

- **GuicedEE**: [`GuicedEE/README.md`](GuicedEE/README.md)
- **JWebMP**: [`JWebMP/README.md`](JWebMP/README.md)
- **EntityAssist**: [`EntityAssist/README.md`](EntityAssist/README.md)
- **ActivityMaster**: [`ActivityMaster/README.md`](ActivityMaster/README.md)

### Specialized Documentation

- **Vert.x Processing**: [`GuicedEE/vertx/README.md`](GuicedEE/vertx/README.md)
- **Persistence**: [`GuicedEE/persistence/README.md`](GuicedEE/persistence/README.md)
- **WebSockets**: [`GuicedEE/websockets/README.md`](GuicedEE/websockets/README.md)
- **RabbitMQ**: [`GuicedEE/rabbitmq/README.md`](GuicedEE/rabbitmq/README.md)
- **RabbitMQ Browser Comms**: [`JWebMP/rabbitcomms/README.md`](JWebMP/rabbitcomms/README.md)

### AI Skills and Guidelines

DevSuite includes an enterprise AI skills repository in the `AIRules/` directory. Skills are modular, self-contained knowledge packs that extend AI agents with specialized workflows, tool integrations, and domain expertise. See [`AIRules/README.md`](AIRules/README.md) for the full skills catalog.

- **Curated skills** (`AIRules/skills/.curated/`): General-purpose skills — architecture, security, TDD, Terraform, Figma, AG Grid, code review, and more.
- **System skills** (`AIRules/skills/.system/`): Project-specific skills for GuicedEE, JWebMP, EntityAssist, and ActivityMaster.

## 🧰 Development Tools

### PowerShell Scripts

DevSuite includes helper scripts for common operations:

```powershell
# Build ActivityMaster modules
.\am.ps1

# Build JWebMP modules
.\jw.ps1

# Build all modules
.\build.ps1

# Deploy artifacts
.\deploy.ps1
```

### Git Submodule Management

DevSuite uses git submodules extensively. To update all submodules:

```bash
git submodule update --init --recursive --remote
```

## 🧭 Project Structure

```
DevSuite/
├── .github/            # GitHub Actions workflows and CI/CD
├── .idea/              # IntelliJ IDEA project configuration
├── ActivityMaster/     # Activity management platform (git submodules)
├── AIRules/            # Enterprise AI skills repository (git submodule)
├── EntityAssist/       # Reactive persistence toolkit (git submodule)
├── GuicedEE/           # Reactive backend framework (git submodules)
├── JWebMP/             # Server-side Angular rendering (git submodules)
├── logs/               # Application logs
├── target/             # Maven build output
├── .env.example        # Environment variable template
├── .gitignore          # Git ignore patterns
├── .gitmodules         # Git submodule configuration
├── pom.xml             # Root Maven POM with profiles
├── README.md           # This file
├── am.ps1              # ActivityMaster build script
├── build.ps1           # Full build script
├── deploy.ps1          # Deployment script
├── jw.ps1              # JWebMP build script
├── remove-rules-submodules.bat  # Windows submodule cleanup
└── remove-rules-submodules.sh   # Unix submodule cleanup
```

## 🤝 Contributing

Issues and pull requests are welcome.

### Guidelines

- Follow existing code style and patterns
- Include tests for new features
- Update documentation for behavior changes
- Ensure JPMS module-info.java is correct
- Run `mvn clean verify` before submitting PR

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests
5. Run the full test suite (`mvn clean verify`)
6. Commit your changes (`git commit -m 'Add amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

## 📄 License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

**DevSuite** — A comprehensive full-stack development platform for reactive, modular, enterprise-grade Java applications.

Built with ❤️ using Java 25+, Vert.x 5, Guice 7, Hibernate Reactive 7, Angular 21, and JPMS.

# GuicedEE 2.6.0

Status: **Maven Central publication blocked by the organization's monthly release-size limit on 16 November 2026. Nothing was published.** The coordinated 2.6.0 artifacts are built, validated and signed in four batches. Central reports that the grace period ended on 13 November and usage resets on 1 December 2026.

## Modular SDKs

All coordinates below use group `com.guicedee.modules.services`. Import the GuicedEE BOM to align wrapper versions; Versioner and StandaloneBOM manage upstream versions.

| Artifact | JPMS module | Prepared upstream version | Purpose |
|---|---|---|---|
| `firebase-admin` | `firebase.admin` | Firebase Admin 9.12.0 | Groups Firebase Admin and its Google SDK closure, including Auth, Messaging and Firestore |
| `commercetools` | `commercetools.sdk.java.api` | Commercetools SDK 20.4.0 | Groups the commerce API, GraphQL client models, RMF and HTTP integration |
| `zstd-jni` | `com.github.luben.zstd_jni` | Zstd JNI 1.5.7-20 | Corrects the upstream descriptor while preserving multi-release classes and native resources |
| `okio` | `okio` | Okio JVM 3.18.2 | Adds an explicit descriptor for the Moneta → OkHttp → Okio dependency chain |

Firebase and Commercetools are independent SDKs. The benchmark uses both to exercise a realistic application dependency graph; applications add only the SDKs they need. Commercetools supplies a GraphQL client, not a GraphQL server.

## Shared modular dependencies

The Commercetools wrapper uses external Netty (including native transports), SLF4J, Commons IO/Lang, Failsafe, Java Money/Moneta, Reactive Streams, Javassist, Zstd and Bouncy Castle modules. Bouncy Castle provider, PKIX, TLS and utility JARs retain their original packages and signatures. SLF4J uses the application's logging provider. OkHttp, modular Okio and Kotlin satisfy Moneta's module requirements.

Commercetools uses **Jackson 3 core/databind only**, with the shared Jackson annotations artifact. Its resolved graph has no Jackson 2 core/databind. This statement does not describe Firebase's privately bundled dependencies or assert a platform-wide Jackson 2 removal.

Automatic support libraries still requiring grouping remain inside the Commercetools module. See the [dependency audit](services/Libraries/commercetools/MODULAR-DEPENDENCY-AUDIT.md) for the exact boundary.

## API documentation

Firebase Javadoc covers the Firebase Admin public API. Commercetools Javadoc covers SDK, GraphQL, HTTP and RMF APIs. Both builds clear stale generated sources and pages, document against the current shaded binary and fail on Javadoc errors. Private relocated implementation libraries are excluded from the API documentation scope.

Kotlin runtime and coroutine wrapper APIs, including Okio, publish Dokka-generated documentation. The Kotlin Mutiny convenience module publishes a module guide alongside its sources.

## Kotlin, CDI and native-image

The release includes `com.guicedee:kotlin`, `com.guicedee:kotlin-mutiny`, and explicit JPMS wrappers for Vert.x Kotlin coroutines and Mutiny Kotlin. The Kotlin runtime supports coroutine lifecycle hooks, context propagation and application-linked jlink packaging.

The GuicedEE BOM also manages `com.guicedee:cdi` and `com.guicedee:native-image`. CDI provides Jakarta annotation integration on the Guice runtime; native-image supplies the GraalVM support module. JVM compatibility tests and a native executable build are distinct checks.

## Application-linked benchmarks

Dedicated Maven jlink modules build the complete application dependency graph with Oracle JDK 25 and generated module launchers. Application classes and dependencies are part of the linked image; automatic-module failures fail the build. The runtime does not include `jdk.jlink`.

The SPI and scanner variants use their generated module paths for JVM runs and their generated launchers for linked runs. Quarkus fast-JAR remains a JVM baseline. Historical JDK-only/classpath jlink measurements are invalid for application-module comparisons and must not be reused as evidence for this release.

## Validation and release boundary

- The fresh 2.6.0 release reactor passed validation and packaging, including API documentation. The artifact audit covers 127 components, 111 versioned JPMS JARs and 460 POM/JAR files. Every JAR has sources and documentation; prepared artifacts match the local Maven install outputs.
- The selected regression run completed 466 tests across client, injector, Vert.x, REST, REST client, persistence, Kotlin, CDI and native-image: **465 passed, one existing DB2 test skipped, zero failures or errors**. Cassandra, MongoDB, SQL Server, MySQL, Oracle and PostgreSQL integration checks ran against Testcontainers databases. Two injector test setup defects were corrected and the injector suite rerun successfully; production runtime sources were unchanged by these fixes.
- The Kotlin example passed module-path validation, JVM execution, full application jlink and linked execution (`GuicedEE/kotlin/target/runtime-verification-20261116-101933/results.json` in DevSuite).
- Eight freshly built SPI/scanner and CDI fixture variants passed on the JVM and application-linked jlink, including OAuth, Commercetools REST/GraphQL and the official Firestore emulator (`benchmarks/target/runs/20261116-122224/summary.json`). This diagnostic run is not a performance comparison.
- The native-image regression tests cover JVM compatibility. This release run did not build or execute a native executable.

The complete selected test report is `target/release-2.6.0/validation-summary.json`. Testcontainers used the active Docker Desktop pipe for this invocation because the local user configuration referenced an obsolete pipe.

## Central publication attempt

The signed, immutable bundles are retained under `target/release-2.6.0/production` in DevSuite:

| Batch | Components | POM/JAR files | State |
|---|---:|---:|---|
| Versioner | 1 | 1 | Rejected by Central quota |
| BOMs and parents | 15 | 15 | Signed; not uploaded |
| Service wrappers | 75 | 300 | Signed; not uploaded |
| Runtime modules | 36 | 144 | Signed; not uploaded |

Versioner deployment `140215d1-60a8-4645-9d31-0ef348fbdb0d` reached `FAILED` with the response: "Publishing blocked: your organization has reached its monthly release size limit." The same response states that the grace period ended on `2026-11-13` and usage resets on `2026-12-01`. See the [GuicedEE Central usage page](https://central.sonatype.com/publishing/usage?org=guicedee).

The publisher stopped before submitting downstream batches. Public POM checks for Versioner, GuicedEE BOM, Kotlin and native-image remained HTTP 404 after the rejection. The immutable `manifest.json`, failed `versioner-deployment.json`, and `publication-blocked.json` preserve the bundles, hashes and response. No successful public-download verification is claimed.

Resume only once the organization's publishing allowance is available. Preserve the failed receipt when arranging a new submission of the same bundles; never duplicate a deployment that is still validating or publishing. Website deployment and IntelliJ Marketplace publication remain separate operations.

## Version alignment

GuicedEE Maven coordinates, website examples and badges, and IntelliJ plugin Maven/Gradle templates now target 2.6.0. The IntelliJ plugin version is 2.6.0; its platform build plugin remains unchanged. Historical release entries retain their original versions.

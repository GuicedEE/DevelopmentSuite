# GuicedEE 2.6.0

Status: version alignment complete; publication pending. These notes describe the modular SDK and benchmark changes prepared for 2.6.0; they do not announce Maven Central publication. Existing local validation used the development artifacts before the release version bump.

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

## Application-linked benchmarks

Dedicated Maven jlink modules build the complete application dependency graph with Oracle JDK 25 and generated module launchers. Application classes and dependencies are part of the linked image; automatic-module failures fail the build. The runtime does not include `jdk.jlink`.

The SPI and scanner variants use their generated module paths for JVM runs and their generated launchers for linked runs. Quarkus fast-JAR remains a JVM baseline. Historical JDK-only/classpath jlink measurements are invalid for application-module comparisons and must not be reused as evidence for this release.

## Validation and release boundary

- Firebase and Commercetools builds passed with API documentation enabled.
- Both application-linked benchmark variants built on Oracle JDK. Following dependency extraction, both passed fixture checks for OAuth, REST, GraphQL and Firestore (`benchmarks/results/20261114-195109/summary.json` in DevSuite).
- Binary inspection confirmed extracted libraries are absent from the Commercetools shade; the resolved graph contains no Jackson 2 core/databind.
- Native Zstd compression, Bouncy Castle loading, Java Money and application logging were checked locally.

These are packaging and capability checks, not a new performance claim. Before publication, build and verify the coordinated 2.6.0 artifacts and rerun the benchmark matrix against those exact artifacts. Publication and website deployment are separate release steps.

## Version alignment

GuicedEE Maven coordinates, website examples and badges, and IntelliJ plugin Maven/Gradle templates now target 2.6.0. The IntelliJ plugin version is 2.6.0; its platform build plugin remains unchanged. Historical release entries retain their original versions.

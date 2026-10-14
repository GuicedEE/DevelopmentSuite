# GuicedEE 2.4.0 release preparation

The release target is **2.4.0** for the versioner, BOMs, parent, runtime modules,
shaded services, examples, website, website backend and runtime image, and Guiced
IntelliJ plugin. JWebMP, ActivityMaster, EntityAssist and MCP consume GuicedEE
2.4.0 while keeping their own artifact versions.

The release includes the ClassGraph 6.0.0 migration for Java 25, explicit clustering
and WebSocket continuity, and aligned website and IntelliJ application generation.
Older release notes retain their original versions. The website badge, catalog,
Maven and Gradle downloads, documentation snippets and image tag use 2.4.0.

The final [BOM dependency refresh](bom/DEPENDENCY-REFRESH-2.4.0.md) advances
stable runtime and test libraries, including reviewed Spring and FastDoubleParser
major updates. Maven plugin versions remain unchanged.

## Local verification

From DevSuite, using Maven 4 and JDK 25:

```powershell
./verify-guicedee-release.ps1 -Stage Validate
./verify-guicedee-release.ps1 -Stage Package
./verify-guicedee-release.ps1 -Stage Test
python ./publish-guicedee-release.py audit
```

Package installs the 117 release components locally, including sources and
javadoc JARs, then checks the 102 binary JARs for versioned JPMS descriptors.
Before rebuilding at the same version, Package preserves prior JARs and generated
source trees under `target/release-2.4.0/previous-artifacts` to prevent shading
older dependency classes or sources back into the release.
Test reaches `verify` so shaded module descriptors exist before downstream tests.
Kubernetes tests require `-IncludeLiveInfrastructure`. Logs and audit manifests
are stored in `target/release-2.4.0`. No step runs Maven clean or deploys artifacts.

Rebuild the consumer parent/BOM chain, JWebMP generators, EntityAssist and
ActivityMaster against the locally installed platform before generating the
website. The Website stage disables Docker image creation:

```powershell
./verify-guicedee-release.ps1 -Stage Consumers
./verify-guicedee-release.ps1 -Stage Website
```

Inspect `GuicedEE/website/target/webroot/guicedee-website/dist/jwebmp/browser` for
fresh 2.4.0 output. The Java owners are the source of generated Angular content.

The IntelliJ plugin targets Java 21 bytecode for IDEA 2025.2+. Its Gradle 8.14.4
wrapper runs on JDK 21; wizard-generated applications target Java 25:

```powershell
$env:JAVA_HOME = 'C:/Software/jdk/21'
Push-Location GuicedEE/intellij-plugin
./gradlew.bat test buildPlugin --console=plain
Pop-Location
```

The plugin compatibility follow-up is version 2.4.1; generated applications still
use GuicedEE 2.4.0. Its installable ZIP is
`GuicedEE/intellij-plugin/build/distributions/GEEIntelliJPlugin-2.4.1.zip`.
Run `verifyPlugin` alongside `test buildPlugin` to check the 2025.2 baseline and
2026.3 EAP build 263.6800.24 before Marketplace upload.

## Verification recorded on 14 October 2026

- Release model validation passed with 117 components. The local artifact audit
  passed for 102 binary JARs, their sources and documentation, and 2.4.0 JPMS versions.
- The 71-module JWebMP, EntityAssist and ActivityMaster consumer reactor installed successfully.
- The GuicedEE website generated and built its production Angular bundle; the bundle
  contains 2.4.0 release content and Maven/Gradle BOM snippets.
- The website backend installed as 2.4.0 and its jlink configuration validated.
  JWebMP, EntityAssist and ActivityMaster websites and the query guide examples compile.
- Focused ClassGraph/class loading/Hibernate tests passed: 10. IntelliJ tests passed: 20.
  MCP protocol and transport tests passed: 9, with its runtime dependencies aligned by the BOM.
- Keycloak API documentation now participates in its build. The JNA platform bridge
  is included in the release profile and supplies sources, module documentation and
  a flattened publication POM.
- The final BOM refresh additionally passed 47 focused framework tests, including
  live SOAP, Kafka, IBM MQ and Hazelcast checks, plus nine MCP tests. Both major
  updates passed Java 25 runtime checks, and the rebuilt production site contains
  the final dependency highlights. Plugin version pins match their prior values.

The full test suite was not run, and the Linux backend runtime image was not built
in this preparation. IntelliJ Marketplace publication has not been run.

## Publication

Maven Central publication completed on 14 October 2026. All 117 components are
published, and all 423 public POM, binary, source and Javadoc files match the
prepared SHA-256 hashes.

| Phase | Components | Deployment ID |
| --- | ---: | --- |
| Versioner | 1 | `addd431b-5679-4bc9-97c6-0febfb4c4157` |
| BOMs and parent POMs | 14 | `31482af9-2570-41c2-9d02-006c541145ce` |
| Services | 69 | `380c9c34-83f2-4a05-b2e5-41ccd107c5c8` |
| Runtime modules | 33 | `b7710f12-c6db-40d4-bdc6-977a85b1e34c` |

Central reported that the Guicedee organization exceeded its monthly file-count
and release-size limits. This release completed, but further publication is
blocked until the usage resets on 1 November 2026. See the
[organization usage page](https://central.sonatype.com/publishing/usage?org=guicedee).

`publish-guicedee-release.py prepare` signs the installed
artifacts using the existing Maven Central credentials and GPG setup. `run`
publishes four resumable batches: versioner, BOMs, services and runtime modules.
The prepared bundles, deployment states and public-file verification reports
live under `target/release-2.4.0/production`. Existing deployment IDs are reused
when resuming; published coordinates must not be uploaded again.

Website image push and Azure deployment use `build-guicedee-website.ps1` and the
website README. IntelliJ Marketplace publication uses the verified plugin ZIP.

The website frontend was deployed on 14 October 2026 to Azure Container App
`guicedee-website` in `DevSites`, revision `v240-20261014`, with 100% traffic.
Image tag `gedmarc/guicedee-website:2.4.0-20261014` was pushed and deployed by digest
`sha256:8175c168eeaec132c6b427e6ea0275a947c28e8a463c05eee52d1e17cbdedcbb`.
Provisioning succeeded and the latest revision became ready. Public homepage,
`/releases`, `/modules` and the main JavaScript bundle match the local production
files, including the IntelliJ plugin section and APM installer commands.
The website backend was not redeployed.

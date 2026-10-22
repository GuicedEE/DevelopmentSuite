# GuicedEE 2.4.1

Maintenance release for Jackson 3 applications on Java 25.

## Fixes

- `CLOUD=true` startup and `GuiceContext.buildConsoleLayout(JSON)` use Log4j JSON
  Template Layout through the shared Client API. The legacy JsonLayout no longer
  requests Jackson 2 `FilterProvider` during static initialization.
- Console level filters accept INFO/DEBUG and WARN/ERROR messages in the intended
  ranges. Cloud output is compact newline-delimited JSON with exception details.
- The Hazelcast JPMS wrapper removes the unused embedded Jackson 2 implementation
  and its service descriptors and Maven metadata. Hazelcast's old embedded Jackson 3
  core/databind are replaced with shared managed Jackson 3.2.3; private Jackson Jr
  helpers are aligned to the same version.
- Website backend and jlink configurations no longer request Jackson 2 databind.
  Backend boot awaits asynchronous startup through `GuiceApplication.run`.

The shared Jackson annotations artifact remains necessary for Jackson 3. This release
is not a certification that every optional adapter is Jackson 2 free: Azure Identity,
Cassandra, CXF OpenAPI, CloudEvents and test tooling have remaining migration paths.
See [the Hazelcast migration inventory](services/JCache/hazelcast/README.md).

## Release surfaces

Versioner, BOMs, parent, runtime modules and shaded services use 2.4.1 together.
JWebMP, ActivityMaster and EntityAssist retain their own versions and consume the
updated GuicedEE parent/BOM. Examples, current documentation, website catalog,
Maven/Gradle application downloads and IntelliJ generation templates target 2.4.1.
Historical release notes retain their original versions. The IntelliJ plugin retains
its independent 2.4.1 version and updates its generated platform target.

## Verification and publication

Run from DevSuite with Maven 4 and JDK 25:

```powershell
./verify-guicedee-release.ps1 -Stage Validate
./verify-guicedee-release.ps1 -Stage Package
./verify-guicedee-release.ps1 -Stage Test
python ./publish-guicedee-release.py audit
./verify-guicedee-release.ps1 -Stage Consumers
./verify-guicedee-release.ps1 -Stage Website
```

Package preserves prior generated artifacts before rebuilding and installs the release
locally. The inventory contains 117 components and 102 binary named-module JARs,
including source and Javadoc attachments. No release command runs Maven clean.
The packaged Inject test starts a fresh JVM with `CLOUD=true` and Jackson 2 absent;
Hazelcast tests check embedded bytecode/metadata and two-member JSON replication.
Tests requiring the separately deployed Kubernetes demo need `-IncludeLiveInfrastructure`.

Publication uses signed immutable bundles and dependency-ordered Central batches:

```powershell
python ./publish-guicedee-release.py prepare
python ./publish-guicedee-release.py run
```

Artifacts, manifests, deployment IDs, verification results and build logs are retained
under `target/release-2.4.1`. Central downloads must match the prepared SHA-256 hashes.
Publication does not create Git commits or tags.

## Website deployment

Build the current frontend and the Java 25 Linux backend runtime image, push versioned
images, then update the `guicedee-website` and `website-backend` Container Apps in
subscription `aee3c089-49a8-472c-8d40-5bf568a1ff55`, resource group `DevSites`.
Use immutable image digests and retain the previous revision identities. Verify Azure
readiness, public SPA routes and downloaded HTML/JavaScript against local production
output. Verify backend readiness, service status and reported release version.

Deployment receipts and live byte comparisons belong under `target/release-2.4.1`.


## Recorded result - 16 October 2026

All four signed Central deployments are PUBLISHED: 117 components and 423 POM/JAR
files downloaded from Maven Central match the prepared SHA-256 manifest. Release
validation and all 102 named-module artifact checks passed. The selected release
suite passed 18 tests; MCP protocol/transport tests passed 6; IntelliJ tests passed
20, with both IC-252.28539.33 and IU-263.6800.24 reported Compatible. These are focused
checks, not a claim that every optional-infrastructure test ran.

Frontend revision `guicedee-website--v241-20261016` and backend revision
`website-backend--v241-20261016` serve 100% of traffic. Frontend verification matched
94 HTML/JavaScript/CSS assets and six routes. Backend readiness/liveness, service
APIs, Swagger/OpenAPI, CORS and internal-route authorization passed. The previous
backend revision is deactivated. Exact image digests and receipts are retained in
`target/release-2.4.1/deployment-images.json` and the live verification reports.

The optional ActivityMaster API URL is unset, so that registry entry remains DOWN;
the four public sites are healthy and backend readiness is UP. Telemetry defaults
to disabled for this backend until an operator configures a collector.

Central reports that the organization's publishing quotas have been reached, with
a grace period through 13 November 2026. This release published successfully;
account usage needs attention before that grace period expires.

No Git commits, reverts, tags or Maven clean were used. IntelliJ artifacts were built
and verified locally; Marketplace publication is a separate operation.

# GuicedEE 2.5.1

Dependency maintenance release covering 117 Maven Central components: BOMs, parent, runtime modules and JPMS wrappers. Includes the 2026-11-08 dependency refresh, updated third-party build plugins, corrected Hibernate BOM management and Azure Identity Javadoc support. Official Apache Maven plugin versions remain unchanged.

Release validation passed. Packaging and publication evidence is retained in target/release-2.5.1. The publisher audits 102 JPMS JARs, sources and Javadoc, signs four immutable bundles, then verifies published POM/JAR SHA-256 hashes against its manifest.

Pre-release dependency validation: 135 client tests passed; persistence ran 35 tests with zero failures/errors and one DB2 skip. Hibernate bytecode enhancement was independently verified. Release packaging skips tests; these earlier checks are separate evidence.

Website deployment is included in this release. Downstream consumer publication is separate.

## Publication result

All four Maven Central deployments are PUBLISHED. All 117 components and 423 downloaded POM/JAR files match the signed manifest SHA-256 hashes. Local audit verified 102 JPMS JARs and their source/Javadoc artifacts.

- Versioner: `79678e36-c1ca-465e-badc-235e763430b7`
- BOMs: `f545d46c-6804-4bd8-b2a9-930bcbe3ddb1`
- Services: `0bc8e5c6-723f-40d0-877a-17866ec74a9b`
- Runtime modules: `1957be97-760c-4db5-b9e2-5ac5f3102a49`

Receipts, signed bundles, manifest and remote verification records: `target/release-2.5.1/production`. Packaging passed; tests were skipped in release packaging. Earlier dependency-refresh tests are documented separately above.

Central reported monthly file-count, release-size and release-count limits, with publishing allowed through 13 November 2026 under a grace period. These warnings did not block this release.

Source version changes and this release record remain uncommitted. No Git tags, GitHub release or downstream consumer publication were performed.

## Website rollout

Website content and current examples updated to 2.5.1; historical release notes retained. Maven generation and explicit Angular production build passed. Production bundle content verified. Docker image pushed as `gedmarc/guicedee-website:2.5.1-20261108`, digest `sha256:fd69a28d78f1f5701f5a6a332608618f743d8fc06f5e8fed2fbe48ce61d3e6ea`.

Azure deployment and public asset verification are pending reauthentication: tenant conditional access expired the cached Azure CLI token (AADSTS70043). Backend deployment is not required for this static-site update.

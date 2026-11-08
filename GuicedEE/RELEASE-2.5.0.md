# GuicedEE 2.5.0

Release coordinates for the GuicedEE BOMs, parents, runtime modules and JPMS
service wrappers are prepared for Maven Central. Third-party dependency pins
are unchanged by this release-version update.

The release profiles contain 117 components. Both the root reactor validation
and `verify-guicedee-release.ps1 -Stage Validate` passed on 2 November 2026.
The verification script now reads Maven 4 subprojects and uses the pinned wrapper.

## Local packaging and publication

Run `./verify-guicedee-release.ps1 -Stage Package` to install release artifacts
locally, then `python ./publish-guicedee-release.py audit` to check the JARs,
sources, Javadoc and module versions. Packaging skips tests; it is not evidence
of test success. The publisher targets 2.5.0 and retains receipts under
`target/release-2.5.0/production`.

After successful artifact audit, `prepare` signs the release bundles and `run`
publishes them in dependency order.

## Publication result

All four Central deployments are PUBLISHED. All 117 components and 423 downloaded
POM/JAR files match the signed release manifest's SHA-256 hashes. The 102 JPMS
JARs passed the local module-version audit. Tests were skipped during packaging.

- Versioner: `95b91a6e-25a3-4350-90a8-3771e1474ace`
- BOMs: `73dd5b42-a2c3-43fb-a175-0616e7187def`
- Services: `0a808e8d-b162-4755-814b-a07567a0062b`
- Runtime modules: `cc174bfb-f516-499c-ba69-6819ae8ea458`

Receipts and per-file verification evidence are in
`target/release-2.5.0/production`. Central reported monthly file-count and
release-size limits exceeded, with a publishing grace period through
13 November 2026. This warning did not prevent this release.

Website deployment and IntelliJ Marketplace publication are separate operations.

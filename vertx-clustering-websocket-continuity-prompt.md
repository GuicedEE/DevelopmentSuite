# Continuity prompt: Vert.x clustering and WebSocket scaling

**Execution status: verified local completion, 4 October 2026.** All six definition-of-done
items are implemented and validated. The complete workflow passed at 21:38 SAST.
Clustering remains disabled by default; no owner service was restarted or deployed.
See the execution handoff at the end and `C:\Java\ne1-world\web\core\docs\clustering.md`.

## Task and operating constraint

Implement the full Vert.x clustering and WebSocket scaling work across
**GuicedEE, JWebMP, and NE1.World Core**, continuing the 4 October 2026 review.
This is an end-to-end implementation task: change the framework sources, install
the resulting libraries locally, integrate them into Core, regenerate affected
browser code, and complete regression and multi-replica validation. Preserve the
currently working operations throughout.

Do not finish with an audit, architecture proposal, roadmap, framework-only fix,
Core-only workaround, or configuration instructions for unimplemented behavior.
The owner explicitly requires the entire implementation. Complete all necessary
framework and Core changes in the same task, including callers, configuration,
tests, and documentation. Resolve routine implementation decisions autonomously
from the source and requirements; a design choice is not a reason to defer work.

The owner confirms that current operations work. Treat that behavior as the
baseline. The findings below concern scaling readiness; they do not establish
that the current single-instance deployment is broken. Revalidate each finding
against current source and effective artifacts before changing it.

This file is the execution prompt for that full implementation; its creation
alone did not implement or enable clustering. When using it, continue until the
definition of done below is satisfied. Local cross-repository edits, library
installation, generation, isolated service startup, and acceptance tests are
part of the implementation scope. Publication and production deployment are
separate from completing and proving the local implementation.

## Workspaces and rules

- Framework workspace: `C:\Java\DevSuite`.
- NE1 application workspace: `C:\Java\ne1-world`; Core is `web/core`.
- Read applicable `AGENTS.md` files and the `guicedee-vertx`,
  `guicedee-hazelcast`, `guicedee-websockets`, and `jwebmp-vertx` skills. Read
  Angular/generator skills when changing the Java-authored browser services.
- Do not git commit, revert, reset, clean, switch branches, publish, or deploy.
  Do not run Maven clean. Preserve unrelated dirty work and private settings.
- Shared libraries are local work only: compile, test, and install locally as
  necessary. Inspect dirty state in each affected child repository first.
- Implement shared behavior in its owning framework module and adopt it in Core.
  Do not bypass a framework defect with a Core-only duplicate implementation.
  Do not leave Core integration as a follow-up after fixing the framework.
- Do not stop/restart the owner's running services or alter private environment
  files to enable clustering without authorization. Use isolated test instances
  and ports, and shut down only resources created by the tests.
  Start and stop those isolated instances as needed to complete validation;
  documenting how someone else could test them does not satisfy this task.
- Core remains read-only for canonical ActivityMaster/FSDM application data.
  Send domain mutations to their authorized backend with verified actor context.
  Preserve finalized account-management authority; do not widen database grants,
  add locking reads, introduce owner credentials, or add local write fallbacks.
- Fix Java/JWebMP generator sources. Do not hand-edit generated Angular output.

## Evidence from the review

These are source/dependency findings, not proof from a running multi-node Core.

1. Core's resolved Maven dependency tree used Vert.x/STOMP **5.2.0** and had no
   GuicedEE Hazelcast or Vert.x Hazelcast cluster manager. No Core cluster
   configurator was found. The reviewed runtime therefore builds a local bus.
2. `VertXPreStartup` enables `buildClustered()` when a discovered
   `VertxConfigurator` implements `ClusterVertxConfigurator`.
3. Startup runs in ascending priority. Vert.x starts at `Integer.MIN_VALUE + 38`;
   `HazelcastPreStartup` runs at `Integer.MIN_VALUE + 70`. The latter prepares
   annotated configuration using `VertXPreStartup.getVertx().executeBlocking()`.
   The cluster configurator can consequently select a default manager before
   the intended Hazelcast configuration or embedded member exists. Moving one
   priority alone would leave the dependency on an already-started Vert.x.
4. Shutdown also runs in ascending priority. `HazelcastPreDestroy` runs at
   `Integer.MAX_VALUE - 100`, while Vert.x shuts down at `Integer.MAX_VALUE`.
   When an embedded member is reused by Vert.x, this closes the member first.
5. `AngularTSSiteBinder` uses a STOMP `BridgeOptions` without point-to-point mode
   and registers a regular consumer on `/toBus/incoming`. The resolved Vert.x
   STOMP bridge defaults to publish semantics for incoming frames. Every Core
   replica in the same event-bus cluster would process a command on that address.
6. JWebMP's bound `IGuicedWebSocket` implementation publishes to
   `/toStomp/<group>`. The STOMP bridge uses normal event-bus consumers for these
   subscriptions, providing a route to sockets on other nodes once clustered.
7. Raw GuicedEE `GuicedWebSocket.broadcastMessage()` falls back to writing to a
   JVM-local socket list. Group event-bus listeners exist, but this fallback
   broadcast does not use them. Do not assume it shares JWebMP STOMP semantics.
8. Core `LobbySubscriptions` issues opaque tickets into a local `HashMap`.
   Redemption removes the ticket and checks the STOMP session, destination,
   expiry, subscription ID, and other constraints. HTTP issuance on replica A
   followed by subscription on replica B fails closed because B lacks the ticket.
9. Lobby active subscriptions hold local connection objects and use
   `localConsumer`. A one-second timer triggers authorized snapshot reads and
   revision reconciliation. Keeping socket objects and local reconciliation on
   their owner is appropriate; sharing those objects is not a solution.
10. Browser `EventBusService` already implements reconnect backoff, listener
    restoration, new STOMP session IDs, and clearing secure capabilities on
    reconnect. Lobby code obtains fresh capabilities and renews leases.

The installed source JARs for GuicedEE `vertx:2.3.0` and JWebMP
`angular:2.0.3-SNAPSHOT` matched the reviewed checkout files after newline
normalization. Verify effective dependencies again; that observation can drift.

## Source entry points

Relative to DevSuite:

- `GuicedEE/vertx/src/main/java/com/guicedee/vertx/spi/VertXPreStartup.java`
- `GuicedEE/vertx/src/main/java/com/guicedee/vertx/spi/ClusterVertxConfigurator.java`
- `GuicedEE/vertx/src/main/java/com/guicedee/vertx/spi/EventBusOptions.java`
- `GuicedEE/hazelcast/src/main/java/com/guicedee/guicedhazelcast/services/HazelcastPreStartup.java`
- `GuicedEE/hazelcast/src/main/java/com/guicedee/guicedhazelcast/implementations/HazelcastClusterConfigurator.java`
- `GuicedEE/hazelcast/src/main/java/com/guicedee/guicedhazelcast/implementations/HazelcastPreDestroy.java`
- `GuicedEE/inject/src/main/java/com/guicedee/guicedinjection/GuiceContext.java`
- `GuicedEE/websockets/src/main/java/com/guicedee/vertx/websockets/GuicedWebSocket.java`
- `GuicedEE/websockets/src/main/java/com/guicedee/vertx/websockets/VertxSocketHttpWebSocketConfigurator.java`
- `JWebMP/vertx/src/main/java/com/jwebmp/vertx/implementations/VertXStompEventBusBridgeIWebSocket.java`
- `JWebMP/plugins/angular/src/main/java/com/jwebmp/core/base/angular/implementations/AngularTSSiteBinder.java`
- `JWebMP/plugins/angular/src/main/java/com/jwebmp/core/base/angular/implementations/StompEventBusPublisher.java`
- `JWebMP/plugins/tsclient/src/main/java/com/jwebmp/core/base/angular/client/services/EventBusService.java`

Relative to NE1:

- `web/core/pom.xml`, `web/core/src/main/java/module-info.java`
- `web/core/src/main/java/world/ne1/core/auth/lobby/LobbySubscriptions.java`
- `web/core/src/main/java/world/ne1/core/auth/lobby/LobbyBridgePolicy.java`
- `web/core/src/main/java/world/ne1/core/auth/lobby/LobbyRuntime.java`
- `web/core/src/test/java/world/ne1/core/test/LobbyBridgePolicyTest.java`
- `modules/lobby-boundary/lobby-web/src/main/java/world/ne1/lobby/web/LobbyService.java`

## Implementation sequence

### 1. Establish the working baseline

Capture effective dependencies, JPMS/service registrations, lifecycle order,
existing focused test results, and the current command/response/broadcast routes.
Trace actual callers before changing semantics. Identify any connection-local
authentication, context, or state that must remain on the accepting node.

Check single-instance operation with clustering absent or disabled. Preserve
current routes, wire formats, action dispatch, broadcast recipients, storage
updates, authorization, reconnect behavior, and Core's backend boundaries.

### 2. Correct cluster configuration and lifecycle

Prepare Hazelcast configuration before clustered Vert.x is built, without
requiring that Vert.x already exist. Establish one clear owner for each member
and runtime. Avoid starting a second member accidentally. Shut down Vert.x before
any externally owned/reused Hazelcast member; await asynchronous cleanup.

Implement explicit cluster activation in the framework and wire it into Core's
dependencies, JPMS graph, bootstrap/configuration, and runtime packaging as
necessary. Supply working checked-in configuration examples for both clustered
and single-instance operation. Keep the current single-instance configuration
working without requiring cluster discovery or reachable peer members.
Do not activate clustering across unrelated applications through an incidental
transitive dependency. Preserve intentional existing clustered use cases.

Verify option composition and annotation/environment handling. Configure the
cluster name, discovery mechanism, bind addresses, advertised addresses, and
ports for both Hazelcast membership and Vert.x TCP event-bus transport. Membership
alone does not establish message connectivity. Document service/environment
isolation and the actual settings used; do not rely on default multicast or `dev`.
Update both JPMS and classpath SPI registrations where applicable.
Integrate cluster readiness/failure reporting with the existing health/lifecycle
mechanisms so Core does not advertise an intentionally clustered runtime as ready
before its required initialization has completed. Prove the assembled Core uses
the locally updated artifacts and intended settings, rather than a stale JAR or
an independently configured test-only runtime.

### 3. Separate command routing from notification broadcasts

Ensure a browser command reaches one intended handler. Choose owner-local or
explicitly addressed ingress if it depends on connection-local context; use
point-to-point distribution only where processing on another replica is valid.
Keep notification broadcasts able to reach all authorized subscribers across
replicas. Do not globally toggle STOMP point-to-point mode without proving its
effect on outbound subscriptions, replies, and multiple tabs/clients.

Namespace shared addresses where necessary to prevent unrelated service roles
from consuming each other's commands. Preserve per-message authorization and
recipient isolation; cluster membership is not user authority.

Implement cluster-capable raw GuicedEE group broadcasting and necessary cleanup
in the owning framework. Preserve one delivery per local recipient and verify
membership removal, closed sockets, and consumer cleanup. Revalidate the finding
first; if it has already been corrected, prove the existing implementation passes
the same cross-node checks instead of introducing redundant changes.

### 4. Make lobby ticket issuance and redemption work across replicas

Choose a concrete design: route issuance to the socket owner, or use shared
atomic single-use ticket redemption. Shared ephemeral state must preserve the
verified actor, destination, STOMP session binding, short expiry, revocation,
capacity limits, and replay rejection. Signing a ticket alone does not enforce
single use. Do not share live socket objects or trust a client-supplied owner or
actor without verification.

Complete operation with HTTP requests and WebSockets accepted by different Core
replicas. Affinity is an optional optimization, not the acceptance solution.
Implement owner routing or shared atomic redemption so a request received on A
and the intended socket on B work without load-balancer stickiness. Keep snapshot
reads authorized on the owning backend and obtain a fresh capability after
reconnect. Implement any needed browser, route, service, and framework contracts
together; do not defer secure ticket handling to another phase.

### 5. Verify recovery and resource limits

Keep reconnect restoration and authoritative snapshot refresh. Vert.x event-bus
delivery is best effort; avoid claims of durable replay or exactly-once delivery.
Prevent application retries from duplicating effects where applicable.

Measure lobby polling and backend pressure: the reviewed implementation has eight
concurrent reads, 128 active subscriptions, 256 combined tickets/active entries,
and four combined entries per actor per replica. Establish which limits are per
node and which must be global. Bound queues, writes, frame sizes, subscriptions,
and stale consumers where the actual implementation requires it. Do not remove
polling or revalidation without an equally reliable recovery path.

## Acceptance checks

Implement and run repeatable isolated tests with at least two real clustered
Vert.x instances and STOMP connections, plus a separate-JVM acceptance harness
that exercises Core's assembled routing, ticket issuance, and socket integration.
Use isolated ports and safe fixtures; preserve the owner's data and services.
Exercise advertised addresses over real TCP connections. An in-process test alone
does not satisfy the full local scaling acceptance requirement. The local harness
does not establish acceptance on an untested cloud or production network.

- Existing unclustered command, response, broadcast, and reconnect journeys pass.
- One browser command invokes one intended handler with two replicas present.
- An outbound broadcast reaches subscribers on both replicas once per recipient.
- Private destinations and actor/realm boundaries remain enforced across replicas.
- A ticket issued on A can be redeemed by the intended socket on B under the
  chosen design; wrong-session, wrong-destination, expired, and replayed tickets
  are rejected. Race concurrent redemptions and require at most one success.
- Terminating A drops A's sockets; clients reconnect to B, obtain new capabilities,
  restore subscriptions, and refresh authoritative state. Stale capabilities fail.
- Disconnect/unsubscribe, startup failure, cluster join failure, and shutdown
  release owned resources without duplicate members or hanging test processes.
- Slow clients, backend saturation, and temporary cluster disconnection remain
  bounded and recover through the documented refresh/retry behavior.
- Regenerated Angular passes relevant build/generation checks; validate browser
  reconnect, secure subscription renewal, and snapshot refresh in an isolated
  browser fixture against the updated implementation.

Before enabling clustering for a running Core, require the two-replica checks and
the single-instance regression checks to pass. Keep missing live/network/browser
proof visible rather than treating compilation as acceptance.

## Definition of done: full implementation

All of the following are required:

1. **GuicedEE:** cluster activation/configuration and lifecycle ownership are
   implemented correctly; raw WebSocket groups deliver across nodes with bounded
   resources and correct cleanup. Relevant framework regression tests pass.
2. **JWebMP:** STOMP commands execute once on the intended handler; outbound
   broadcasts still reach all intended subscribers; response routing, connection
   context, and reconnect behavior work in clustered and unclustered modes.
   Java-authored browser changes are regenerated and validated.
3. **NE1 Core:** effective dependencies, module/service registrations, runtime
   configuration, health integration, and cross-replica lobby capability handling
   adopt the corrected framework. Core works with clustering disabled and enabled.
4. **Local integration:** changed shared artifacts are built/tested/installed in
   dependency order, and Core's effective artifacts and packaged runtime are
   checked to consume those exact local changes. Existing public/private routes,
   shell operations, and authentication/backend boundaries pass relevant regression
   checks against the assembled application.
5. **Scaling acceptance:** the repeatable separate-JVM Core harness passes command
   routing, cross-node broadcast, cross-replica ticket redemption, rejection/race
   checks, replica loss, reconnect, resource bounds, and shutdown checks. Browser
   acceptance covers the affected user journeys.
6. **Documentation:** checked-in examples and runnable test commands describe the
   implemented activation, addressing/discovery, isolation, resource ownership,
   and failure/recovery behavior. No core feature remains a TODO or follow-up.

Continue through failures and resolve defects within the authorized scope. If an
external prerequisite actually prevents a required check, make every remaining
independent implementation change, record the exact blocker and runnable command,
and clearly mark the implementation/acceptance incomplete. Do not relabel a
blocked test, missing Core adoption, or an unimplemented framework change as done.

## Previous validation and final handoff

On 4 October 2026, these commands succeeded from `C:\Java\ne1-world`:

```powershell
mvn.cmd -f web/core/pom.xml dependency:tree '-Dincludes=com.guicedee:hazelcast,io.vertx:vertx-hazelcast,com.guicedee.modules.services:vertx-hazelcast,io.vertx:vertx-core,io.vertx:vertx-stomp,com.guicedee:websockets' '-Dverbose' '-Dstyle.color=never'
mvn.cmd -f web/core/pom.xml '-Dtest=LobbyBridgePolicyTest,LobbyRuntimeTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstyle.color=never' test
```

The focused run passed 11 `LobbyBridgePolicyTest` and four `LobbyRuntimeTest`
tests. They use a single Vert.x instance and do not establish cluster readiness.
No clustering fixes were implemented in that review.

For the final implementation handoff, report completed framework and Core changes,
changed repositories/files, the implemented command and ticket routing design,
exact activation settings, preservation of single-instance behavior, tests actually
run, and results for every definition-of-done item. Keep any untested hosting
assumptions or real blockers explicit. Update this prompt's status to reflect
verified completion and outstanding work. Do not commit or publish the changes.

Primary references (check the resolved Vert.x version when implementing):

- [Vert.x Hazelcast clustering](https://vertx.io/docs/vertx-hazelcast/java/)
- [Vert.x STOMP bridge](https://vertx.io/docs/vertx-stomp/java/)
- [Vert.x event-bus delivery and routing](https://vertx.io/docs/apidocs/io/vertx/core/eventbus/EventBus.html)

## Execution handoff: 4 October 2026

| Definition-of-done item | Verified result |
| --- | --- |
| 1. GuicedEE | Complete: explicit cluster activation, configuration before Vert.x, composed options/metrics, owned member/cache reuse, startup rollback/readiness, ordered shutdown; cross-node raw groups and bounded socket cleanup |
| 2. JWebMP | Complete: browser commands request an owner-local consumer; notifications retain publish semantics; private replies/storage write only their originating connection; bounded STOMP writes/offline queue; Java-generated services rebuilt and exercised |
| 3. NE1 Core | Complete: dependency/JPMS/classpath SPI adoption, disabled-by-default configuration, quorum readiness and atomic shared single-use lobby leases; live sockets and authorization stay on their accepting node; canonical data remains read-only |
| 4. Local integration | Complete: nine updated framework JARs installed in dependency order and SHA-256 matched against Core's tested module path; assembled public/private, shell, authentication, App Place and lobby regressions pass; packaged Core JAR exercised in separate JVMs |
| 5. Scaling acceptance | Complete: three real member JVMs over explicit TCP; one command owner, multi-node/tab broadcasts, HTTP A/socket B capabilities, concurrent redemption, replay/binding/expiry rejection, replica loss, quorum loss/restoration, backend saturation, startup failures, actual stalled STOMP/raw peers and owned shutdown; Chromium reconnects A to B with HTTP on C and obtains a fresh capability/revision |
| 6. Documentation | Complete: implementation, changed source owners/files, activation/topology examples, resource limits, failure semantics and runnable workflow are in Core `docs/clustering.md` and `docs/clustering-examples/`; no required local feature or acceptance check remains deferred |

Final successful command, from DevSuite:

```powershell
.\verify-vertx-clustering.ps1
```

Actual final results (zero failures/errors/skips):

- Framework Java tests: 312 — inject 7, Vert.x 66, web 8, metrics 9,
  Hazelcast 3, TypeScript client 215 and Angular 4. The raw WebSocket library
  builds/installs successfully and is covered by the real socket acceptance harness.
- Java generation reruns: one EventBus rendering test and two lobby rendering tests.
  Generated Angular TypeScript passes typechecking and browser bundling.
- Five executable transport reconnect regressions pass.
- Core: 100 selected regressions, including six separate-JVM/browser acceptance
  checks; those six checks also pass with Core's packaged production JAR.
- Two isolated Chromium contexts prove connection loss/reconnect, fresh sessions
  and capabilities, subscription restoration, authoritative revision 2, one broadcast
  per recipient, private storage (including a guessed-destination subscriber),
  owner-local commands and cleanup.
- Nine effective framework artifacts match their built JARs. The workflow records
  exact paths/hashes in Core `target/clustering-artifacts.json` and the dependency
  tree in DevSuite `target/clustering-verification/effective-dependencies.log`.

Commands and ticket design: `/toBus/incoming` uses a local-only request after
policy checks, retaining the accepting node's context. No global STOMP
point-to-point switch is used. A Hazelcast entry processor atomically consumes
the shared ticket bound to its actual server-generated STOMP session and
destination; it carries verified identity, expiry and owner-local backend
revalidation. Live connections are never shared. Capabilities remain ephemeral;
membership-based split-brain protection has a failure-detection window and is not
CP consensus, durable replay or exactly-once domain processing.

Activation is explicit: `VERTX_CLUSTER_ENABLED=true`,
`NE1_CORE_CLUSTER_DATA_MEMBERS=3`, a distinct `HAZELCAST_CLUSTER_NAME`,
`HAZELCAST_JOIN_TYPE=TCP`, and explicit peer/bind/advertised addresses. The three
local examples use member ports 15701–15703 and event-bus ports 15801–15803.
Both transports must be reachable. Single-instance mode needs no peer discovery;
the checked-in single-instance example sets `VERTX_CLUSTER_ENABLED=false`.
No private environment file was changed or activated.

Changed repositories are GuicedEE inject, vertx, web, metrics, hazelcast,
websockets and services (`JCache/hazelcast`), JWebMP angular and tsclient, NE1
Core, and this DevSuite prompt/workflow. Core `docs/clustering.md` lists the
individual source owners/files. The existing lobby-web generator produces its
browser fixture output without a source change.

Evidence: DevSuite `target/clustering-workflow.log` and
`target/clustering-verification/`; Core `target/clustering-runtime/surefire-reports`,
`target/clustering-browser.log`, `target/cluster-browser/browser-transcript.log`
and `target/core-cluster-*.log`. The harness scrubs child environments and uses
owned temporary directories/ports, safe verified-proof/backend fixtures and the
production framework/Core code. Only owned processes/resources are stopped.

Untested outside this local scope: production/cloud networking, live Keycloak/DPoP
cryptography, live ActivityMaster authorization/database integration and the whole
platform reactor. No Git commit/revert/reset/clean, Maven clean, branch switch,
publication or deployment was performed; unrelated edits and owner services remain
preserved.

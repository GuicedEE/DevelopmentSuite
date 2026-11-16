# Current performance reference

The standalone [benchmark repository](https://github.com/GuicedEE/benchmarks) contains
reproducible workloads, the latest raw measurements and generated comparison grids.
Use [current results and graphs](benchmarks/RESULTS.md) for the SDK JVM, jlink, native,
clean-build and PostgreSQL persistence comparisons. The original service template
was unavailable; these are independent reference workloads, not a rerun of that service.

The PostgreSQL cohort measured on **16 November 2026** uses three fresh starts per
application, 150 measured joined reads and CRUD cycles per start, and 20 warmups.
All **126 capability checks** passed. Apps use 0.25 CPU / 500 MiB; PostgreSQL runs
separately with durable commits. Startup ends after the first committed 15-entity graph.

| Application | Startup median (s) | CRUD cycle p50 (ms) | Peak container memory (MiB) |
|---|---:|---:|---:|
| GuicedEE reactive stateless | 14.730 | 81.47 | 229.38 |
| Quarkus ORM / Narayana | 11.809 | 77.50 | 221.35 |
| Micronaut ORM / local transactions | 15.827 | 95.91 | 241.60 |

GuicedEE's startup gap to Quarkus is **2.92 s** in this cohort. CRUD-cycle p50 is
**5.1% higher**; the sample does not establish a CRUD improvement. The report retains
all starts, ranges, percentages, exact images and methodology. The separate
[instrumented profile](benchmarks/docs/PERSISTENCE_PROFILE.md) identifies Hibernate
model registration and mapping/query-engine initialization as the main remaining costs;
its phase timings are excluded from the performance rankings.

The report below is historical evidence from **13 November 2026**. Its measurements,
conclusions and references describe the original evaluation, not the current suite.

---

GuicedEE vs Quarkus: evaluation report
Date: 2026-11-13. Subject: a production-style Kotlin webhook service (the commercetools "AI Hub" shipping extension in ../Q_v_H/quarkus), ported to GuicedEE 2.5.1 and measured against the Quarkus original on Rancher Desktop Kubernetes. Primary criterion (set by you): limited-resource Kubernetes, fastest runtime, fastest scale-out. Main tier: 250m CPU / 500Mi, requests = limits.

Status: final. The evaluation is closed at your request. Open items are listed in §13 and were not run.

Evidence lives in measurements/MEASUREMENTS.md (+ results/), guicedEE/LEDGER.md, guicedEE/FRICTION.md, guicedEE/DEVIATIONS.md, research/, PLAN.md (claims in §7). Numbers below are from those files; I spot-checked the startup table against raw tables but did not re-run any measurement.

1. Verdict
For your stated criterion, GuicedEE does not beat Quarkus, and it loses clearly to Quarkus native. On this service at 250m/500Mi:

Untuned, GuicedEE needs about 34 s to Ready versus 14 s for Quarkus JVM and about 1-2 s for Quarkus native.
Its first business request fails with a gateway timeout in 38 of 40 runs (Quarkus JVM: 1 of 20), and its first business 200 arrives at about 67 s (Quarkus JVM 42 s, native 1.1 s).
It uses more memory than Quarkus JVM (about 416 MiB idle against 313; 465-487 MiB under load against 381), leaving 13-35 MiB below the OOM limit.
Scale-out 1 to 5: all new pods serving after 34-35 s (Quarkus JVM 22 s, native 1.9 s).
What GuicedEE does give: a much faster build (2.2 s warm clean, 1.2 s incremental, against about 9 s), a smaller dependency graph (115 jars against 322), JPMS and no build-time code generation, and warm steady-state throughput within 20% of Quarkus JVM. None of these is on your primary criterion.

Caveat that matters: I wrote this port, not its author. The author could tune it better. I applied the same JVM flag to both frameworks, but I did not hunt for GuicedEE-specific startup tuning beyond that. See §9.

2. Correction to an earlier interim summary
While the run was in progress I compared tuned GuicedEE (-XX:TieredStopAtLevel=1) with untuned Quarkus JVM and said GuicedEE reached the first business response sooner. That comparison was unfair and is wrong. Untuned against untuned, GuicedEE is slower (67 s against 42 s). Tuned against tuned, Quarkus is ready in 6.3 s against GuicedEE's 13.2 s. Ignore the earlier interim figures.

3. Startup and scale-out at 250m/500Mi (n=10 per variant, reverse-order pass n=5 agrees within 3%)
Variant	Ready (k8s)	/ready probe	First business request	First request failed	First business 200	CPU-s to Ready	Idle memory after first request
Quarkus JVM	14.3 s	14.0 s	28.5 s	1 of 20 (504)	42.4 s	3.5	313 MiB
Quarkus JVM, TieredStopAtLevel=1	6.3 s	5.9 s	12.4 s	0	18.4 s	1.4	275 MiB
Quarkus native	2.0 s	1.1 s	25 ms	0	1.1 s	0.04	54 MiB
GuicedEE (module path)	34.6 s	34.4 s	29.7 s	20 of 20	67 s	9.0-9.5	416-419 MiB
GuicedEE jlink	32.1 s	31.9 s	33.0 s	18 of 20	68 s	6-9	408-425 MiB
GuicedEE, TieredStopAtLevel=1	13.2 s	13.0 s	11.8 s	0	24.8 s	3.2	382 MiB
GuicedEE jlink, TieredStopAtLevel=1	13.6 s	12.7 s	12.3 s	0	24.9 s	3.2	412 MiB
The agent's reading, which I agree with: at 0.25 core, startup is a CPU-budget problem. JIT compilation competes with class loading and the first request. Untuned GuicedEE spends about 9 CPU-seconds before Ready, which is roughly 36 s of wall time at 250m. The first-request cost (lazy commercetools SDK build, Guice AOP, ClassGraph scan work) is paid at runtime. Quarkus does more of this at build time.

Scale-out (manual kubectl scale, single node, local images, no pull):

Variant	1 to 5: all new pods serving	1 to 3 under 45 users/s: failures during transition	After transition
Quarkus JVM	22.1 s	42.6%	recovers fully
Quarkus native	1.9 s	0%	0%
GuicedEE	34.0 s	33.8%	15.9% still failing, new pods crash-loop (3 restarts each)
GuicedEE jlink	35.1 s	40.0%	14.8% still failing, 3 restarts each
Cold start under load is the important hazard. If traffic reaches a JVM pod at the moment it becomes Ready (126 req/s, 5 minutes), both JVM stacks fail 100% of requests for the whole run with 3-5 OOMKills; native has 0 errors. A JVM replica at this tier needs a warm-up gate before it takes traffic. Quarkus JVM and GuicedEE both need it; GuicedEE is more fragile because it has less memory headroom.

4. Warm steady state
Warm at 126 req/s offered (n=3): all four variants run with 0 errors and p50 1.4-1.5 ms. CPU is Quarkus JVM 135 mC, GuicedEE 130-131 mC, native 147 mC. Memory is Quarkus JVM 383 MiB, GuicedEE 480, jlink 465, native 35. GuicedEE shows 100-500 ms p99 spikes (Quarkus JVM worst window 221 ms, native 4 ms).
Ramp knee (warm, one run each): Quarkus JVM held 560 req/s with no errors. GuicedEE and jlink held 448 req/s (80%) and failed at 560. Native degraded at 224-280 req/s. A JIT-compiled JVM beats the native image on peak throughput at 250m. Every variant ends in OOMKill or crash at overload.
TieredStopAtLevel=1 halves startup time and cuts peak throughput by about 55% for both frameworks (knee 224 req/s).
Throughput per millicore is within about 10% across all four. Throughput per MiB favours native by about 11-14x.
Per-resource efficiency: at your tier, native Quarkus is the only variant that is cheap in both CPU and memory. For many small pods, memory is likely your binding resource.

5. Build time and size
Scenario (median, n=5, isolated Gradle home)	GuicedEE installDist	Quarkus quarkusBuild -x test
Cold	9.1 s	18.8 s
Warm clean	2.2 s	9.1 s
Incremental, one-line Kotlin change	1.2 s	9.0 s
The "sub-second compile" claim is not reproduced (1.2 s incremental, 1.6 s in the earlier spike), but GuicedEE is about 7x faster on incremental builds. Quarkus augmentation costs 4.7-6.7 s on every build for this service. The "builds up to a minute" claim was not reproduced either: Quarkus JVM was 9-19 s. Quarkus native took about 98 s on macOS.
GuicedEE clean build with its 72 unit tests: 6.1 s. Quarkus tests are 6,018 lines against 790 for GuicedEE, so the test trees are not equivalent.
Main code: Quarkus 3,229 Kotlin lines, GuicedEE 3,497. Runtime dependencies: 322 jars (126.5 MB) against 115 jars (67.7 MB).
Images: Quarkus JVM 488 MB, Quarkus native 210 MB, GuicedEE module path 401 MB, GuicedEE jlink 225 MB (partial jlink: the commercetools SDK and Swagger UI stay on the classpath). AppCDS did not work on the module path.
6. Claim-by-claim (PLAN.md §7)
Verdicts: Supported / Partly / Not supported / Unverifiable. "Measured" means from this evaluation; "inferred" means reasoning from research.

#	Claim (from the framework author's message)	Verdict	Evidence
1	Quarkus build takes about a minute; the same result without Quarkus takes under 3 s	Partly. Quarkus JVM was 18.8 s cold, 9.1 s warm, 9.0 s incremental, not a minute. Native took about 98 s on macOS. GuicedEE: 9.1 s cold, 2.2 s warm clean, 1.2 s incremental. So "under 3 s" holds for warm GuicedEE builds. The Quarkus build-step chain itself is only 2.2-2.8 s; the rest is Gradle, Kotlin and packaging (measured)	§5
2	Quarkus is "merely a wrapper around Vert.x" doing "mass magic"	Partly. Quarkus does generate beans and handlers at build time, so it is more than a wrapper. But GuicedEE also does discovery work (ClassGraph, ServiceLoader, Guice AOP bytecode), at runtime instead. "No magic" is inaccurate for GuicedEE (research)	research/positioning.md
3	Writing directly on Vert.x gives more performant code than Quarkus	Not supported on this service. Warm knee: Quarkus JVM 560 req/s against GuicedEE 448 (80%). Per-millicore cost is about equal. GuicedEE runs everything on one event loop and has 100-500 ms p99 spikes (measured)	§4
4	GuicedEE keeps all Quarkus benefits with the same comfort	Partly. Capability checklist 30 pass, 0 fail, 2 N/A of 32, but about 520 custom and 1,150 glue lines, REST module reaches 133 of 262 on the acceptance gate, no native image, no build-time validation, Kotlin needs workarounds (§7, §8, §9)	§7-§9
5	Compile times are sub-second	Not supported. 1.2 s incremental with the real service (1.6 s in the earlier spike). Much faster than Quarkus (about 9 s), but not sub-second (measured)	§5
6	Startup on the JRT filesystem (which Quarkus does not support) beats Quarkus	Not supported at the 250m tier. Untuned GuicedEE jlink: 32 s to Ready against 14 s (Quarkus JVM) and 2 s (native). "JRT" is a URLStreamHandler that lets ClassGraph scan jlink images; it is not a startup optimisation. Hello-world numbers (543 ms jlink first response on a free laptop) did not carry over (measured)	§3
7	Coding methods are identical to Quarkus	Not supported. Only Guice's @Inject works, not Jakarta's; allopen is mandatory for Guice AOP; Jackson 3 Kotlin module needs explicit registration; module-info.java is required; REST module has no 415/406 and different error shapes; fault-tolerance and metrics needed glue (research/spike-results.md, FRICTION.md)	§7-§9
8	Support goes to the underlying engine anyway, so the wrapper adds nothing	Not testable directly; the opposite risk is the real one. GuicedEE has a single maintainer and 0-6 stars per repo. Quarkus has a large vendor-backed community. The wrapper does add something (DI, REST, config, OpenAPI), but its support depends on one person (research/maturity.md; inferred)	§10, §11
9	In use at several South African financial organisations	Unverifiable. No public evidence found; not speculated	research/maturity.md
10	Quarkus is a "dragon one" whose workarounds hide where the ecosystem is going	Mostly not supported for this service. The baseline has 15 build.gradle.kts workarounds. Most are CVE or dependency pins and native-image accommodations for libraries; only a few are Quarkus-caused. allopen is needed on both frameworks. I did not test which pins would also apply to GuicedEE	measurements/baseline, research/quarkus-inventory.md
11	Build-time against runtime work trade-off (added by you)	Evidence favours build-time work for your criterion. GuicedEE saves about 7 s per build, but pays about 20 s extra to Ready and about 25 s extra to first 200 at every pod start, plus first-request timeouts in 38 of 40 untuned runs. Fail-fast: Quarkus catches wiring errors at build; GuicedEE at startup. Native image is only possible with build-time work. The developer wins every build; the fleet loses every start. For a scaling fleet at 250m the second cost dominates	§3, §5
Where the claims are fair, and where they are marketing
Fair: GuicedEE's build is genuinely much faster. Its dependency graph is smaller. JPMS-first packaging is real.
Marketing: "sub-second", "faster JRT startup", "more performant than Quarkus", "identical coding methods", and production-use statements that cannot be checked. A single-maintainer framework presented as a drop-in for a vendor-backed one.
Quarkus claims that held up: build-time processing does buy startup and memory at small CPU limits. Quarkus's builds are slower, but not by "a minute" here.
What Quarkus provides that GuicedEE does not, and the reverse
Quarkus only: native image, build-time validation and augmentation, a large extension ecosystem (including a GraphQL client, fault tolerance with metrics, OIDC, OpenAPI, OpenTelemetry out of the box), vendor backing and documented upgrade paths, mature Kotlin support.
GuicedEE only (or better): fast incremental builds, JPMS-first design, runtime ServiceLoader modularity, a smaller dependency set, no code generation to reason about.
Risks of adopting GuicedEE (research and inference)
Bus factor: one maintainer; 0-6 stars per repo; no public adopters verified.
Security patch cadence: unproven; the dependency set (Vert.x 5, Jackson 3, Guice 7) is new, and you own the integration.
Hiring and onboarding: almost no outside knowledge base; Quarkus skills are common.
Licensing: Examples, Parent, Workflows and Packages repos are GPL-3.0 (modules Apache-2.0); copying from the examples would be a licence risk.
Operational: SIGTERM drops in-flight requests; single event loop; blocking retry uses Thread.sleep; fault-tolerance has no ft_* metrics; logging prints each line twice and defaults to DEBUG.
Bias declaration
The framework's author supplied the claims, so they come from an interested party.
The Quarkus baseline here is Kotlin and extension-heavy, which may inflate its build time.
I wrote the GuicedEE port, so a more experienced GuicedEE developer might tune it better; conversely, I know the baseline's internals well.
I am an AI model working from the project files and subagent reports; I did not independently re-run the measurements.
7. Free-versus-custom ledger (guicedEE/LEDGER.md)
Port totals: about 2,000 lines are copied or translated from the baseline's business logic, about 1,150 lines are glue, about 520 lines are custom.
Came free: HTTP server, DI, JSON, config, health endpoint, server and client tracing spans, Dropwizard metrics basics, OpenAPI generation from code (needs about 250 lines of swagger-core annotations to match the baseline), retry and fault-tolerance annotations.
Glue: about 330 lines for a labelled metrics registry and fault-tolerance series; about 40 lines of outer-bean restructure so the fallback works.
Custom: an 85-line rolling circuit breaker (the module's breaker is cumulative, so the stock-check scenario needed it); W3C traceparent propagation is ignored by the tracing module (estimated 3-5 hours by hand); GraphQL client for commercetools is absent (custom).
Not available: GraalVM native image; build-time config validation.
Fault-tolerance parity: 31 of 32 scenarios match the baseline. Logging prints each line twice and defaults to DEBUG. Pool per upstream client is fixed at 5, against 50 in the baseline.
Capability checklist (guicedEE/capability-checklist.md): 30 pass, 0 fail, 2 N/A of 32. It is a draft and still needs your review.
8. REST-module shortcomings (PLAN.md §3E: framework fidelity)
You required business routes on GuicedEE's own REST module, so the acceptance numbers measure the framework, not my router:

Acceptance gate: custom router 198 of 262; REST module 133 of 262 (135 of 320 total). With harness aliases after M4: 160 of 262 and 174 of 320. The in-cluster main-mode run gives 145 pass, 142 fail, 26 skipped, identical to local. The port does not reach Quarkus's 319 of 319.
The 65-case loss comes from: no 415/406 handling (about 33), OPTIONS answered with 405 (11), path normalisation (10+), body binding (7); also HEAD gets 405, unknown paths return an HTML 404, malformed JSON binds to null, and handler exceptions become 500 InvocationTargetException.
58 cases are Quarkus-path-specific (metrics label and docs parity); these are expected not to pass under capability parity.
Recorder-mode and auth-mode acceptance were not run in the cluster for GuicedEE.
9. Kotlin verdict
Workable, with friction (guicedEE/FRICTION.md): needs Kotlin 2.4.x, the allopen plugin (mandatory for Guice AOP), only Guice's own @Inject annotations work, Kotlin Jackson module registered explicitly for Jackson 3, module-info.java kept in Java, and Gradle needs --patch-module. Warm Gradle builds are fast (1.2-2.2 s). Quarkus handles Kotlin with less ceremony. Verdict: usable, not smooth.

10. Positioning (research/positioning.md)
GuicedEE is a personal platform developed since 2016 by one maintainer (Marc Magon). It has no written "why"; the stated pitch is README and tagline. Real differences: JPMS-first packaging, no build-time processing, ServiceLoader discovery, Guice plus Vert.x 5 plus Jackson 3.
Against peers: Quarkus (build-time optimisation, native, big ecosystem), Micronaut (compile-time DI, native), Helidon (Níma, virtual threads, lighter), Dropwizard (curated libraries, simple), GlassFish/JBoss (full Jakarta platform servers). Peer facts rest largely on search snippets, not deep verification.
Niche: teams that want JPMS-modular, runtime-assembled Guice applications on the Vert.x event loop and accept a single-maintainer stack. For your criterion (limited-resource k8s, fastest start and scale), Quarkus native and Micronaut fit better. The agent's view, which I share, is that demand looks closer to personal preference than demonstrated market need. This is an assessment from limited evidence, not a measurement.
Licensing: the Examples, Parent, Workflows and Packages repos are GPL-3.0; modules are Apache-2.0. I copied nothing from the GPL repos.
11. Caveats you should weigh
Author bias in reverse. The port is mine; the author may know startup levers I do not. Both frameworks got the same flag set; neither was exhaustively tuned. A fair re-test would give the GuicedEE author a chance to tune.
Busy host. The Mac was not quiet in the first measurement attempt (host CPU idle 68-91%, Spotlight and macOS background work, and two sleeps). The resumed run held caffeinate, with no sleep and no API drop. The reverse-order pass changed nothing (within 3%).
Single-node scale-out. Rancher runs on one node, with local images (no pull time). Real clusters add scheduling, image pull and node scale-up time, which hit every variant, so the relative ranking is more reliable than the absolute seconds.
Unexplained timing effect. A pod started alone becomes Ready 1.6-2x slower than the same pod started together with 4-7 others (Quarkus JVM 12.4 s against 7.9 s; GuicedEE 34 s against 18 s). Cause not isolated; compare variants only within one table.
Ramps and fixed-rate runs are small samples: ramps n=1 per variant; cold fixed-rate n=2; scale-out under load n=1 per variant plus one control; 1 to 8 scale-out n=2. The tuned (TieredStopAtLevel=1) variants have startup and ramp data only. M-7 (15-minute run) and the HPA test were not run.
Per-pod state. The in-memory persistence means order-context lookups 404 when served by another replica (about 4.5% with 3 replicas, for every variant). This was excluded from the failure figures.
Native timing bias. The probe learns pod IPs through a hostPath file, adding up to about 1 s of pickup delay to native's /ready and first-business numbers; this biases native against itself, not for it.
Hello-world against real service. Early spike numbers (543-1013 ms first response on a hello-world) are not representative; all headline numbers above use the real service.
Not covered: Firestore and Secret Manager (a non-default compile-only variant, not built), full M5 verification, JFR or allocation profiling, long-run soak and GC behaviour, real GKE. Nothing was run against any non-rancher-desktop context.
Secrets exposure. During the early spike, a subagent printed ~/.gradle/gradle.properties into the session transcript. Private-repository tokens may be in it; consider rotating them.
12. What would change the verdict
If your service can use a warm-up gate and you can afford about 500 MiB per pod, then Quarkus JVM and GuicedEE are within about 20% on warm throughput, and build time and dependency count could decide. Quarkus is ahead on cold start and on memory, GuicedEE is ahead on build speed.
If memory or scale-out speed is the constraint, Quarkus native is the only option here that holds up (54 MiB, 1.9 s to five serving pods, 0% errors under cold load).
GuicedEE would need GraalVM support or substantial startup work from its author to compete on your primary criterion. I would re-test if that arrives.
13. Open items (not done; evaluation closed)
guicedEE/capability-checklist.md is still a draft awaiting your review.
Not run: M-7 15-minute run, HPA test, repeats of the n=1 results, recorder and auth acceptance runs for GuicedEE in-cluster, the Firestore and Secret Manager variant.
Rotate the tokens in ~/.gradle/gradle.properties if they are real.
Recommendation: consider native for the scale-out criterion; keep JVM with a readiness warm-up gate otherwise.
14. Cleanup state
The eval-quarkus-*, eval-guicedee* and eval-load-1 namespaces are deleted (stuck Terminating; the measurement agent cleared an empty finalizer on them). eval-mocks is still running. Local images (qvh/*:eval) remain in Rancher's Docker. Nothing was pushed, committed or run against any other cluster.

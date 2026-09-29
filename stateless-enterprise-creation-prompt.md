# Prompt: Start & Create a New Enterprise Using a `Mutiny.StatelessSession`

## Goal

Allow the full **start / create new enterprise** lifecycle in ActivityMaster FSDM to be driven from a
Hibernate Reactive **`Mutiny.StatelessSession`** (no first-level cache, no dirty-checking, JDBC-batchable
inserts) — not only from a `Mutiny.Session`.

Today the entry points only accept `Mutiny.Session`:

```java
Uni<IEnterprise<?,?>> startNewEnterprise(Mutiny.Session, name, adminUser, adminPass);
Uni<IEnterprise<?,?>> startNewEnterprise(Mutiny.Session, name, adminUser, adminPass, UUID);
Uni<IEnterprise<?,?>> createNewEnterprise(Mutiny.Session, IEnterprise);
```

We want stateless-session counterparts:

```java
Uni<IEnterprise<?,?>> startNewEnterprise(Mutiny.StatelessSession, name, adminUser, adminPass);
Uni<IEnterprise<?,?>> startNewEnterprise(Mutiny.StatelessSession, name, adminUser, adminPass, UUID);
Uni<IEnterprise<?,?>> createNewEnterprise(Mutiny.StatelessSession, IEnterprise);
```

## Why this is large

`Mutiny.Session` and `Mutiny.StatelessSession` are **distinct interfaces** (no shared supertype for the
operations we use). The enterprise creation flow fans out across the whole FSDM stack, and **every**
downstream contract is currently `Mutiny.Session`-only:

| Layer | Contract(s) that are `Mutiny.Session`-only today |
|---|---|
| Enterprise entry | `IEnterpriseService.create/startNewEnterprise/createNewEnterprise/loadUpdates/getUpdates/getEnterpriseAppliedUpdates/performPostStartup` |
| Systems | `IMasterSystem.registerSystem/createDefaults/postStartup/getSystem/getSystemToken/hasSystemInstalled` |
| Systems svc | `ISystemsService.getActivityMaster/findSystem/create/registerNewSystem/getSecurityIdentityToken/doesSystemExist` |
| Passwords | `IPasswordsService.createAdminAndCreatorUserForEnterprise/findByUsername/addUpdateUsernamePassword/...` |
| Classifications | `IManageClassifications.addClassification/addOrUpdateClassification/findClassifications/...` |
| Warehouse core | `IWarehouseCoreTable` (live single-create security, canRead/canWrite, readableIds) |
| Updates | `ISystemUpdate.update(Mutiny.Session, ...)` and every `@SortedUpdate` implementer |
| Builders | EntityAssist builders already support both (`builder(Mutiny.StatelessSession)`) ✅ |

A true end-to-end stateless flow needs stateless overloads (or a session-agnostic abstraction) threaded
through all of the above. EntityAssist already supports both session kinds at the builder level, and
the security batch path (`createDefaultSecurity(Mutiny.StatelessSession, ...)`) is already stateless —
those are the proof points that the rest can follow.

## Design decision

Two viable strategies:

1. **Overload proliferation** — add a `Mutiny.StatelessSession` overload beside every `Mutiny.Session`
   method down the chain. Most faithful (genuinely runs stateless end-to-end) but touches the most files.
2. **Bridge at the entry point** — stateless entry points use the stateless session for the lean,
   batchable work (the enterprise record `insert`, name→id resolution, idempotency reads) and open a
   managed stateful session (via `sessionFactory` / `SessionUtils`) for the parts that fundamentally need
   a persistence context (managed-entity classifications, live single-create security, post-startup).

**Chosen path: phased.** Ship the **bridge** first (Phase 1 — immediately usable, compiles, no API
churn downstream), then progressively push statelessness deeper (Phases 2-4) where it pays off
(bulk install throughput), converting overloads layer by layer with tests at each step.

## Phases

### Phase 1 — Stateless entry points (bridge) ✅ START HERE
- `EnterpriseService` / `IEnterpriseService`:
  - `create(Mutiny.StatelessSession, name, desc)` — stateless find-or-`insert` of the `Enterprise` row.
  - `getEnterprise(Mutiny.StatelessSession, UUID)` — `session.get(Enterprise.class, uuid)`.
  - `startNewEnterprise(Mutiny.StatelessSession, ...)` ×2 and
    `createNewEnterprise(Mutiny.StatelessSession, IEnterprise)` — seed the enterprise record on the
    stateless session, then bridge to the existing stateful orchestration for the deep install.
- No downstream signature changes. Fully backwards compatible.

### Phase 2 — Stateless systems + systems-service
- Add `Mutiny.StatelessSession` overloads to `IMasterSystem` (`registerSystem`, `createDefaults`,
  `hasSystemInstalled`, `getSystem`, `getSystemToken`) and `ISystemsService.getActivityMaster` /
  `getSecurityIdentityToken` / `findSystem` / `create`.
- Convert `EnterpriseService.installSystems*/createBase*/performSystemInstall` to a stateless variant.

> **Scope reality (discovered during Phase 2):** the *write* install methods (`createDefaults`,
> `registerSystem`) and the identity-classification `findSystem(ISystems, parent)` /
> `getSecurityIdentityToken` fan out into the whole domain layer (`IClassificationService`,
> `ISecurityTokenService`, `IWarehouseCoreTable.canRead`, …), all `Mutiny.Session`-only. Converting
> those safely is a core-wide re-plumb (Phases 3-4). Phase 2 therefore ships only the **pure
> entity-lookup** surface that genuinely runs stateless today (builders already support it):
> `ISystemsService.getActivityMaster/findSystem(enterprise,name)/doesSystemExist` +
> `IMasterSystem.getSystem/hasSystemInstalled(StatelessSession)`. The heavy install stays on the
> Phase 1 stateful bridge.

### Phase 3 — Stateless classifications, updates & passwords
- `IManageClassifications` stateless overloads (link find-or-create).
- `ISystemUpdate.update(Mutiny.StatelessSession, ...)` + stateless `loadUpdates/getUpdates`.
- `IPasswordsService.createAdminAndCreatorUserForEnterprise(Mutiny.StatelessSession, ...)`.

### Phase 4 — Stateless security + post-startup + tests
- Live single-create security on stateless (or keep the existing stateless-batch path).
- `performPostStartup` stateless path.
- Test coverage: `startNewEnterprise(StatelessSession, ...)` provisions an enterprise + admin identical
  to the stateful path (assert security row counts per the matrix in the activitymaster skill).

## Rules / constraints (from skills)

- **Stateless sessions cannot hydrate `@Cacheable` entities with eager `@ManyToOne` associations**
  (validated against PostgreSQL). The `Systems` entity is `@Cacheable` + eager `enterprise`/`activeFlag`.
  On a `Mutiny.StatelessSession`:
  - a **criteria query** that materialises the entity underflows Hibernate Reactive's `LoadContexts`
    stack → `NoSuchElementException` at `StandardStack.pop` ("Illegal pop()"); and
  - `session.get(Systems.class, id)` trips the **L2-cache assembler** →
    `PropertyAccessException: Could not set value of type [CompletableFuture]: 'Systems.activeFlagID'`.
  ⇒ **Stateless system lookups must return scalars** (id / count), never the managed entity. Callers
  that need the managed `Systems` entity must use a `Mutiny.Session`. Scalar projections
  (`selectColumn(id).get(UUID.class)`, `getCount()`) and `session.get` of the **root** `Enterprise`
  (no eager parent FK) work fine on a stateless session.

- **One action per session at a time**; never run two ops concurrently on the same session.
- **Never `await()`** in service flows — compose with `chain`/`invoke`, return `Uni`.
- **Don't nest `SessionUtils.withActivityMaster`** inside a flow that already owns a session/tx (a nested
  tx can't see the outer uncommitted writes → `NoResultException`).
- **Stateless security batch path is never gated** by `isSecurityEnabled()`; the live single-create path
  is flag-driven.
- Records must be **committed** before securing them on a separate stateless transaction (FK visibility).
- Use `Environment.getSystemPropertyOrEnvironment(...)` for config — never raw `System.getenv`.

## Acceptance

- `IEnterpriseService` exposes stateless overloads for `create`, `getEnterprise(UUID)`,
  `startNewEnterprise` (×2) and `createNewEnterprise`.
- A stateless-driven `startNewEnterprise(StatelessSession, name, admin, pass)` creates a working
  enterprise + admin user equivalent to the stateful path.
- Existing stateful API and tests remain green (no breaking changes).

## Progress log

- [x] Phase 0 — research + this plan.
- [x] Phase 1 — stateless entry points (bridge) — **done**.
  - `EnterpriseService.create(Mutiny.StatelessSession, name, desc)` — stateless find-or-`insert` leaf.
  - `EnterpriseService.getEnterprise(Mutiny.StatelessSession, UUID)` — `session.get(...)` leaf.
  - `IEnterpriseService` + `EnterpriseService` stateless overloads of `startNewEnterprise` (×2) and
    `createNewEnterprise` — bridge to the stateful orchestration (top-level entry points).
  - **Note / Phase 2 carry-over:** the stateless `startNewEnterprise` deliberately does *not* pre-seed
    the enterprise row on the caller's stateless session, because that write only commits when the
    caller's stateless transaction closes (after the bridged stateful tx has already run) → would
    insert a duplicate. Genuine same-unit stateless seeding lands once the install chain itself takes
    a `Mutiny.StatelessSession` (Phase 2).
- [x] Phase 2 — stateless systems + systems-service (bounded lookup surface) — **done & DB-validated**.
  - `ISystemsService` + `SystemsService` stateless overloads: `doesSystemExist(StatelessSession,…)`
    (scalar `getCount()`), `findSystemId(StatelessSession, enterprise, name,…)` and
    `getActivityMasterId(StatelessSession,…)` (scalar id projection, `selectColumn(Systems_.id)`).
  - `IMasterSystem` stateless default methods: `getSystemId(StatelessSession, enterprise)` and
    `hasSystemInstalled(StatelessSession, enterprise)`, delegating to the systems-service scalars.
  - **Revised from the first attempt:** the originally-added entity-returning stateless overloads
    (`getActivityMaster`/`findSystem`/`getSystem` → `ISystems`) were **withdrawn** — they compiled but
    fail at runtime on a stateless session (the `Systems` eager-association / L2-cache limitation above,
    caught by the safety-net test). The scalar id/boolean resolvers are the genuinely-stateless-safe API.
  - **Deferred (core-wide re-plumb):** the *write* install path (`createDefaults`, `registerSystem`)
    and identity-classification lookups stay on the Phase 1 stateful bridge.
- [x] Phase 3 — stateless safety net — **done & DB-validated (6/6 green against PostgreSQL)**.
  - `TestActivityMasterStatelessEnterprise` provisions the enterprise on the stateful path, then asserts
    the stateless surface matches the stateful baseline: `getEnterprise` by name + uuid, idempotent
    stateless `create`, `doesSystemExist`, `getActivityMasterId`, `findSystemId`.
  - Promoted `create(Mutiny.StatelessSession, name, desc)` onto `IEnterpriseService` (returns the
    exported `IEnterprise<?,?>`).
  - **Run:** `mvn -pl ActivityMaster/core test -Dtest=TestActivityMasterStatelessEnterprise`
    (Testcontainers Postgres) ⇒ `Tests run: 6, Failures: 0, Errors: 0` / BUILD SUCCESS.
  - **Pending deep slices (bottom-up, re-run the test after each):**
    1. `IWarehouseCoreTable` / `IManageClassifications` stateless link find-or-create.
    2. `IClassificationService` / `ISecurityTokenService` stateless overloads.
    3. `ISystemUpdate.update(StatelessSession,…)` + stateless `loadUpdates/getUpdates`.
    4. `IPasswordsService.createAdminAndCreatorUserForEnterprise(StatelessSession,…)`.
    5. `EnterpriseService` install loop converted to thread the stateless session end-to-end.
- [ ] Phase 4 — stateless security/post-startup + tests.

## Architectural conclusion (DB-validated)

The safety-net run surfaced a hard constraint that reshapes Phases 2-5: **the entities the enterprise
install creates and reads (`Systems`, and by the same pattern `Classification`, `SecurityToken`, …) are
`@Cacheable` with eager `@ManyToOne` associations, and Hibernate Reactive cannot hydrate such entities
on a `Mutiny.StatelessSession`** (LoadContexts pop on criteria materialisation; L2-cache reactive-
association `PropertyAccessException` on `session.get`). 

Consequently a *true* end-to-end stateless install is **not achievable without re-mapping those entities**
(dropping `@Cacheable` and/or making the eager associations lazy) — which would degrade the stateful
runtime that deliberately relies on the L2 cache (e.g. `SystemsService.getSystemById`, `NameIdCache`) and
eager navigation. That trade-off is out of scope here.

**Therefore the Phase 1 bridge is the correct long-term design, not a stopgap:** stateless entry points
do the lean, scalar, batchable work (enterprise record `insert`, name→id/count lookups) and bridge to a
managed `Mutiny.Session` for the managed-entity install. The genuinely stateless-safe surface is exactly
what is shipped and tested in Phases 1-3:
- enterprise `create` / `getEnterprise(name|uuid)` (root entity — no eager parent FK),
- `doesSystemExist` (scalar count), `findSystemId` / `getActivityMasterId` / `getSystemId` (scalar id),
- `hasSystemInstalled` (scalar boolean).

Deep slices (classifications/security/updates/passwords write paths) are **deferred-by-design**: they
would only become stateless-capable after an entity-mapping change, and should otherwise continue to run
on the stateful bridge.

## Re-verification (2026-06-27) — root cause pinned to the exact mapping difference

Re-ran the safety net from the core module against Dockerised Postgres:
`Tests run: 7, Failures: 0, Errors: 0` / BUILD SUCCESS — including
`startNewEnterprise_statelessEntry_completesFullProcess` (test #7), which drives the **entire
create-and-start lifecycle from a real `sessionFactory.openStatelessSession()` entry point** and asserts
it completes and resolves the enterprise. The user-facing capability is therefore implemented and
DB-validated.

Direct entity inspection nails the precise rule (no longer a heuristic):

| Entity | `@Cacheable` | Eager `@ManyToOne` FKs | Stateless read result |
|---|---|---|---|
| `Enterprise` | yes | **none** (only LAZY `@OneToMany` collections) | ✅ returns cleanly (root entity) |
| `Systems` | yes | **yes** → `Enterprise` + `ActiveFlag` (both `FetchType.EAGER`) | ❌ `PropertyAccessException: CompletableFuture` on `activeFlagID` / criteria `LoadContexts` pop |

So the constraint is exactly: *a stateless session cannot hydrate a `@Cacheable` entity that eager-loads
`@ManyToOne` associations.* It is **not** a general "no stateless writes" limit —
`WarehouseCoreTable.createDefaultSecurity/createSecurityGrant/createScopeRestrictedSecurity` already
perform **pure stateless inserts** (pre-resolved references, FK-id only) and are used today.

**Net:** the stateless write primitives already exist, stateless reads are safe for FK-free root entities
(`Enterprise`), and the deep install legitimately bridges to managed sessions for the cacheable+eager
entities. No further code change is required to satisfy "start & create a new enterprise from a
`StatelessSession`"; deeper genuine-stateless install remains gated on an entity-remapping decision
(drop `@Cacheable`/make `@ManyToOne` lazy on `Systems`/`Classification`/`SecurityToken`) that would trade
off the stateful runtime's L2-cache + eager-navigation behaviour.

## Breakthrough (2026-06-27) — "fetch ids/scalars + prep" bypasses the eager-association trap

The entity-remapping decision is **no longer the only path**. The eager-association failure only happens
when Hibernate *hydrates the managed entity*. By projecting the row's **own scalar columns** and
**prepping a fresh detached instance** ourselves, a stateless session can return a usable entity without
ever assembling the eager `@ManyToOne` graph:

- `Systems` already exposes a 4-arg constructor `(UUID id, String name, String description, String
  systemHistoryName)`. A multiselect of those four scalar columns (`Object[]`) is a **scalar result, not
  an entity result**, so it never enters the LoadContexts/L2-cache assembler path that underflows on a
  stateless session.
- The eager FK references are wired from data already in hand (the `enterprise` is a method parameter →
  no extra read); the remaining eager FKs are left for the caller to supply where a managed graph is
  actually needed (the stateless security-write API already takes `enterprise`/`activeFlag`/tokens as
  pre-resolved parameters).

Implemented + DB-validated (`Tests run: 8` / BUILD SUCCESS, test #8
`findSystem_stateless_returnsPreppedDetachedEntity`):
- `ISystemsService.findSystem(Mutiny.StatelessSession, enterprise, name, …)` → prepped detached `Systems`
- `ISystemsService.getActivityMaster(Mutiny.StatelessSession, enterprise, …)` → prepped detached `Systems`

The prepped entity carries the correct id + name + wired enterprise (asserted equal to the stateful
baseline). This is the reusable pattern for pushing statelessness deeper **without** dropping `@Cacheable`
or eager mappings: every cacheable+eager entity that needs a stateless read can expose a scalar
constructor projection + prep, and threading the (parameter) session through stays intact.

**Next deep slices can now follow this pattern** (each backed by the safety net): apply the same
scalar-projection-prep to `Classification` / `SecurityToken` resolution, then convert the install loop's
system/classification reads to the stateless prepped resolvers while keeping writes on the existing
stateless insert API.

### Slice progress

- ✅ **`Systems`** — `ISystemsService.findSystem/getActivityMaster(Mutiny.StatelessSession,…)` return
  prepped detached `Systems` (test #8). DB-validated.
- ✅ **`Classification`** — `IClassificationService.find/getIdentityType/getHierarchyType/
  getNoClassification(Mutiny.StatelessSession,…)` return prepped detached `Classification` built from a
  scalar projection of `(id, name, description, classificationSequenceNumber)` with the enterprise wired
  from `system.getEnterprise()` (eager `concept` left null). Test #9 asserts the stateless prepped
  identity-type classification equals the stateful baseline (id + name), composing on the prepped
  stateless system. `Tests run: 9` / BUILD SUCCESS.
- ✅ **`SecurityToken`** — the seven canonical folder/group getters (`getAdministratorsFolder`,
  `getEveryoneGroup`, `getEverywhereGroup`, `getSystemsFolder`, `getApplicationsFolder`,
  `getPluginsFolder`, `getGuestsFolder`) gained `Mutiny.StatelessSession` overloads on
  `ISecurityTokenService`, all delegating to a shared `findFolderTokenStateless(...)` helper. It reuses the
  managed getters' `findFolder + withName + enterprise + range` filters but projects the token's own
  scalars `(id, securityToken, name, description)` and preps a fresh detached `SecurityToken` (5-arg
  constructor), enterprise wired from `system.getEnterprise()`. These are exactly the tokens the stateless
  `createDefaultSecurity(Mutiny.StatelessSession, …, groupFolderTokens, …)` insert API consumes — so the
  canonical seven-grant matrix can be resolved + written on a single stateless unit. Test #10 asserts the
  stateless prepped Administrators folder token equals the stateful baseline (id + name), composing on the
  prepped stateless system. `Tests run: 10` / BUILD SUCCESS.
- ✅ **`ActiveFlag`** — `IActiveFlagService.getActiveFlag(Mutiny.StatelessSession,…)` returns a prepped
  detached `ActiveFlag` (`@Cacheable` but LAZY `@ManyToOne`) via scalar projection of
  `(id, name, description, allowAccess)`, enterprise wired from the parameter.
- ✅ **First stateless WRITE path — `createDefaultSecurity` end-to-end.** Added
  `ISecurityTokenService.resolveDefaultGroupFolderTokens(Mutiny.StatelessSession, system, …)` — the
  previously-missing stateless half — which assembles the canonical seven group/folder tokens (via the
  stateless prepped getters) into a `SECURITY_*`-keyed map. Test #11
  (`createDefaultSecurity_statelessEndToEnd_insertsSevenGrants`) runs the **whole flow on one stateless
  unit**: resolve enterprise → prepped `Systems` → stateless `ActiveFlag` → stateless token map →
  `IWarehouseCoreTable.createDefaultSecurity(StatelessSession, system, enterprise, activeFlag, tokens)` —
  asserting exactly the seven grant rows are inserted. `Tests run: 11` / BUILD SUCCESS. This proves the
  resolve-then-insert default-security path is now fully stateless-capable.
- ✅ **Stateless classification CREATE (the `createDefaults` write primitive).** Added stateless
  `ClassificationsDataConceptService.find(StatelessSession,…)` (prepped `ClassificationDataConcept`) and
  `IClassificationService.create(Mutiny.StatelessSession, name, description, system, …)`. The stateless
  `create` composes every building block on one stateless unit: prepped existence `find` (idempotent) →
  prepped data-concept + active-flag FK references → stateless `insert` of the lean classification row →
  stateless default-security (`resolveDefaultGroupFolderTokens` + `createDefaultSecurity`). Test #12
  (`createClassification_statelessEndToEnd_persistsAndIsIdempotent`) asserts the row is inserted with an
  id, is idempotent (second call returns the same id — no duplicate), and is findable afterwards via the
  prepped read. `Tests run: 12` / BUILD SUCCESS. This is exactly the write a system's `createDefaults`
  performs (e.g. `RulesSystem` creates the `Rules` / `RulesType` classifications).
- ⏭️ **Install loop** — wire a concrete system's `createDefaults` to a stateless overload using
  `create(StatelessSession,…)` + the prepped resolvers, then thread the stateless session up through the
  install sequence.

### First whole-system slice (2026-06-27)

- ✅ **`RulesSystem.createDefaults` runs end-to-end on a `Mutiny.StatelessSession`.** Supporting pieces:
  - `ISystemsService.getSecurityIdentityToken(Mutiny.StatelessSession,…)` — resolves the system identity
    token by **scalar-projecting the `SystemIdentity` relationship-classification's `Value` column**
    (via `SystemsXClassification.findLink` + `selectColumn(WarehouseRelationshipTable_.value)`), never
    hydrating the `@Cacheable` link entity. This was the last read the per-system `createDefaults` needed.
  - `MasterDefaultSystem.getSystem/getSystemToken(Mutiny.StatelessSession,…)` stateless helpers.
  - `IMasterSystem.createDefaults(Mutiny.StatelessSession,…)` default (throws `UnsupportedOperationException`
    = "not yet converted") so the install loop can prefer the stateless overload where present and fall
    back otherwise — the incremental-migration seam.
  - `RulesSystem` overrides it: prepped `findSystem` → stateless `getSystemToken` → stateless
    `IClassificationService.create` for `Rules` + `RulesType`.
  - Test #13 (`rulesSystemCreateDefaults_stateless_runsEndToEnd`) runs the whole system's defaults on one
    stateless transaction and asserts both classifications resolve afterwards. `Tests run: 13` / BUILD
    SUCCESS. The `@BeforeAll` still provisions via the proven **stateful** install, confirming no
    regression to the managed path.
- ⏭️ **Next** — convert the remaining leaf systems' `createDefaults` the same way, then teach the
  `EnterpriseService` install loop to call the stateless overload (with the `UnsupportedOperationException`
  fallback) on a stateless transaction.

### Hierarchy write + second system (2026-06-27)

- ✅ **Stateless hierarchy `addChild`** — `IContainsHierarchy.addChild(Mutiny.StatelessSession,…)` default.
  Existence is checked with a scalar `getCount()` (never hydrating the `@Cacheable` link entity), the
  active flag is resolved explicitly (the prepped system's eager active-flag is `null`), the
  `…XClassification` link row is written with `session.insert(...)`, and its default security is provisioned
  via the stateless `resolveDefaultGroupFolderTokens` + `createDefaultSecurity`.
- ✅ **Parent-aware stateless classification create** — `IClassificationService.create(StatelessSession,
  name, description, system, IClassification parent, …)` (+ `Enum` and `Enum`-with-`Enum`-parent default
  overloads). Inserts/finds the classification then links it under the parent via the stateless `addChild`
  — idempotent in both steps.
- ✅ **`ProductsSystem.createDefaults` runs end-to-end on a `Mutiny.StatelessSession`** — provisions the
  full `Products → ProductGroup → {ProductTypeName, ProductPremiumType, ProductBaseCost}` hierarchy
  statelessly. Tests #14 (`createHierarchy_stateless_linksFreshChildToParent` — fresh names → real
  insert + link path) and #15 (`productsSystemCreateDefaults_stateless_runsEndToEnd`) pass.
  `Tests run: 15` / BUILD SUCCESS. Regression: `TestActivityMasterSecurityAdminLogin` passes 3/3 in
  isolation (a batch failure was pre-existing shared-DB test-ordering contamination, not these changes).
- ⏭️ **Next** — convert the remaining systems' `createDefaults` (Address, Events, InvolvedParty,
  Arrangements, Classifications, …) and then wire the `EnterpriseService` install loop to prefer the
  stateless overload (with the `UnsupportedOperationException` fallback) on a stateless transaction.

### More systems + create family (2026-06-27)

- ✅ **Concept-/parent-aware stateless create family.** `IClassificationService` now exposes the full
  stateless create matrix: `create(StatelessSession, name, desc, [concept], system, [parent], …)` plus
  `Enum`, `Enum`+`Enum`-parent, `Enum`+`String`-parent, and `Enum`+concept default overloads — mirroring
  the managed create family. The core impl resolves the supplied data-concept (or `NoClassification`),
  inserts the lean row, provisions stateless default security, and links the parent via stateless
  `addChild`.
- ✅ **`ClassificationsSystem.createDefaults` (the foundational classifier) runs stateless** — provisions
  the enterprise root, hierarchy type, `NoClassification`, default, `Security` + `SystemIdentity` /
  `SecurityPassword(/Salt)`, and the enterprise classifications, all on one stateless session (test #16).
- ✅ **`AddressSystem` / `ResourceItemSystem`** — stateless `createDefaults` overrides (both are no-op
  provisioners; trivial, safe additions to the migration seam).
- `Tests run: 16` / BUILD SUCCESS. **Converted systems: `RulesSystem`, `ProductsSystem`,
  `ClassificationsSystem`, `AddressSystem`, `ResourceItemSystem`.**
- ⏭️ **Next** — remaining classification systems that also create resource-item types / loops
  (`EventsSystem`, `InvolvedPartySystem`, `ArrangementsSystem`), then wire the `EnterpriseService` install
  loop to prefer the stateless `createDefaults` overload (with the `UnsupportedOperationException`
  fallback) on a stateless transaction — the step that makes the install itself partially stateless.

### InvolvedParty entity creates + more systems (2026-06-27)

- ✅ **Stateless `InvolvedParty*Type` reference-entity creates.** `IInvolvedPartyService` gained stateless
  `createIdentificationType` / `createNameType` / `createType` (String + `Enum` overloads), each backed by
  a stateless prepped finder (scalar projection of `id/name/description` on the `@Cacheable`,
  no-eager-FK entities) + a stateless insert + the stateless default-security matrix.
- ✅ **`InvolvedPartySystem.createDefaults` runs stateless** — provisions all 15 identification types, 12
  name types and 7 involved-party types on one stateless session (test #17).
- ✅ **`ArrangementsSystem`** — stateless `createDefaults` (no-op provisioner).
- `Tests run: 17` / BUILD SUCCESS. **Converted systems (7): `RulesSystem`, `ProductsSystem`,
  `ClassificationsSystem`, `AddressSystem`, `ResourceItemSystem`, `ArrangementsSystem`,
  `InvolvedPartySystem`.**
- ⏭️ **Next** — `EventsSystem` (needs a stateless resource-item-type create + its concept/loop creates),
  then the base-setup infrastructure systems, and finally wire the `EnterpriseService` install loop to
  prefer the stateless `createDefaults` overload (with the `UnsupportedOperationException` fallback).

### EventsSystem + resource-item types — all domain systems done (2026-06-27)

- ✅ **Stateless resource-item-type create** — `IResourceItemService.createType(Mutiny.StatelessSession,…)`:
  scalar `getCount()` existence, stateless insert (id assigned up front) + stateless default security, or a
  prepped detached `ResourceItemType` when it already exists.
- ✅ **`EventsSystem.createDefaults` runs stateless** — provisions the `LogItemTypes` / `EventStatus`
  concept classifications, every `LogItemTypes` child (enum + String-parent create), and the `LogItem`
  resource-item type, all on one stateless session (test #18).
- `Tests run: 18` / BUILD SUCCESS. **All 8 domain systems are now stateless-capable: `RulesSystem`,
  `ProductsSystem`, `ClassificationsSystem`, `AddressSystem`, `ResourceItemSystem`, `ArrangementsSystem`,
  `InvolvedPartySystem`, `EventsSystem`.**
- ⏭️ **Next** — the base-setup infrastructure systems (`SystemsSystem`, `SecurityTokenSystem`,
  `EnterpriseSystem`, `TimeSystem`, `ActiveFlagSystem`, `ClassificationsDataConceptSystem`) that bootstrap
  before the Activity Master system is fully up, then wire the `EnterpriseService` install loop to prefer
  the stateless `createDefaults` overload (with the `UnsupportedOperationException` fallback).

### Infrastructure systems (2026-06-27)

- ✅ **Stateless `SystemsService.create(StatelessSession, enterprise, name, desc[, history])`** — lean
  find-or-create of a `Systems` row (no security writes; later phases secure it).
- ✅ **Stateless `ActiveFlagService.create(StatelessSession, …)`** — find-or-create of an ActiveFlag
  reference row (no security stamped, matching the managed reference-data create).
- ✅ **Stateless `ClassificationsDataConceptService.createDataConcept(StatelessSession, …)`** — find-or-create
  data concept + stateless default security (tolerant when the security structure isn't up yet).
- ✅ **5 of 6 infrastructure systems converted**: `SystemsSystem` (creates the Enterprise/ActiveFlag/Activity
  Master `Systems` rows), `ActiveFlagSystem` (all active-flag rows), `ClassificationsDataConceptSystem`
  (every `EnterpriseClassificationDataConcepts` value), `EnterpriseSystem` + `TimeSystem` (no-op). Tests
  #19–#21 validate the three with writes. `Tests run: 21` / BUILD SUCCESS.
- ⏳ **`SecurityTokenSystem` — the one remaining (the 858-line security bootstrap).** It is special: it
  *creates* the very root/group/folder security tokens, the token hierarchy links, the access-grant matrix,
  the security classifications, AND the Activity Master involved party — i.e. it builds the structure that
  every stateless default-security primitive (`resolveDefaultGroupFolderTokens`, `createDefaultSecurity`)
  *consumes*, so it has inherent bootstrap circularity. A faithful stateless conversion needs a dedicated
  set of new stateless write primitives that don't exist yet:
  1. `IClassificationService.create(StatelessSession, …, Integer sequence, …)` (sequence variant);
  2. `ISecurityTokenService.create(StatelessSession, classification, name, desc, system)` (token create);
  3. `ISecurityTokenService.grantAccessToToken(StatelessSession, …)` (grant rows);
  4. `ISecurityTokenService.link(StatelessSession, parent, child, classification)` (token hierarchy);
  5. stateless relationship-classification add (`addOrUpdateClassification` / `addOrReuseClassification`);
  6. stateless involved-party create (`IInvolvedPartyService.create(StatelessSession, …)`).
  Until then it remains on the `IMasterSystem.createDefaults(StatelessSession)` →
  `UnsupportedOperationException` seam, i.e. the install loop will run it on the stateful bridge.
  **Converted so far: 13 of 14 systems** (all 8 domain + 5 of 6 infrastructure).
- ⏭️ **Then** — wire the `EnterpriseService` install loop to prefer the stateless `createDefaults`
  overload (with the `UnsupportedOperationException` fallback) on a stateless transaction.

### SecurityTokenSystem bootstrap — primitive #5 landed: stateless relationship-classification add (2026-06-27)

Continuing the **no-bridge** conversion of `SecurityTokenSystem` (the last of the 6 listed missing
write primitives — see the list above). The user requirement is explicit: *the system must accommodate
a `StatelessSession` and must not bridge to a managed session.*

- ✅ **Stateless relationship-classification adds** — `IManageClassifications` gained
  `addOrReuseClassification(Mutiny.StatelessSession, …)` and `addOrUpdateClassification(Mutiny.StatelessSession, …)`
  (Enum + String name overloads), both **find-or-insert** (idempotent) returning `Uni<Void>`. They:
  - check existence with a scalar `getCount()` on the `…XClassification` link builder (never hydrating the
    `@Cacheable` link entity);
  - resolve the classification via the existing stateless `IClassificationService.find(StatelessSession,…)`;
  - wire the enterprise from `system.getEnterprise()` (the prepped/created system carries it — no managed
    lazy fetch), resolve the active flag via `IActiveFlagService.getActiveFlag(StatelessSession,…)`;
  - write the link with `session.insert(...)` and provision its default security via the stateless
    `resolveDefaultGroupFolderTokens` + `createDefaultSecurity` path (tolerant — the security bootstrap's
    batch apply-defaults phase re-applies it, and the group/folder tokens may not exist yet at the point
    `enterprise.addOrUpdateClassification(EnterpriseIdentity,…)` is first called).
  - A migration-seam `configureForClassification(Mutiny.StatelessSession, linkTable, classification, system)`
    default (throws `UnsupportedOperationException`) is overridden on **`Enterprise`** and **`Systems`**
    (the two link owners the bootstrap tags: `EnterpriseIdentity` and `SystemIdentity`); both just set the
    owning back-reference (their managed `configureForClassification` was already session-free).
  - Added a stateless `numberOfClassifications(Mutiny.StatelessSession, name, value, system, …)` (scalar
    `getCount`, no `canRead`) for verification.
- ✅ **Compiles green** — `activity-master-client` and `activity-master` both `BUILD SUCCESS` (offline).
- ✅ **Test #23** (`addOrReuseClassification_stateless_tagsSystemIdempotently`) added: on one stateless
  transaction, create a fresh classification then tag the Activity Master `Systems` row with it; re-run
  the create+tag a second time; assert exactly **one** active link exists (find-or-insert idempotency).
  Compiles. *(Full DB run pending — the suite-level Testcontainers run is heavy and `-Dtest=Class#method`
  did not isolate the single method in this Maven 4 setup; run with the suite to validate:
  `mvn -o -f ActivityMaster/core/pom.xml test -Dtest=TestActivityMasterStatelessEnterprise`.)*

#### Remaining for a genuinely stateless `SecurityTokenSystem.createDefaults(StatelessSession)` (replace the bridge)

The token/classification/grant/link/apply-defaults phases are now all backed by stateless primitives
(`classificationService.create(StatelessSession,…)`, `securityTokenService.create/grantAccessToToken/
link/applyDefaultSecurityToTable(StatelessSession,…)`, and the new `addOr*Classification(StatelessSession,…)`).
The one deep dependency left is the **Activity Master involved-party creation** (`createActivityMasterInvolvedParty`
→ `SystemsSystem.createInvolvedPartyForNewSystem`). Remaining primitives to add (each then unblocks the
full no-bridge `createDefaults` conversion + the `EnterpriseService` install-loop wiring):

1. Stateless party-relationship adds — `IManagePartyIdentificationTypes`/`IManagePartyTypes`/
   `IManagePartyNameTypes` `addOrReuseInvolvedParty*(Mutiny.StatelessSession,…)` (their `configure*Addable`
   hooks are already synchronous/session-free; mirror the `addOr*Classification` stateless pattern with a
   `getCount` existence gate + `session.insert`).
2. Stateless involved-party create — `IInvolvedPartyService.create(Mutiny.StatelessSession, system, Pair,
   boolean, …)` + `setupInvolvedPartyOrganicStatus` (swap `session.persist`→`session.insert`,
   `session.fetch(system/enterprise)`→`system.getEnterprise()`, managed `createDefaultSecurity`→stateless).
3. `SystemsSystem.createInvolvedPartyForNewSystem(Mutiny.StatelessSession,…)` (composes 1+2 with the
   already-stateless `getActivityMaster`/`getSecurityIdentityToken`/`findInvolvedParty*Type`).
4. `SecurityTokenSystem.createDefaults(Mutiny.StatelessSession,…)` — replace the bridge body with stateless
   `createSecurityClassifications`/`createSecurityTokens`/`createGroupsAndFolders`/apply-defaults (via
   `applyDefaultSecurityToTable(StatelessSession,…)`)/`createActivityMasterInvolvedParty` helpers; update
   test #22 to assert the genuine stateless run (not the bridge).
5. `EnterpriseService` install loop — prefer `createDefaults(StatelessSession)` (with the
   `UnsupportedOperationException` fallback) on a stateless transaction, so the whole install runs from a
   `StatelessSession` entry point with only the first parameter changed.

### Batched `IManage<xxx>` capability conversion (2026-06-27)

Rather than convert relationship-capability methods piecemeal, added `Mutiny.StatelessSession` twins of the
read + create family (`find*` / `findAll*` / `numberOf*` / `has*` / `add*` / `addOrReuse*` / `addOrUpdate*`)
across the capability mixins, following one uniform transformation:

- **Reads** are verbatim (the EntityAssist query builder is session-polymorphic — `builder(session)` accepts
  both session kinds; bodies already use `system.getEnterprise()`, not `session.fetch`).
- **Writes** swap `session.fetch(system).fetch(enterprise)` → `system.getEnterprise()`,
  `session.persist` → `session.insert`, the managed per-row `createDefaultSecurity(session, system, …)` → the
  stateless `resolveDefaultGroupFolderTokens` + `createDefaultSecurity(session, system, enterprise, activeFlag,
  tokens, …)` path (tolerant), and `configure*Addable(session, …)` → `configure*Addable((Mutiny.Session) null,
  …)` (the hook ignores its session — verified). `addOrUpdate` reuses a new stateless
  `SCDLinkMaintenance.retireActiveRow(Mutiny.StatelessSession, …)` overload.
- Link entities are either non-`@Cacheable` or `@Cacheable` with **LAZY** FKs, so `.get()` / `.getCount()` on
  the `*X*` relationship builders are stateless-safe (only `Systems`/`Classification`/`SecurityToken` — the
  cacheable+eager dimension entities — are not).

Supporting primitives added: `IActiveFlagService.getArchivedFlag/getDeletedFlag(Mutiny.StatelessSession, …)`
(+ `ActiveFlagService` impl, shared scalar-projection helper), `SCDLinkMaintenance.retireActiveRow` stateless
overload.

**Converted (12 of 17 capability mixins) — client + core `BUILD SUCCESS`:**
`IManageClassifications`, `IManagePartyIdentificationTypes`, `IManagePartyTypes`, `IManagePartyNameTypes`,
`IManageProducts`, `IManageRules`, `IManageResourceItems`, `IManageEvents`, `IManageGeographies`,
`IManageAddresses`, `IManageArrangements`, `IManageInvolvedParties`.

**Deferred (5 — the `*Types` mixins): `IManageProductTypes`, `IManageRuleTypes`, `IManageEventTypes`,
`IManageArrangementTypes`, `IManageResourceItemTypes`.** These resolve their secondary **by name** through a
`Mutiny.Session`-only service finder (e.g. `IProductService.findProductTypeForProduct`,
`IEventService.findEventType`), so a faithful stateless twin first needs `Mutiny.StatelessSession` overloads of
those service finders. They are not on the enterprise-bootstrap path, so they are a clean follow-up slice.

> Note: the pure-close SCD mutations (`update` / `expire` / `archive` / `remove` — `session.merge` /
> `session.detach` based) were not twinned in this batch; they mutate existing managed rows and are not part of
> the create/build flows. A stateless variant would use `session.update` (or a bulk close) and can follow.

### `*Types` mixins finished + `IContains<xxx>` (2026-06-27)

**Option (a) complete — all 17 `IManage<xxx>` mixins now have stateless twins.** The 5 `*Types` mixins were
unblocked by adding `Mutiny.StatelessSession` overloads of the secondary-by-name service finders (each a
scalar-projection + prep of the `@Cacheable`/eager `*Type` entity via its `(UUID,String,String)` constructor):

- `IProductService.findProductTypeForProduct` (+ `ProductService` impl)
- `IRulesService.findRulesTypes` (+ `RulesService`)
- `IResourceItemService.findResourceItemType` (+ `ResourceItemService`)
- `IEventService.findEventType` (+ `EventsService`)
- `IArrangementsService.findArrangementType` (+ `ArrangementsService`)

With those, the read + create-family (`find*` / `numberOf*` / `has*` / `add*` / `addOrReuse*` / `addOrUpdate*`)
stateless twins were added to **`IManageProductTypes`, `IManageRuleTypes`, `IManageEventTypes`,
`IManageResourceItemTypes`, `IManageArrangementTypes`** (the secondary `*Type` is resolved via the stateless
finder; object-based `ArrangementType` writes need no finder). Client + core `BUILD SUCCESS`.

**`IContains<xxx>` capability interfaces.** Only two carry `Mutiny.Session` methods; the other seven are pure
POJO getters/setters (no session):
- `IContainsHierarchy` — `addChild(StatelessSession,…)` already existed; added verbatim stateless read twins
  `findParent` / `findParentLink` / `findParents` / `findChildren` (Enum + String). `archiveChild` (a close
  mutation via `table.archive(session)`) is not twinned, consistent with the IManage close-mutation skips.
- `IContainsData` — added `getData(Mutiny.StatelessSession, …)` (abstract) + the `ResourceItem` impl (MongoDB
  json-store path, with a stateless relational fallback via `session.get(ResourceItemDataValue.class, id)`
  degrading to empty bytes on failure).

Full core `test-compile` green. The complete stateless relationship/hierarchy capability is now in place
to support a genuinely stateless enterprise build (the remaining no-bridge `SecurityTokenSystem` + install-loop
wiring is the next slice).

### Query-builder + type-interface stateless twins (2026-06-28)

Extended the `Mutiny.StatelessSession` copies to the query-builder and entity-"type" interfaces (same pattern —
the EntityAssist builder and `createNativeQuery` are session-polymorphic, so reads are verbatim swaps):

- **Query builders:** `IEventQueryBuilder.hasEventType(StatelessSession, …)` (×3 overloads);
  `IQueryBuilderClassifications.getClassificationsValuePivot(StatelessSession, …)` (×3 — the aggregate pivot
  executes its native SQL directly on the supplied stateless session rather than the builder's bound session).
- **Type interfaces:** `IResourceItem.getFilename/getDataRow(StatelessSession, …)` (+ `ResourceItem` impls);
  `IInvolvedParty.getSecurityIdentity(StatelessSession)` (+ `InvolvedParty` impl, composing the stateless
  party-id-type relationship read + `getSystemToken(StatelessSession,…)`); `IContainsData.getData(StatelessSession,…)`
  (+ `ResourceItem` impl); `IWarehouseCoreTable.countDefaultSecurity(StatelessSession)` (impl already present).
- **Party-mixin relationship-read twins (completing their read family):** added stateless
  `findInvolvedParty{IdentificationType,Type,NameType}` / `…All` / `numberOf…` / `has…` to
  `IManagePartyIdentificationTypes` / `IManagePartyTypes` / `IManagePartyNameTypes`, and declared the three
  stateless secondary finders (`findInvolvedPartyIdentificationType` / `findType` / `findInvolvedPartyNameType`)
  on the `IInvolvedPartyService` interface (the concrete prepped impls already existed).

Client + core + `test-compile` all `BUILD SUCCESS`.

> Deferred (deep security-read chain — not a simple builder twin): `IWarehouseCoreTable.canRead/canWrite/readableIds`
> stateless would require twinning `hasGrant` → `ISecurityTokenService.getApplicableSecurityTokenIds` →
> `IActiveFlagService.getVisibleRangeAndUpIds` → abstract `resolveActiveFlagIdByName`. `IWarehouseBaseTable.expire`
> (a close mutation) is likewise deferred, consistent with the IManage `archive`/`remove` skips. The query-builder
> `.canRead(system, identityToken)` filter takes no session (bound to the builder's session) and is already
> stateless-compatible.

### Stateless security-read chain (2026-06-28)

Twinned the full per-row security-read chain end-to-end (bottom-up), so the `IWarehouseCoreTable` row-level
checks now have stateless variants:

- `NameIdCache.getActiveFlagId(Mutiny.StatelessSession, …)` + a `StatelessResolver` functional interface.
- `IActiveFlagService.resolveActiveFlagIdByName(Mutiny.StatelessSession, …)` (abstract) + `ActiveFlagService` impl,
  and the `getVisibleRangeAndUpIds(Mutiny.StatelessSession, …)` / `getRemovedRangeIds(Mutiny.StatelessSession, …)`
  default range-id assemblers.
- `ISecurityTokenService.getApplicableSecurityTokenIds(Mutiny.StatelessSession, …)` default — the `WITH RECURSIVE`
  applicable-token expansion runs via `session.createNativeQuery(…)` (fully stateless-safe).
- `IWarehouseCoreTable.canRead/canWrite/readableIds(Mutiny.StatelessSession, …)` decls + `WarehouseCoreTable`
  impls (+ a private stateless `hasGrant`). canRead/canWrite delegate to `hasGrant`; readableIds mirrors the
  managed scalar collection.

Client + core + `test-compile` all `BUILD SUCCESS`.

> Runtime note: the applicable-token resolution (native SQL) is fully stateless-safe; the security-row scan in
> `hasGrant`/`readableIds` mirrors the managed `securities.getAll()` entity read, so it is subject to the same
> `@Cacheable`+eager hydration rule as any other entity read on a stateless session (the security-link rows must
> be hydratable). The remaining `IWarehouseBaseTable.expire` close-mutation stays deferred.







### SecurityTokenSystem no-bridge + SystemsSystem stateless party (2026-06-28)
Removed the **last stateless-to-managed bridge**. The two remaining deep pieces are now genuinely stateless:
- **`SystemsSystem.createInvolvedPartyForNewSystem(Mutiny.StatelessSession, �)`** � new overload composing the
  already-stateless pieces: prepped `getActivityMaster` / `getSecurityIdentityToken` reads, stateless
  `IInvolvedPartyService.create`, the prepped `findInvolvedParty*Type` finders, and the stateless
  `addOrReuseInvolvedParty{IdentificationType,Type,NameType}` writers. (Wildcard-capture leak on the result was
  fixed by an explicit `<IInvolvedParty<?,?>>failure(...)` branch + `replaceWith((IInvolvedParty<?,?>) ip)`.)
- **`SecurityTokenSystem.createDefaults(Mutiny.StatelessSession, �)`** � now runs the **entire** security
  bootstrap directly on the supplied stateless session (no `sessionFactory.withTransaction` bridge):
  `createSecurityClassifications` (stateless `IClassificationService.create` concept/sequence/parent overloads),
  `createSecurityTokens` (stateless token `create` + `grantAccessToToken` + `enterprise.addOrUpdateClassification`),
  `createGroupsAndFolders` (sequential stateless token creates threaded through an `ISecurityToken[]`, stateless
  `system.addOrReuseClassification`, stateless `classificationService.find`, stateless `link`), the full
  `createAccessGrantsSequentially` grant matrix (stateless `grantAccessToToken`), both apply-defaults phases
  (now `securityTokenService.applyDefaultSecurityToTable(StatelessSession, new X(), system)` per table), and
  `createActivityMasterInvolvedParty` (the new `SystemsSystem` stateless overload). Within one stateless tx,
  inserts execute immediately (read-your-writes), so earlier-created tokens are visible to the later
  apply-defaults phases.
Client unchanged (only existing stateless interface methods used). **Core + test-compile `BUILD SUCCESS`.**
Test #22 updated: `securityTokenSystemCreateDefaults_statelessNoBridge_completes` (asserts the Administrators
folder + Everyone group resolve after a genuinely-stateless, top-level `openStatelessSession()` bootstrap).
> Could not run the Testcontainers DB suite this slice � Docker Desktop daemon was not running. The changes are
> compile-validated; the DB safety-net (tests #1-#23) should be re-run once Docker is up.
#### Still NOT done � full no-bridge `startNewEnterprise(Mutiny.StatelessSession)` (prompt #4/#5)
`EnterpriseService.startNewEnterprise(Mutiny.StatelessSession, �)` still bridges via
`SessionUtils.withSessionTx`. Making the **whole** install run from a stateless entry point is blocked on
converting the remaining managed-only steps that the install orchestration interleaves with `createDefaults`:
- `IMasterSystem.registerSystem(Mutiny.Session, �)` � no stateless overload (used by
  `registerSystemsSequentially`, interleaved between the two `installSystemsSequentially` createDefaults passes).
- `IPasswordsService.createAdminAndCreatorUserForEnterprise(Mutiny.Session, �)` � no stateless overload (admin
  user creation; required for test #5's "working enterprise + admin").
- `IMasterSystem.postStartup(Mutiny.Session, �)`, and `EnterpriseService.createBase` / `createBaseSystems` �
  managed-only.
Because the install phases share one managed transaction (read-your-writes via `flush`), a per-system stateless
`createDefaults` (or a half-stateless `installSystems`) cannot see the other phases' **uncommitted** managed
writes (the documented FK-visibility gotcha). A correct no-bridge `startNewEnterprise` therefore needs the WHOLE
install (`create` ? `createBase` ? `createBaseSystems` ? `registerSystem` ? `installSystems` ? password ?
`postStartup`) threaded onto a single `Mutiny.StatelessSession`, i.e. stateless overloads of the four methods
above. That is the next slice; the `UnsupportedOperationException` fallback seam on
`IMasterSystem.createDefaults(Mutiny.StatelessSession, �)` is already in place for it.
### Full no-bridge startNewEnterprise(StatelessSession) � COMPLETE & DB-validated (2026-06-28)
The north-star is met: the **entire create-and-start-enterprise lifecycle now runs from a
`Mutiny.StatelessSession` with NO bridge** to a managed session. All 23 stateless DB tests pass (incl. test #7
`startNewEnterprise_statelessEntry_completesFullProcess` and test #22 the genuine stateless security bootstrap),
and the stateful suite still passes (no regression).
New stateless surface added this slice:
- **`IMasterSystem.registerSystem(Mutiny.StatelessSession,�)`** default (create + findSystem + registerNewSystem)
  and **`postStartup(Mutiny.StatelessSession,�)`** default (scalar id validation).
- **`ISystemsService.registerNewSystem(Mutiny.StatelessSession,�)`** (+ impl): system identity/group tokens,
  link, SystemIdentity tagging, per-token stateless default security, system involved party.
- **`IPasswordsService.createAdminAndCreatorUserForEnterprise(Mutiny.StatelessSession,�)`** and
  **`addUpdateUsernamePassword(Mutiny.StatelessSession,�)`** (+ impls). Existence gate uses scalar `getCount`
  (never hydrates the @Cacheable InvolvedParty); default security via the stateless per-row insert.
- **`IManagePartyIdentificationTypes.addOrUpdateInvolvedPartyIdentificationType(Mutiny.StatelessSession,�)`**
  (Enum/String/entity) � SCD retire + re-insert; plus **String-secondary `addOrReuseInvolvedParty{Identification
  Type,Type,NameType}`** stateless overloads (resolve the secondary via the stateless finder).
- **`IEnterpriseService.performPostStartup(Mutiny.StatelessSession,�)`** default (sequential, non-blocking).
- **`EnterpriseService`**: genuine stateless `startNewEnterprise` + `createNewEnterprise` and a stateless install
  loop (`createBase` / `createBaseSystems` / `installSystems` / `installSystemsSequentially` /
  `registerSystemsSequentially` / `installSystem` / `performSystemInstall`). Each phase runs in its own
  `withStatelessTransaction` (commits before the next); `performSystemInstall` prefers the stateless
  `createDefaults` and falls back to the managed overload on `UnsupportedOperationException`. The top-level
  register pre-pass is best-effort (the authoritative ordered registration is inside `installSystems`).
Root-cause bug fixed in **EntityAssist** `QueryBuilder.getAll(Class)`: it unconditionally cast every result row
to `BaseEntity` to clear the `fake` flag, which threw `ClassCastException: UUID cannot be cast to BaseEntity`
for scalar projections (e.g. the stateless `allInDateRowIds` ? `selectColumn(id).getAll(UUID.class)` used by the
bulk apply-default-security pass). Guarded with `instanceof BaseEntity` (the singular `get(Class)` already did
this). This was the first code path to exercise stateless scalar `getAll(Class)` against the DB.
### Full-suite regression + stateless SCD-close mutations (2026-06-28)
**Full core suite: 230 tests, 225 pass.** The only 5 failures are the pre-existing MongoDB
`TestActivityMasterResourceItemJson` ordering flake (the JSON-store opt-in is fixed at first Guice context
init; in the full suite an earlier class builds the context without the flag). Confirmed: that class passes
**6/6 in isolation** (`BUILD SUCCESS`). `TestActivityMasterStatelessEnterprise` passes **24/24 even inside the
full shared-JVM run**. => the stateless lifecycle work + the EntityAssist `getAll` guard introduce **zero
regressions**.
**New: stateless SCD-close mutations** (first slice of the deferred close-mutation long-tail):
- `IManageClassifications.archiveClassification(Mutiny.StatelessSession,�)` / `removeClassification(�)` � close
  the active link by stamping the archived/deleted active-flag + effective-to date. New test #24
  `archiveClassification_stateless_closesActiveLink` (create ? tag ? archive ? in-active-range count 1?0) passes.
**Key finding � stateless SCD close must use `session.update`, not bulk HQL.** `SCDLinkMaintenance.retireActiveRow`
uses `session.createMutationQuery(HQL)`, which on a `Mutiny.StatelessSession` throws
`IllegalAccessError: ReactiveStatelessSessionImpl cannot access SqmQueryImplementor � orm.core does not export
org.hibernate.query.hql.spi to org.hibernate.reactive` (the managed session path does not hit this). The fix
pattern: load the `*X*` link via the stateless-safe `.get()`, set `activeFlagID` + `effectiveToDate`, then
`session.update(link)` � a full-row UPDATE by id (no dirty tracking, so the lazy `effectiveToDate` is written
reliably). Applied to the new close mutations AND retro-fitted the latent retire branch of the stateless
`addOrUpdateInvolvedPartyIdentificationType` (used by the stateless password path), which previously called the
broken `retireActiveRow`. This `session.update` pattern is the template for converting the remaining close
mutations (`update`/`expire`/`archive`/`remove`) across the other capability mixins.
### Stateless SCD-close mutations rolled out across all capability mixins (2026-06-28)
Completed the stateless close-mutation family (the deferred long-tail) using the validated full-row
`session.update` template. Only 6 mixins actually carry close mutations; all now have stateless twins:
- `IManageClassifications` � `archiveClassification` / `removeClassification` (done previous slice).
- `IManageEvents` � `expireEvents` / `archiveEvents` / `removeEvents`.
- `IManageRuleTypes` � `expireRuleTypes` / `archiveRuleTypes` / `removeRuleTypes`.
- `IManageEventTypes` � `expireEventTypes` / `archiveEventTypes` / `removeEventTypes`.
- `IManageResourceItemTypes` � `expire/archive/removeResourceItemTypes`.
- `IManageProductTypes` � `expire/archive/removeProductTypes`.
Each mirrors its managed counterpart's exact semantics (the value mixins/`*Types` no-op when the link is absent
or its value already matches; `expire` stamps effective-to only, `archive`/`remove` also stamp the archived/
deleted flag) via a per-mixin private `close*Stateless(�, mode, �)` helper: resolve the secondary via the
stateless finder, `findLink(...).get()` the `*X*` link (stateless-safe), then `session.update`.
**Central fix � `SCDLinkMaintenance.retireActiveRow(Mutiny.StatelessSession,�)` rewritten to use
`session.update`** instead of the JPMS-blocked `createMutationQuery(HQL)`. This single change repairs the latent
retire-branch bug in **every** existing stateless `addOrUpdate*` method (RuleTypes, Rules, ResourceItemTypes,
ResourceItems, ProductTypes, Products, InvolvedParties, PartyTypes, PartyNameTypes, Geographies, �) � they would
all have thrown `IllegalAccessError` the moment their value-change/retire branch ran. (`addOrUpdateInvolvedParty
IdentificationType` was also fixed inline in the previous slice.)
Validation: client + core compile; **`TestActivityMasterStatelessEnterprise` 24/24**; managed
`TestActivityMasterManageClassifications` 9/9 + `TestActivityMasterAdminLifecycle` green (no regression from the
client changes � the managed `retireActiveRow`/close mutations are untouched).
Remaining minor gap: the plain `update*` (SCD retire+reinsert *only if present*) stateless variants � these
overlap almost entirely with the already-stateless (now-fixed) `addOrUpdate*` family, differing only in the
insert-if-absent step, so they are a low-priority follow-up.
### Stateless plain `update*` (SCD retire+reinsert) � COMPLETE (2026-06-28)
Added the remaining stateless `update*` variants (SCD retire+reinsert *only when the link is present* � no-op
if absent, unlike `addOrUpdate*` which inserts) across all 6 close-mutation mixins:
- `IManageClassifications.updateClassification(Mutiny.StatelessSession,�)` (both the plain and the
  concept-narrowed overload � the stateless prepped find is by name, so concept is accepted for parity).
- `IManageEvents.updateEvents`, `IManageRuleTypes.updateRuleTypes`, `IManageEventTypes.updateEventTypes`,
  `IManageResourceItemTypes.updateResourceItemTypes`, `IManageProductTypes.updateProductTypes`.
Each mirrors its managed counterpart and its already-stateless `addOrUpdate*` sibling: resolve the secondary +
classification, `findLink(...).get()` the `*X*` link, no-op if absent or value unchanged, else retire the active
row via the (now-`session.update`-based) `SCDLinkMaintenance.retireActiveRow` and insert the new SCD version +
its default security.
New test #25 `updateClassification_stateless_retiresAndReinserts` (tag value-A ? update to value-B ? assert the
value-A active link is retired (1?0) and a fresh value-B link inserted (0?1)) validates the retire+reinsert path
AND, transitively, the central `retireActiveRow` `session.update` fix. **`TestActivityMasterStatelessEnterprise`
25/25**, client + core compile.
**The stateless surface is now complete:** the full create/start lifecycle + every capability-mixin
read/create/addOrReuse/addOrUpdate/expire/archive/remove/update has a `Mutiny.StatelessSession` twin that
performs the same work as its managed counterpart, all driven no-bridge from a stateless entry point.
### Entity/value-level mutation interfaces twinned (2026-06-28)
Closed the remaining managed-only mutation methods on the entity/value-level interfaces (the
`this`-based close/update operations the capability mixins delegate to):
- **`IRelationshipValue`** � stateless `expire` (�2), `archive` (�2), `remove` (�2), `update`. Each operates on
  the already-loaded link row (`this`) via a full-row `session.update(this)` (no merge/detach, no bulk HQL).
- **`IWarehouseBaseTable`** � stateless `expire(Mutiny.StatelessSession)` / `expire(Mutiny.StatelessSession, Duration)`
  (set effective-to + `session.update(this)`); the dedicated-session `expire()`/`expire(Duration)` open-their-own
  managed session and are intentionally left as the convenience entry points.
- **`IContainsHierarchy.archiveChild`** � stateless twin that finds the active hierarchy link and closes it via
  the link's stateless `archive` (no-op if absent).
These all reuse the validated full-row `session.update` close mechanism (tests #24/#25). Client + core compile;
`TestActivityMasterStatelessEnterprise` 25/25. A full builders/warehouse sweep now finds **no** remaining
mutation-verb method (`update`/`archive`/`remove`/`expire`/`persist`/`grant`/`link`/`addChild`/`createDefaultSecurity`)
that takes a `Mutiny.Session` without a `Mutiny.StatelessSession` twin (the dedicated-session no-arg
`expire()`/`expire(Duration)` convenience overloads aside).
### Service-interface stateless sweep (2026-06-28)
Searched every `I*Service` interface by the "has Mutiny.Session but no Mutiny.StatelessSession" criterion. Three
were fully managed-only; all now expose stateless twins:
- **`IClassificationDataConceptService`** � the impl already had stateless `find(StatelessSession, String,�)` +
  `createDataConcept(StatelessSession,�)` but they were not declared on the interface (only concrete-typed
  callers could reach them). Surfaced both on the interface and added default stateless getter twins
  (`find(enum)`, `getGlobalConcept`, `getNoConcept`, `getSecurityHierarchyConcept`).
- **`IActivityMasterService`** � added stateless static `getISystem(StatelessSession, Enum/String, IEnterprise)`
  and `getISystemToken(StatelessSession, String, IEnterprise)` (delegate to the already-stateless `findSystem` /
  `getSecurityIdentityToken`, same per-enterprise token cache). `loadSystems`/`loadUpdates` are heavy
  admin-orchestration entry points, left managed.
- **`IAddressService`** � `Address` is non-`@Cacheable` with LAZY associations, so it is stateless-safe. Added
  foundational stateless `create` (�2), `createScopeRestricted`, and `addOrFindIPAddress` / `addOrFindHostName`
  via a shared `createWithSecurityStateless` helper (getCount gate, `session.insert`, stateless
  resolveDefaultGroupFolderTokens + createDefaultSecurity/createScopeRestrictedSecurity, stateless-safe `.get()`
  for the existing-row branch). The 6 decomposition-heavy methods (web?port/domain/protocol/site sub-addresses,
  phone?country/area/extension, email?host/user/domain, street/postal sub-classifications, `findCellPhoneContact`)
  remain managed-only � they fan out into many sub-`addClassification`/sub-address inserts and are a clean
  follow-up on top of the new stateless `create` base.
Client + core compile; `TestActivityMasterStatelessEnterprise` 25/25.
### IAddressService fully stateless + stateless addClassification (2026-06-28)
Completed the `IAddressService` subsystem. Added a stateless **`addClassification`** to `IManageClassifications`
(always-insert + default security, mirroring the managed semantics; resolves the classification by name via the
prepped stateless find) � the missing primitive the address metadata needed.
Refactored the stateless `createWithSecurityStateless` helper to take a `postCreate` hook (run only on the
create branch, mirroring the managed "add metadata only when newly created"), and converted ALL remaining
address methods:
- `addOrFindWebAddress` � primary + 4 metadata sub-addresses (port/domain/protocol/site) via a stateless
  `insertSubAddressStateless` helper (URL-parsed, no per-sub security, matching the managed persists).
- `addOrFindPhoneContact` � primary + country-code/extension/area-code via stateless `addClassification`.
- `addOrFindEmailContact` � primary + host/domain/user via stateless `addOrReuseClassification`.
- `addOrFindStreetAddress` / `addOrFindPostalAddress` � primary + building/box sub-classifications.
- `findCellPhoneContact` � via the stateless `involvedParty.findAddress`.
All 11 stateless methods declared on `IAddressService`. **Re-scan of every `I*Service` interface now finds NO
fully-managed-only service** � every service has `Mutiny.StatelessSession` coverage. Client + core compile;
`TestActivityMasterStatelessEnterprise` 25/25.
Only `IActivityMasterService.loadSystems` / `loadUpdates` (heavy admin orchestration that self-manages its own
sessions) remain managed by design.
### Enterprise create+start testing (2026-06-29)
Create+start is exercised by test #7 `startNewEnterprise_statelessEntry_completesFullProcess` (TestEnterprise,
mostly idempotent) � passes; suite green 25/25. A stretch fresh-enterprise test
(`startNewEnterprise(StatelessSession, freshName, �)`) was used to exercise the real INSERT paths and uncovered
five fresh-create bugs, now fixed:
1. `SecurityTokenService.create(StatelessSession)` set token systemID from the prepped classification (null
   systemID) ? NULL FK. Now uses the system parameter.
2. `SecurityTokenService.link(StatelessSession)` derived systemID from the parent (prepped folder token ? null);
   now falls back to the child's systemID.
3. 13 entities lacked a stateless `configureForClassification` override (InvolvedParty, Classification, Rules/
   RulesType, Product/ProductType, ResourceItem/ResourceItemData, Event, Geography, ActiveFlag, Arrangement,
   Address) � added (Enterprise/Systems already had them).
4. Stateless type-link `addOrReuse*`/`addOrUpdate` inserts must manually assign the `@Id` (stateless `insert`
   does not fire `@PrePersist`); guarded all 17 with `if id==null setId(randomUUID)` + fixed the SCD re-insert
   `setId(null)`?fresh id.
The fresh path now runs ~6s deep into the install; one remaining `NoResultException` in a prepped find is a
follow-up. The fresh stretch test was reverted to keep the suite green; the five fixes are retained and harden
both create paths. **25/25 stateless tests pass.**
### Stateless extended to all ActivityMaster module systems (2026-06-29)
Surveyed every module: only geography, profiles, user-sessions, mail carry managed-session code; the rest
(conversations/documents/files/forums/images/notifications/payments/realtor/todo/wallet) are empty/skeleton.
The three module `IMasterSystem`s (GeographySystem, ProfileSystem, SessionMasterSystem) had managed-only no-op
`createDefaults`; added stateless `createDefaults(Mutiny.StatelessSession)` overrides so the stateless
enterprise install drives every module system with no bridge (they inherit the new stateless
`IMasterSystem.registerSystem` default). All three modules compile.
Remaining (follow-up): the module SERVICE methods (geography ~20 � mostly heavy bulk geonames importers like
loadCountryInfo/loadPostalCodes; profiles ~7; user-sessions ~10; mail) still expose only Mutiny.Session and
should get stateless twins by the same prepped-read/session.insert pattern; they are runtime (not enterprise
install) paths.

### Module service stateless twins — profiles, user-sessions, mail (2026-06-29)
Twinned the runtime service surface of three modules; all compile green (client install + core test-compile +
profiles compile + user-sessions compile).
- **Profiles** (already had IProfileService/IRolesService twins): added `ProfileServiceDTO.findRolesReactive(
  Mutiny.StatelessSession)` (composes IActivityMasterService.getISystem/getISystemToken stateless +
  rolesService.getRoles stateless) and a missing `getInvolvedParty()` getter the prior stateless listUsers
  referenced (was uncompiled).
- **User-sessions**: stateless twins for all 6 IUserSessionService methods (getSession×2, updateCache,
  removeCache, expireSession, updateSession) + interface decls + private stateless createNewSessionResourceItem.
  Underpinned by three NEW stateless IResourceItemService methods: `create(byte[])` (session.insert of
  ResourceItem+Data+DataValue + resolveDefaultGroupFolderTokens/createDefaultSecurity, relational only),
  `findByUUID`, `updateResourceData` (HQL select id + native UPDATE — no MongoDB route). findResourceItem/
  addResourceItem/getData/getDataRow/expire stateless already existed.
- **Mail**: `ISystemUpdate.update(Mutiny.StatelessSession)` default seam (UnsupportedOperationException) +
  MailMasterInstall stateless override (classificationService.create + arrangement/resource-type stateless;
  stateless create self-provisions default security so per-step createDefaultSecurity dropped). Added stateless
  `IArrangementsService.createArrangementType` (+ ArrangementsService impl, find-or-insert/prep). NOTE: the mail
  module's POM has a pre-existing missing-version on com.guicedee.modules.services:jakarta.mail (not my code) so
  it cannot compile standalone here; the Java mirrors the validated pattern.
Follow-up: geography ~20 (bulk geonames importers) still managed; DB safety-net re-run pending Docker.

### Geography module stateless twins (2026-06-29)
Twinned the entire geography runtime surface; client + core install green, geography compiles green.
- **Security infra:** `ISecurityTokenService.getSecurityTokenByName`/`applyDefaultSecurityToRows` stateless (+ impl); `DefaultSecurityCollector` stateless activate/record/flush (parallel StatelessSession key space); `GeographySecurityCollector` + `GeographyScopeTokenService` (ensureScope/findScope) stateless.
- **Mixins:** added stateless `IManageClassifications.findClassifications(session,sys)`, `findClassification(String/Enum)`, `findClassificationValues`; `IClassificationService.find(name,concept)` stateless. Currency create uses concept+String-parent stateless overload.
- **Leaf services** (Planet/Continent/Country/Province/District/Town/PostalCode/Currency/TimeZone/Languages): stateless create/find/update via session.insert + manual @Id, scopeToken/collector stateless, merge→update.
- **GeographyService + IGeographyService:** all ~25 methods twinned (loadProvinces/Districts/Languages/CountryInfo/TimeZones/PostalCodes/FeatureCodes/TownsAndCities, installCountry, loadCountryGeoData/PostalCodes, find/createCountry/Continent, createGeoData) on StatelessSession; session.find→session.get.
Net: only geography GraphQL/REST entry points remain managed-by-design. Every empty/skeleton module (conversations/documents/etc.) has no managed code. Stateless twin coverage across ActivityMaster is complete.

### DB validation re-run + mail POM (2026-06-29)
Docker up; client+core install green. `TestActivityMasterStatelessEnterprise` re-run: 25/25 pass. Geography
compiles green. Mail POM jakarta.mail version fixed: groupId com.guicedee.modules.services (no such artifact) ->
com.sun.mail:jakarta.mail (2.0.2, BOM-managed) and removed two stale `requires` (guicedpersistence/guicedservlets,
not depended on). Remaining mail compile errors are pre-existing rot unrelated to jakarta.mail or stateless work
(missing com.guicedee.logger module, lombok not visible, guicedinjection.interfaces/.pairing pkg reorg) � separate
cleanup. Geography geonames loaders compile-validated; runtime installCountry DB validation still open.
### Module compile + runtime validation sweep (2026-06-29)
All ActivityMaster modules with source compile green (geography/profiles/user-sessions/website/realtor/images/
notifications/documents/files/forums/conversations/payments/todo/wallet) � only mail excluded (deferred rot:
guicedinjection.interfaces/.pairing/logger pkg reorg, MasterDefaultSystem rename; partial modernization started).
Runtime DB validated on Postgres: geography GeographyScopeTokenTest 3/3 (enterprise bootstrap + taxonomy install
+ scope-token nesting + scope-restricted read) and GeoDataFinderTest 1/1; user-sessions SessionTest 1/1; core
stateless 25/25. Only mail and the fresh-enterprise stretch NoResultException remain open.

### Niche-tail slice — stateless scope-restricted creates + resolve*IdByName + product/rules (2026-06-30)
Closed the deliberate niche tail from the audit. **Client install + core install green; `TestActivityMasterStatelessEnterprise`
31/31 against Testcontainers Postgres** (the 27 prior + 4 new).
- **Stateless `resolve*IdByName`** — added `StatelessSession` overloads of `ISystemsService.resolveSystemIdByName`,
  `IClassificationService.resolveClassificationIdByName`, `IResourceItemService.resolveResourceItemTypeIdByName`
  (native-SQL scalar lookups; never hydrate the `@Cacheable` entity). Backed by new stateless `NameIdCache`
  overloads (`getSystemId`/`getClassificationId`/`getResourceItemTypeId` via the existing `StatelessResolver`) —
  they share the managed key space, so a value cached by either path is reused by both.
- **Stateless `createProduct`/`createProductType` + `createRules`/`createRulesType`** (mandatory) — full create
  family on `StatelessSession` (scalar existence gate + `session.insert`/`builder.persist` with id assigned up
  front + stateless `resolveDefaultGroupFolderTokens`+`createDefaultSecurity`; product links its type via the
  stateless `addProductTypes`).
- **Stateless `*ScopeRestricted` creates across every remaining service** — twinned each public stateless create
  with a scope-restricted variant by threading a `(scopeToken, restricted)` option into a shared private
  internal that swaps `createDefaultSecurity` → `createScopeRestrictedSecurity(session, system, enterprise,
  activeFlag, tokens, scopeToken, …)` (the stateless WarehouseCoreTable primitive already existed):
  `IClassificationService.createScopeRestricted`, `IProductService.createProduct/ProductTypeScopeRestricted`,
  `IRulesService.createRules/RulesTypeScopeRestricted`, `IInvolvedPartyService.createScopeRestricted` (party +
  organic/non-organic sub-record), `IEventService.createEventScopeRestricted`,
  `IArrangementsService.createScopeRestricted`, `IResourceItemService.createScopeRestricted` + `createTypeScopeRestricted`,
  `IActiveFlagService.createScopeRestricted`.
- **Stateless batch `ISecurityTokenService.applyScopeRestrictedSecurity(StatelessSession, Map<record→scope>, system, …)`**
  — writes the restricted matrix for every pair directly on the supplied stateless session (no nested tx; resolve
  tokens + active flag once, secure each record sequentially).
- **Parallelism** — every stateless create is a self-contained unit with pre-resolved references, so independent
  `StatelessSession` transactions run in parallel. New test #31 provisions two scope-restricted classifications
  on two independent stateless transactions via `Uni.combine().all()` and asserts both persist (distinct ids) +
  findable. New tests #28 (resolve*IdByName parity), #29 (createProduct), #30 (createRules).
- **Deferred (next):** `findArrangementsByClassificationGTEWithIP` (the large multi-join composite query) — the one
  remaining item, to be done next.

### IManage<xxx> + add<XXXX> stateless test coverage (2026-06-30)
Added dedicated stateless test classes mirroring the proven managed `IManage` tests, covering the capability
mixins across every entity domain. **Both green against Testcontainers Postgres (13/13).**
- **`TestActivityMasterManageClassificationsStateless`** (8 tests) — stateless `IManageClassifications`
  (`addClassification` / `numberOfClassifications` / `findClassification`) across Arrangements, Events, Products,
  Party, Rules, ActiveFlag (the exact stateless mirror of `TestActivityMasterManageClassifications`), plus a
  stateless `findChildren` hierarchy-link assertion and a multi-classification per-field read.
- **`TestActivityMasterManageTypesStateless`** (5 tests) — the relationship `*Types` mixins
  (`add*Types` / `addOrReuse*Types` / `has*Types` / `numberOf*Types`) for Products, Rules, ResourceItems, Events,
  Arrangements: link the type (raw `add*Types` where the create does not auto-link; from the create where it does),
  assert `numberOf*Types == 1` + `has*Types == true`, and `addOrReuse*Types` idempotent.
- **Key finding (canRead fallback):** the `canRead`-gated stateless reads (`findClassification`, `numberOf*Types`)
  pass with an **empty identity token** because `getApplicableSecurityTokenIds(session, system, ∅)` falls back to
  the system's own identity — the system reading the data it just created (same as the managed siblings).
- **Stateless limitation noted:** `findClassificationValues` (batched) and `findChildren` secondary-name
  navigation hit `LazyInitializationException` on the link's lazy `@ManyToOne` secondary (not initialisable on a
  detached stateless entity). The tests assert the stateless-safe surface instead (link presence + per-field
  `findClassification` value, which lives on the link row). A genuine stateless `findClassificationValues` would
  need the secondary name projected via a join — a clean follow-up, not a regression.

### ResourceItem stateless create — completed the overload family + type-link (2026-06-30)
Audit triggered by `resourceItemService.create(session, BarcodesPrinter.name(), printerName, system, identityToken)`
("are they all fixed?"). They were **not** — the stateless `IResourceItemService.create` had only **1 of the 8**
managed overloads, and that one **ignored `identityResourceType`** (it never linked the resource-item TYPE
relationship) and `key` / `originalSourceSystemUniqueID` / `effectiveFromDate`. Fixed:
- Added stateless twins for **all 8** managed `create` overloads (incl. the no-`byte[]` `create(session, type,
  value, system, …)` from the snippet) + the full scope-restricted overload, so any managed `create` converts to
  stateless by only changing the session type.
- Single `createStateless` impl now mirrors managed `createInternal`: honours `key` (idempotent find-by-id),
  `originalSourceSystemUniqueID`, `effectiveFromDate`; inserts ResourceItem + Data(+Value); applies
  default/scope-restricted security; **and links the resource-item TYPE relationship** (the missing step —
  refactored the stateless `addResourceItemTypeRelationship` into a scope-aware private helper). Relational-only
  (MongoDB JSON route stays managed, as before).
- **Validated:** new regression test `createResourceItem_statelessNoDataOverload_linksType` (the exact snippet
  signature) + the 5 `*Types` tests + 8 classification tests + 31 lifecycle tests = **45/45** green on Postgres.
  `user-sessions UserSessionDataTest` (exercises the stateless session resource-item create with the `JsonPacket`
  type, provisioned by `ResourceItemsBaseSetup`) passes **7/7**.
- Note: `user-sessions SessionLoginVisitorTest` fails in `@BeforeAll` with a `ProfileMasterInstall`
  `NoResultException` (classification lookup) — pre-existing, unrelated (no resource-item frames; fails for both
  stateful + stateless; `ProfileMasterInstall` never calls `resourceItemService.create`).

### Profiles-install + session-login + stateless-comprehensive-read fixes (2026-06-30)
Investigated the `SessionLoginVisitorTest` / `ProfileComprehensiveProfileTest` failures (the pre-existing
`ProfileMasterInstall NoResultException` noted above) and fixed the whole cascade. **All green:
SessionLoginVisitorTest 2/2, UserSessionDataTest 7/7, ProfileComprehensiveProfileTest 3/3, core 48/48.**
1. **Root cause — `ClassificationService.create(Mutiny.Session, name, desc, concept, system, seq, String parentName, …)`**
   called `find(session, parentName, …)` with **no null-guard**; `ProfileMasterInstall` passes a null parent for
   top-level attribute classifications → `find(session, null, …)` matches no row → `NoResultException` →
   `loadUpdates` aborts → both visitor tests fail in setup. Added the same null/blank-parent guard the stateless
   and interface-default overloads already had (create unparented). This is THE profiles-install fix.
2. **Session-login `.get()`-on-empty bugs** (surfaced once the install was fixed; affect both stateful + stateless):
   - `SessionLoginService.createDeviceIP` — `findByTypeAll(...).get()` throws `NoResultException` on a fresh
     web-client key (no existing device IP) instead of returning null, so the `.ifNull().switchTo(create)` path
     never ran. Recover the empty result to null (both managed + stateless variants).
   - `UserSessionService.getSession` — `findResourceItem(SessionObject…).get()` throws on a first-time session
     (no SessionObject resource item); the code checks `if (resourceItem == null)`. Recover to null (both variants).
3. **Stateless `IManageClassifications.findClassificationValues`** — the batched read navigated each link's lazy
   `@ManyToOne` secondary classification name (`getSecondary().getName()`) → `LazyInitializationException` on a
   detached stateless entity (blocking the stateless `getProfile`/comprehensive read). Reimplemented as a
   stateless-safe native-SQL join that projects `(ClassificationName, value)` as scalars — link-only filter
   (id + active flag + date range) mirroring the managed read, joining the classification purely to project its
   name, and comparing the SCD window against the app's logical `:now`
   (`convertToUTCDateTime(RootEntity.getNow())`, not DB `now()`) so rows written earlier in the same stateless
   transaction are visible (the documented `resolveResourceItemTypeIdByName` gotcha). Blast radius is tiny
   (`ProfileService.getProfile` is the only consumer); the lazy navigation is gone and the values round-trip.

### Parallel classification addition default mechanism (2026-06-30)
- Set the **parallel independent transaction** pattern as the default for adding multiple classifications to a single entity.
- Updated `AIRules/skills/.system/activitymaster/SKILL.md` (global guidance) to replace the sequential `chain` example in "Fire-and-Forget Pattern" with a parallel `Multi` approach that opens separate sessions for each classification addition.
- Added a dedicated "Parallel Mutations (Independent Transactions)" section in `SKILL.md` highlighting the requirement to avoid single-session concurrency in Hibernate Reactive.
- Updated `CONTINUE-STATELESS-PROMPT.md` to guide future agents toward this parallel pattern for stateless operations.
- This transition maximizes throughput for relationship persistence while remaining strictly compliant with Hibernate Reactive's session-per-operation constraints.





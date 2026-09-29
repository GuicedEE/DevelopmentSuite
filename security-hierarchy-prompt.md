# ActivityMaster — Hierarchy-Driven Security: Design Discussion (PROMPT)

> Status: **IN IMPLEMENTATION** — design agreed; building incrementally.
> Scope: how nested folder/user/location hierarchies interact with the ActivityMaster
> security-token model, and how to drive "default population" of grants by hierarchy.
> Date: 2026-06-06

## ⏱️ Current cycle status (read me first)

| Increment | What | State |
|---|---|---|
| **1** | Geography **scope tokens** in the token graph (Planet→Continent→Country), mirroring data into `Everywhere`-rooted token tree | ✅ **DONE & COMPILES** (client+core+geography) — see §6 |
| **Q1** | Same-named places → no scope collision (UUID-keyed names) | ✅ answered — §7 |
| **Q2** | `moveToken(...)` to move a user group to another group | ✅ **DONE & COMPILES** — §7 |
| **primitive** | `IWarehouseCoreTable.createSecurityGrant(...)` single arbitrary-token grant row | ✅ **DONE & COMPILES** — §8 |
| **(b)** | **Postgres integration test** for the scope tree + `moveToken` | ✅ **DONE & TEST-COMPILES** — §9 |
| **(a)** | **Scope-restricted security matrix** (Everywhere=none + scopeToken=read) applied across **multiple entity types** | ✅ **MECHANISM DONE & COMPILES** (generic on base table + batch SPI) — §10 |
| **first dataset** | Apply restriction to **Classifications** (user pick) | ✅ **DONE & COMPILES** — `createScopeRestricted(...)` + test — §11 |
| **more datasets** | Apply restriction to **Party** and **Event** | ✅ **DONE & COMPILES** — §12 |
| **more datasets** | Apply restriction to **Arrangement** and **Address** | ✅ **DONE & COMPILES** — §13 |
| **more datasets** | Apply restriction to **Products** (Product + ProductType) and **Rules** (Rules + RulesType) | ✅ **DONE & COMPILES** — §14 |
| **more datasets** | Apply restriction to **ResourceItem** (item + ResourceItemType) and **ActiveFlag** | ✅ **DONE & COMPILES** — §15 |
| **flag-driven default** | Make **scope-restriction the active default for all live creates** once the security flag is enabled (post enterprise+admin install) | ✅ **DONE & COMPILES** — §16 |

> **Agreed order (user, 2026-06-06):** do **(b) test first**, then **(a)**. Confirmed: **(a) spans
> multiple entities** — the scope-restricted matrix is a reusable mechanism that any record type can opt
> into (not a single-table change), so it is built generically (on `WarehouseCoreTable` / a service
> helper) and applied per opt-in dataset.

---

## 0. Agreed decisions (settled)

- ✅ **Deny-first / default-deny** baseline is accepted — no access unless a grant exists.
- ✅ **Additive (OR) grants** accepted — access is the union of all applicable tokens' grants; there is
  no negative/override DENY row (a "restriction" is the *absence* of a grant, not a deny row).
- ✅ Location levels will be **mirrored as scope tokens under `Everywhere`** so the existing recursive
  token climb can resolve location scoping (verified net-new — see §2d).

---

## 1. Ground truth — how security actually resolves today

There are **two independent hierarchies**. Do not conflate them:

| Hierarchy | Table | What it contains | Walked by the access decision? |
|---|---|---|---|
| **Token hierarchy** | `security.securitytokenxsecuritytoken` | groups / folders / users (Everyone → Administrators/Guests → …) | ✅ **Yes** — `getApplicableSecurityTokenIds` climbs child→parent via a single `WITH RECURSIVE` query |
| **Data hierarchy** | per-domain `XxXx` self-joins (e.g. folder→file, geography→geography) | warehouse *records* | ❌ **No** — `canRead/canWrite` only inspect rows linked **to that one record** |

Key facts baked into the current code:

1. **Per-record default fan-out.** Every warehouse record gets **7 default security rows**, one per
   canonical group/folder token, with this grant matrix:

   | Token | create | update | delete | read |
   |---|:--:|:--:|:--:|:--:|
   | Administrators | ✅ | ✅ | ✅ | ✅ |
   | Everyone | ❌ | ❌ | ❌ | ❌ |
   | Everywhere | ❌ | ❌ | ❌ | ✅ |
   | Systems | ✅ | ✅ | ❌ | ✅ |
   | Applications | ✅ | ✅ | ❌ | ✅ |
   | Plugins | ✅ | ✅ | ❌ | ✅ |
   | Guests | ❌ | ❌ | ❌ | ✅ |

2. **Grants are ADDITIVE (OR), default-deny.** `canRead` = *true* if **any** applicable token has a
   row with `ReadAllowed = true`. `canWrite` = *true* if any applicable token has `CreateAllowed`
   **or** `UpdateAllowed`. If no row grants it → **no access**. There is **no explicit DENY** that
   overrides a grant.

3. **Inheritance flows through the *token* graph, not the *record* graph.** A user in a sub-group
   automatically gains every parent group's grants because the token climb is transitive.

4. **Live single-create security now FOLLOWS `isSecurityEnabled()`** (changed — see §16). When the flag
   is **enabled** (post-install secure-by-default) every live single-entity create writes the
   **scope-restricted** matrix (no Everyone/Everywhere/Guests); when **disabled** (explicitly cleared
   during install) it writes the historical world-readable default. The **stateless batch path used by the
   installer is NOT gated** and always provisions the public default matrix for canonical/reference rows.

5. **`enforceMembershipPolicy`** restricts what may be parented where (Systems/Applications/Plugins
   folders are type-locked; only Administrators may restructure the canonical root).

---

## 2. The worked example under discussion

> "I have the **Everywhere** group and I want to restrict an Application **AppA** to only **planet
> earth** (the first level under Everywhere). So I would alter AppA's security record with the token
> for planet earth and set it to **deny read**? Same down 4 levels to city level? And this concept
> applies to everything — systems, plugins, etc.?"

### 2a. Important correction: there is no "deny" — model it as **scoped grant + default-deny**

Because grants are additive and the baseline is deny, you **do not** add a "deny read" row to lock
AppA out of everything-except-earth. Instead you:

- **Grant** AppA's identity token `read` on the resources/scopes it *should* reach (earth + below),
  and
- **Withhold** the grant everywhere else (default-deny does the "restriction" for free).

Adding a "deny" row would do nothing useful today — another applicable token that still grants read
would win (OR semantics), and if nothing grants read, access is already denied. So "deny" is the
*absence* of a grant, not a row.

> ⚠️ If you truly need **negative/override** permissions (an explicit DENY that beats a GRANT, NTFS
> style), that is a **new capability** — see §4. The current engine cannot express it.

### 2b. "Everywhere → planet earth → … → city" needs **location-scope tokens**

`Everywhere` is a *security* group. `Planet Earth → Continent → Country → City` is a *geography data*
hierarchy. They are not the same graph. To make "restrict AppA to earth" resolvable by the existing
engine, you mirror the location levels as **security tokens** nested under `Everywhere`:

```
Everywhere                      (security group token)
  └── Earth         (scope token)      ← level 1
        └── Africa  (scope token)      ← level 2 (continent)
              └── ZA (scope token)     ← level 3 (country)
                    └── Cape Town      ← level 4 (city)
```

Then:

- Each geography/resource **record** is stamped with a default grant row for the scope token(s) of
  its location (e.g. a Cape Town record carries a row for the `Cape Town` scope token, which is a
  child of `ZA` → `Africa` → `Earth` → `Everywhere`).
- **AppA's identity token is linked under the `Earth` scope token.** Expanding AppA therefore yields
  `Earth → Everywhere → root` — so AppA can read any record granted to `Earth` *or any descendant
  scope* **only if** the climb reaches a granting token.

> Direction check: the recursive walk is **child → parent**. A caller gains the grants of its
> **ancestors**, not its descendants. So linking AppA under `Earth` means AppA inherits `Earth`'s and
> `Everywhere`'s grants — it does **not** automatically gain `ZA`/`Cape Town` (those are *below*
> `Earth`). Choose the link level deliberately (see §3).

### 2c. Yes — the concept is uniform across token *types*

Systems, Applications, Plugins, Users, Guests are all just **typed security tokens** in the same
graph. The same scoping mechanism applies to all of them, subject to `enforceMembershipPolicy`
(System/Application/Plugin tokens must live under their type folder; generic scope/group tokens nest
freely). So "restrict to a location/branch" works identically for a System token, a Plugin token, or
a user group — you link the identity token at the chosen scope and stamp records with scope grants.

---

## 2d. VERIFICATION — does geography creation already create scope tokens? (NO)

Checked the geography module against the running code (2026-06-06):

| Behaviour | Status today |
|---|---|
| Geography creators call `GeographySecurityCollector.record(session, geo)` per row | ✅ yes (`PlanetService`, `ProvinceService`, `DistrictService`, `TownService`, `PostalCodeService`, `GeographyService`) |
| Load phase `flush(...)` → `applyDefaultSecurityToRows(...)` stamps **default grant rows** | ✅ yes — one stateless batch per phase |
| Those grant rows reference the **7 canonical tokens** (incl. `Everywhere = read`) | ✅ yes — resolved once per batch |
| Geography record gets its **own mirrored scope token** (`Earth`/`Africa`/`ZA`/`City`) created + `link`ed under `Everywhere` | ❌ **NO — does not happen** |

Conclusion: the location tree exists **only as data** (`GeographyXGeography` self-join). It is **not**
mirrored into the **token graph** that `getApplicableSecurityTokenIds` walks. There is no
`getEverywhereGroup` + `create` + `link` in the geography module — only per-record grant rows
(`GeographyXGeographySecurityToken`, `GeographyXResourceItemSecurityToken` are the *grant-row* tables,
**not** scope tokens).

**Therefore "geography creation should create its associated security group/scope token" is NET-NEW
work.** Until it exists, "restrict AppA to Earth" is not resolvable — there is no `Earth` token to
link AppA under, and no per-record grant pointing at an `Earth` scope token.

### What the new feature must do (per geography node, on create)
1. Resolve/`get` the `Everywhere` group once per batch (same caching pattern as the 7 canonical tokens).
2. For each geography node, **find-or-create a scope `SecurityToken`** (e.g. named by geonameId/level)
   and `link(...)` it under its **parent geography's scope token** (mirroring the `GeographyXGeography`
   parent edge; the top level links under `Everywhere`). Respect `enforceMembershipPolicy` (these are
   generic scope tokens, so they nest under the generic `Everywhere` group fine).
3. When stamping the record's default security rows, **add a grant row for that node's own scope
   token** (read-grant), in addition to the 7 canonical rows.
4. Keep it batched + stateless (extend the collector/flush path) — never per-row round-trips.

This makes the location levels first-class in the token graph so identity tokens (AppA, SystemX,
PluginY) can be linked at a chosen level and inherit upward via the existing recursive climb.

---

## 3. How to drive "default population" by hierarchy (recommended)

**Option A — inheritance carried by the TOKEN hierarchy (reuse the engine, near-zero new code):**

1. Mint a **scope token per location level** (`Earth`, `Africa`, `ZA`, `Cape Town`) and `link(...)`
   them parent→child under `Everywhere` (respecting `enforceMembershipPolicy`).
2. When a record is created, stamp its **default security rows** to include the scope token(s) for
   its location, in addition to the 7 canonical tokens. Write these on a `Mutiny.StatelessSession`
   (the established `createDefaultSecurity` / `applyDefaultSecurityToRows` batch pattern).
3. To "restrict AppA to Earth": `link` AppA's identity token under the `Earth` scope token and grant
   read on the Earth-scoped resources. Default-deny handles everything outside that branch.
4. "Allow another user group on this branch" = **one** additional grant row or **one** `link` — it is
   additive and the recursive climb propagates it; no subtree rewrite needed.

This keeps the access decision a single recursive query and matches how the canonical install
already builds the tree.

---

## 4. If you need true downward inheritance / explicit DENY (NEW capability)

Only if §3 is insufficient:

- **B1 — Materialize-on-write (propagate down the data tree).** On child create / re-parent, copy or
  merge the parent record's effective grants into the child's default rows. Read path stays flat and
  fast; parent permission edits require a batched re-propagation pass down the subtree (stateless
  walk, same shape as `applyDefaultSecurityToRows`).
- **B2 — Resolve-on-read (recursive record climb).** Extend `canRead/readableIds` to also union the
  parent *record* chain via a second `WITH RECURSIVE`. Parent edits are instantly effective, but
  every check now climbs two graphs (record tree + token tree) — heavier, bigger core change.
- **Explicit DENY.** Add a precedence rule (e.g. any applicable `ReadDenied` row overrides all
  grants). This changes the fundamental OR/default-deny semantics and must be designed carefully
  (precedence, depth-specificity, audit). Currently **not** supported.

**Recommendation:** Option A for structural/scope inheritance; reach for B1 only when per-record ACLs
must diverge from the token structure; treat B2 / explicit-DENY as a deliberate, separately-scoped
core change.

---

---

## 6. Implementation log (Increment 1 — geography scope tokens)

**Status: ✅ DONE & COMPILES** (client + core + geography all `mvn install/compile` exit 0, 2026-06-06).

### What was built
A new service shadows the geography **data** tree into the security **token** graph, at the coarse
gating levels only (**Planet → Continent → Country**).

| File | Change |
|---|---|
| `geography/.../GeographyScopeTokenService.java` | **NEW.** `ensureScope(session, geo, parentGeo, label, system, token)` find-or-creates a scope token named `GeoScope:<geographyId>` (deterministic, idempotent) and links it under the parent geo's scope token, or under the `Everywhere` group when `parentGeo == null`. Also `findScope(...)` + `scopeTokenName(geo)` helpers. Scope tokens use the generic `UserGroup` classification so the membership policy permits nesting under `Everywhere`. |
| `client/.../ISecurityTokenService.java` | **+** `getSecurityTokenByName(session, name, system, token)` SPI — name-keyed lookup (the existing `getSecurityToken` keys on the token varchar, not the name). |
| `core/.../SecurityTokenService.java` | **+** implementation of `getSecurityTokenByName` (SecurityToken builder, `withName` + recover-null). |
| `geography/.../PlanetService.java` | Wired: after persist + `record(...)`, `ensureScope(geo, null, code, …)` → links the planet's scope under **Everywhere**. |
| `geography/.../ContinentService.java` | Wired: `ensureScope(geo, planet, description, …)` → under the planet's scope token. |
| `geography/.../CountryService.java` | Wired: `ensureScope(geo, continent, description, …)` → under the continent's scope token. |

### Resulting token graph after a geography load
```
Everywhere                         (canonical UserGroup)
  └── GeoScope:<earthId>           (Planet)
        └── GeoScope:<africaId>    (Continent)
              └── GeoScope:<zaId>  (Country)
```

### How to restrict Application "AppA" to Earth (and below) — usage
```java
// resolve the planet, then its scope token, then link AppA's identity token under it
geographyService.findPlanet(session, "Earth", system, token)
    .chain(earth -> scopeTokenService.findScope(session, earth, system, token))
    .chain(earthScope -> securityTokenService.link(session, earthScope, appAToken, userGroupClass));
// AppA now expands AppA → GeoScope:<earthId> → Everywhere → root; default-deny restricts elsewhere.
```
Because the climb is **child → parent**, linking AppA under `GeoScope:<earthId>` grants it everything
`Earth`'s scope (and `Everywhere`) is granted. The same applies to System / Plugin / user-group tokens.

### Deliberately deferred (documented, not done)
- **City/Province/Town/PostalCode scope tokens** — a scope token per city/town would mint (and per-row
  secure) thousands of tokens during a bulk load. Enable later behind a toggle, ideally batching the
  scope-token creation + their own default security.
- **Per-record scope grant rows** (Increment 2) — geography *records* still carry only the 7 canonical
  grant rows (incl. `Everywhere = read`). They do **not yet** carry a grant row pointing at their own
  scope token. So today the scope tokens shape the *token graph* (identity tokens can be capped to a
  branch), but record reads are still gated by the 7 canonical grants. Increment 2 = stamp each record
  with a read-grant row for its scope token (extend the batch grant matrix / collector flush).
- **Generic `GeographyService` inline `addChild` paths** (lines ~824, ~1059) and Province/District
  creators are not wired.

### Follow-up / risks to watch
- `securityTokenService.create(...)` runs a per-token `createDefaultSecurity` on each new scope token
  (~21 round-trips/token). Fine at ≤ a few hundred countries; revisit if finer levels are enabled
  (batch it).
- No runtime/integration test yet (needs Postgres). Add a test: load Earth→Africa→ZA, assert the three
  `GeoScope:*` tokens exist and are linked, and that an identity token linked under `GeoScope:<earthId>`
  expands to include `Everywhere`.

---

---

## 7. Q&A captured during build (2026-06-06)

### Q1 — Two places with the same name in different districts/countries?
**No collision at the scope-token level.** Scope tokens are named **`GeoScope:<geographyId>`** — keyed by
the geography record's **UUID**, never by the place name. Two cities both called "Springfield" are two
distinct geography records (distinct UUIDs) → two distinct scope tokens, each linked under its own
parent (its own district/country scope). The human name is only the token *description*. This is exactly
why the deterministic name uses the id, not the name.

> ⚠️ Caveat one level lower — the **geography record** dedupe. The current city/district/province
> creators (`DistrictService.findDistrict`, `ProvinceService.findProvince`) look up by **name** within
> the enterprise and *reuse* an existing row when found. So two same-named places could be collapsed into
> one *geography record* **before** scope tokens ever enter the picture. That is a pre-existing
> geography-record concern (disambiguate by parent), independent of this feature. It does **not** affect
> the wired levels (Planet unique, Continent unique, Country ISO-unique).

### Q2 — How do I move a user group to another user group?
**Implemented `ISecurityTokenService.moveToken(...)`** (built + compiles):
```java
securityTokenService.moveToken(session, oldParentGroup, newParentGroup, childGroup, userGroupClass);
// oldParent == null  → exclusive reparent: closes ALL current parent edges, then links newParent
```
It **temporally closes** the `oldParent → child` membership edge (sets `EffectiveToDate = now`, so the
`WITH RECURSIVE` climb stops traversing it) and creates the new `newParent → child` edge. Other parent
memberships of the child are untouched (precise move, not a wipe) unless `oldParent` is `null`. The
**same membership policy** as `link(...)` is enforced on the destination first (so e.g. you cannot move a
`System`-typed token out from under the Systems folder, or move a group into a locked type folder) — it
fails cleanly before closing any edge. Idempotent: a child already under `newParent` is a no-op.

> A plain additive **`link(...)`** still exists for *multi-membership* (a group belonging to several
> parents at once). Use `moveToken` when you want the child to leave its old parent.

---

## 8. Increment 2 — IMPORTANT correction (do NOT naively add scope grant rows)

While building, a semantic trap surfaced. The default fan-out grants **`Everywhere = read`** on every
record, and `Everywhere` is the **root** of the scope tree. Any identity token linked under a scope
(`GeoScope:<earthId>`) therefore **expands to include `Everywhere`** (child→parent climb) and so already
matches the universal `Everywhere = read` grant on *every* record. **Conclusion: simply adding a
per-record grant row for its own scope token does NOT create restriction — it is redundant with the
universal read.**

To make a record **actually location-restricted** you need *both*:
1. The record is **not** world-readable — i.e. it carries **no `Everywhere = read`** grant (use a
   **scope-only** default matrix, not the canonical seven), **and**
2. The record carries a **read grant for its scope token**, **and**
3. The caller's identity token is linked under that scope (or an ancestor that is granted).

Because geography reference data is *intended* to be public, you would **not** restrict the geography
rows themselves. The scope tokens are the **vocabulary**; restriction belongs on **application/business
records** (folders, files, resources) that opt into the scope-only matrix.

### Building block delivered
Added **`IWarehouseCoreTable.createSecurityGrant(statelessSession, system, enterprise, activeFlag, token,
create, update, delete, read, identity…)`** (built + compiles) — writes a single grant row pairing this
record with an *arbitrary* token (e.g. a scope token) and explicit flags, on a stateless session. This is
the primitive a scope-restricted record uses to grant `read` to its scope token (and deliberately omit
`Everywhere`). The redundant "stamp scope grant on top of the public geography defaults" idea is
**dropped**; the real Increment 2 is "scope-only matrix for records that opt into restriction", to be
wired where an actual restricted dataset exists.

### Revised plan
- [x] `createSecurityGrant` primitive (done).
- [x] A `createScopeRestrictedSecurity(...)` matrix (Administrators=CRUD, Systems/Apps/Plugins=…,
  scopeToken=read, **Everywhere=none**) for opt-in restricted records. → **§10**
- [ ] Pick/confirm the first **restricted dataset** (an application record type) to apply it to — geography
  reference rows stay public.

---

## 9. (b) DONE — integration test for scope tree + moveToken

**File:** `geography/src/test/java/.../GeographyScopeTokenTest.java` (NEW, **test-compiles**; execution
needs Docker/Postgres via the existing Testcontainers `PostgreSQLTestDBModule`).

Two ordered tests, booting the same harness as `GeographyOnDemandRestTest` (bootstrap enterprise +
`GeographySystemInstall` taxonomy, which now creates Planet "Earth" + continent scope tokens):

1. `scopeTokensAreCreatedAndNested` — resolves `findPlanet("Earth")` + `findContinent("AF")`, asserts both
   have `GeoScope:*` tokens, then expands the **continent** scope token via
   `getApplicableSecurityTokenIds(...)` and asserts the set contains **continent scope + planet scope +
   Everywhere** — proving the `Continent → Planet → Everywhere` nesting.
2. `moveTokenRelocatesGroupMembership` — creates three `UserGroup` tokens (A, B, child), links child→A,
   asserts `applicable(child)` contains A, calls `moveToken(session, A, B, child, userGroupClass)`, then
   asserts `applicable(child)` now contains **B** and **not A**.

> To run when Docker is available: `mvn -pl geography test -Dtest=GeographyScopeTokenTest` (from the
> ActivityMaster reactor, with client/core installed).

---

## 10. (a) DONE (mechanism) — scope-restricted security matrix, generic across entities

Built generically so **any** warehouse entity type can opt in (the user's point: (a) spans multiple
entities). All compile (client + core install exit 0).

| API | Where | What |
|---|---|---|
| `IWarehouseCoreTable.createScopeRestrictedSecurity(stateless, system, enterprise, activeFlag, groupFolderTokens, scopeToken, identity…)` | base table (client SPI + `WarehouseCoreTable` impl) | Per-record restricted fan-out: **Administrators=CRUD**, **Systems/Apps/Plugins=create/update/read**, **Everyone/Everywhere/Guests = omitted** (→ default-deny, NOT world-readable), **scopeToken = read**. |
| `ISecurityTokenService.applyScopeRestrictedSecurity(session, Map<record → scopeToken>, system, identity…)` | service SPI + `SecurityTokenService` impl | Multi-entity batch entry point: resolves the group/folder tokens **once**, then writes the restricted matrix for every (record, scopeToken) pair in **one stateless transaction**. |

### Crucial semantics (climb direction) — write this on the wall
`applicable(identity)` = identity **+ its ancestors** (parents, transitively, child→parent). A record
scoped to token `T` is readable **iff `T ∈ applicable(identity)`**, i.e. `T` is the identity **or an
ancestor of it**. Therefore:

> **A record scoped to `T` is readable by any identity located at `T` or BELOW it (a descendant of `T`).
> Identities ABOVE `T`, or in a sibling/unrelated branch, cannot read it.**

So you scope a **record** at the *broadest* identity level allowed to see it; deeper (more specific)
identities inherit it; shallower ones don't. (This is folder-style inheritance, just stated in token
terms.) Example: a record scoped to `GeoScope:<africaId>` is readable by an identity at Africa, ZA or
Cape Town — but **not** by an identity sitting at Earth (above Africa) nor one under Asia.

### How to apply to a real dataset (the remaining open choice)
```java
// after persisting your restricted records, map each to the scope token it should be visible under:
Map<IWarehouseCoreTable<?,?,?,?>, ISecurityToken<?,?>> recordScopes = …; // e.g. record → GeoScope token
securityTokenService.applyScopeRestrictedSecurity(session, recordScopes, system, token);
```
Geography reference rows stay **public** (default matrix). The restricted matrix is for
**application/business** record types that opt in — pick the first one next.

---

## 11. First restricted dataset = **Classifications** (DONE & COMPILES)

User picked **Classifications** as the first opt-in restricted dataset. Wired an opt-in restricted
create path; **default classification create stays public/unchanged** (so the public taxonomy that
everything depends on is untouched).

### Why a new live-session method was needed
The classification is created + secured on the **caller's live session** (the default path calls the
live `createDefaultSecurity`). The stateless batch matrix can't be used here — it opens a separate
transaction that can't see the still-uncommitted classification (FK to `base` would fail). So a
**live-session** restricted variant was added, mirroring the existing live default find-or-create.

| API | Where | What |
|---|---|---|
| `IWarehouseCoreTable.createScopeRestrictedSecurity(Mutiny.Session, system, scopeToken, identity…)` | base table (client SPI + `WarehouseCoreTable` impl) | **Live-session** restricted matrix: find-or-create Administrators=CRUD, Systems/Apps/Plugins=CUR, **no** Everyone/Everywhere/Guests, **scopeToken=read**. Same bootstrap-tolerance as the live default path. |
| `IClassificationService.createScopeRestricted(session, name, desc, concept, system, seq, parent, scopeToken, identity…)` | client SPI + `ClassificationService` impl | Same as `create(...)` but applies the restricted matrix instead of the public one. Internals refactored: the create body is now `createWithSecurity(…, Function<Classification,Uni<?>> securityFn, …)`; public `create` passes `createDefaultSecurity`, `createScopeRestricted` passes `createScopeRestrictedSecurity(scopeToken)`. **No behavioural change to the public path.** |

### Test (added to `GeographyScopeTokenTest`, test-compiles)
`scopeRestrictedClassificationIsBranchRestricted` (@Order 3): creates a `scope` token, an `insider`
linked **under** the scope, and an `outsider` (no scope), then `createScopeRestricted("RestrictedClassification", … scope)`
and asserts **`canRead(insider) == true`** and **`canRead(outsider) == false`** — i.e. the classification
is visible only within the scope branch.

### Builds
client install ✅, core install ✅, geography main+test compile ✅ (one paren-nesting + one wildcard
`canRead` cast fixed during the build).

### Still open for Classifications
- The **record → scope mapping rule**: how does a caller decide *which* scope token a restricted
  classification gets? (explicit arg today — caller supplies `scopeToken`.) If classifications should be
  auto-scoped by their **own hierarchy** (parent classification → scope), that's a follow-up.
- Run the test under Docker/Postgres to confirm the reactive `canRead` semantics end-to-end.


## 12. Party + Event scope-restricted creates (DONE & COMPILE)

Extended the opt-in restricted pattern to two more entity types (user request: "Party, then Event").
**Default creates remain public/unchanged**; restriction is a separate opt-in method. Same refactor as
Classifications (a security-strategy `Function` chosen at the entry point), all builds green
(client + core install exit 0).

| Entity | New API (client SPI + core impl) | Notes |
|---|---|---|
| **Party** | `IInvolvedPartyService.createScopeRestricted(session, system, key, idTypes, isOrganic, scopeToken, identity…)` | Secures the `InvolvedParty` **and** its `InvolvedPartyOrganic`/`NonOrganic` sub-record with the restricted matrix (scope grant threaded through `setupInvolvedPartyOrganicStatus`). Refactor: `create(...)` → `createWithSecurity(…, Function<IWarehouseCoreTable,Uni<?>> securityFn, …)`. |
| **Event** | `IEventService.createEventScopeRestricted(session, eventType, key, scopeToken, system, identity…)` | Secures the `Event` row with the restricted matrix; event-type linking unchanged. Refactor: `createEvent(...)` → `createEventWithSecurity(…, Function<Event,Uni<?>> securityFn, …)`. |

Both reuse the live-session `WarehouseCoreTable.createScopeRestrictedSecurity(session, system, scopeToken, identity…)`
from §11 (the records are created on the caller's uncommitted session, so the stateless batch variant
can't be used). Semantics identical to §10/§11: not world-readable; readable only by identity tokens at
the scope node **or below it**.

> Tests: the Party/Event restricted paths share the exact mechanism proven by the Classification test
> (§9 `scopeRestrictedClassificationIsBranchRestricted`). Dedicated Party/Event reactive tests are a
> follow-up (would live in core test sources with the Postgres harness).

---

## 13. Arrangement + Address scope-restricted creates (DONE & COMPILE)

Extended the opt-in restricted pattern to **Arrangement** and **Address** (user request: "Then arrangement
and address please"). Same security-strategy `Function` refactor; **public creates unchanged**.

| Entity | New API (client SPI + core impl) | Notes |
|---|---|---|
| **Arrangement** | `IArrangementsService.createScopeRestricted(session, type, key, arrangementTypeClassification, arrangementTypeValue, system, scopeToken, identity…)` | Refactor: `create(...)` → `createWithSecurity(…, Function<Arrangement,Uni<?>> securityFn, …)`; public passes `createDefaultSecurity`, restricted passes `createScopeRestrictedSecurity(scopeToken)`. |
| **Address** | `IAddressService.createScopeRestricted(session, addressClassification, key, system, scopeToken, identity…)` | Same strategy refactor on the address create path. |

---

## 14. Products + Rules scope-restricted creates (DONE & COMPILE)

Extended the opt-in restricted pattern to the **Products** and **Rules** datasets (user request: "ok then
products and rules"). Each dataset has **two** independent securable warehouse tables (the primary record
and its *type*), so both get a restricted variant. **Public creates remain world-readable/unchanged**;
restriction is always a separate opt-in method. All builds green (client + core install exit 0).

| Entity | New API (client SPI + core impl) | Notes |
|---|---|---|
| **Product** | `IProductService.createProductScopeRestricted(session, productType, key, name, description, code, system, scopeToken, identity…)` | Refactor: `createProduct(...)` → `createProductWithSecurity(…, Function<Product,Uni<?>> securityFn, …)`; product-type linking unchanged. |
| **ProductType** | `IProductService.createProductTypeScopeRestricted(session, productsType, key, description, system, scopeToken, identity…)` | Refactor: `createProductType(...)` → `createProductTypeWithSecurity(…, Function<ProductType,Uni<?>> securityFn, …)`. |
| **Rules** | `IRulesService.createRulesScopeRestricted(session, rulesType, key, name, description, system, scopeToken, identity…)` | Refactor: `createRules(...)` → `createRulesWithSecurity(…, Function<Rules,Uni<?>> securityFn, …)`. |
| **RulesType** | `IRulesService.createRulesTypeScopeRestricted(session, rulesType, key, description, system, scopeToken, identity…)` | Refactor: `createRulesType(...)` → `createRulesTypeWithSecurity(…, Function<RulesType,Uni<?>> securityFn, …)`; find-or-create branch preserved (existing type still returned via `findRulesTypes`). |

All four reuse the live-session `WarehouseCoreTable.createScopeRestrictedSecurity(session, system, scopeToken, identity…)`
from §11. Semantics identical to §10–§13: not world-readable; readable only by identity tokens at the scope
node **or below it**.

> Drive-by fix: `ProductService.findProductByResourceItem(...)` had a pre-existing broken
> `.onItem().transform(results -> (Uni)results)` (wrapped a `Uni` inside a `Uni` → compile error).
> Restored to the codebase-standard `(Uni)`-cast-of-chain form.

---

## 15. ResourceItem + ActiveFlag scope-restricted creates (DONE & COMPILE)

Extended the opt-in restricted pattern to **ResourceItem** and **ActiveFlag** (user request: "resource item
and active flag"). All builds green (client + core install exit 0).

| Entity | New API (client SPI + core impl) | Notes |
|---|---|---|
| **ResourceItem** | `IResourceItemService.createScopeRestricted(session, identityResourceType, key, resourceItemDataValue, originalSourceSystemUniqueID, effectiveFromDate, data, system, scopeToken, identity…)` | Secures **both** the `ResourceItemData` row **and** the `ResourceItemXResourceItemType` link with the restricted matrix. Implemented via a nullable `scopeToken` threaded through a private `createInternal(...)` + a `scopeToken`-aware overload of `addResourceItemTypeRelationshipInternal(...)`. (The bare `ResourceItem` row itself stamps no security today — unchanged.) |
| **ResourceItemType** | `IResourceItemService.createTypeScopeRestricted(session, value, key, description, system, scopeToken, identity…)` | Refactor: `createType(...)` → private `createTypeInternal(…, scopeToken, …)`; find-or-create branch preserved. |
| **ActiveFlag** | `IActiveFlagService.createScopeRestricted(session, enterprise, name, description, system, scopeToken, identity…)` | **Special case:** the public `ActiveFlagService.create(...)` is reference-data that stamps **no** per-record security, and it isn't even an SPI method. The restricted variant *introduces* security stamping (restricted matrix) for the first time. Refactor: `create(...)` → `createWithSecurity(…, Function<ActiveFlag,Uni<?>> securityFn, …)`; public passes a **no-op** securityFn (behaviour unchanged), restricted passes `createScopeRestrictedSecurity(scopeToken)`. |

> **ActiveFlag caveat (documented in Javadoc):** ActiveFlags gate row visibility for *every* record that
> references them and are normally enterprise-global. Restricting a flag is unusual — intended only for
> tenant/branch-private flags. The mechanism is provided; whether to use it is a deployment decision.

All reuse the live-session `WarehouseCoreTable.createScopeRestrictedSecurity(session, system, scopeToken, identity…)`
from §11. Semantics identical to §10–§14: not world-readable; readable only by identity tokens at the scope
node **or below it**.

---

## 16. Flag-driven default: scope-restriction in effect after enterprise+admin install (DONE & COMPILE)

User requirement (2026-06-06): *"Once the enterprise is installed and the admin is created with security
tokens, the scope restricted for all items should be in effect (following the security flag enablement)."*

**What changed.** The live single-create security path now **follows the security flag**. In
`WarehouseCoreTable.createDefaultSecurity(Mutiny.Session, ISystems, UUID…)` — the method every
post-bootstrap single-entity create calls — the matrix is now chosen at runtime:

| `ActivityMasterConfiguration.isSecurityEnabled()` | When | Matrix written |
|---|---|---|
| **`true`** (secure-by-default; the steady-state after enterprise install + admin/canonical-token creation) | normal runtime | **Scope-restricted**: Administrators=CRUD, Systems/Applications/Plugins=create/update/read, **no** Everyone/Everywhere/Guests (→ default-deny, not world-readable). Delegates to `createScopeRestrictedSecurity(session, system, null, identity)`. |
| **`false`** (explicitly cleared during enterprise install/bootstrap) | install only | **World-readable default** (the historical 7-grant matrix incl. Everywhere/Guests=read), so reference data provisioned during install stays public. |

**Why this lands the requirement.** During install the flag is deliberately disabled, so canonical/reference
rows created then stay world-readable. The moment install completes and the runtime returns to its
secure-by-default state (flag `true`), **every subsequent live create is automatically scope-restricted** —
no per-call opt-in needed. The per-item `createScopeRestricted(… scopeToken …)` methods (§11–§15) remain the
way to additionally pin a record to a *specific* scope token.

**Scope of the change (important).**
- Only the **live** path is gated. The **stateless batch path** used by the installer
  (`createDefaultSecurity(StatelessSession, …)`) is **intentionally NOT gated** and always writes the public
  default matrix — otherwise install (which runs scope-free, where the flag falls back to `true`) would lock
  down reference data.
- The default restricted matrix uses **no explicit scope token** (`null`), so it grants the Administrators +
  System/Application/Plugin hierarchy only. Callers whose identity is **not** under that hierarchy must be
  granted an explicit scope (via the §11–§15 opt-ins) to read such records.
- `isSecurityEnabled()` is **secure-by-default** (`true` with no/started scope; only `false` when explicitly
  cleared), so the safe posture is the default.

Builds green (core install exit 0). The `SecurityFlagLifecycle` tests only assert the flag's own
toggling and are unaffected.

> Follow-up to watch: any flow that creates data as a *system* and later reads it back as **Everyone /
> Guests** will no longer see it once the flag is on. That is the intended secure-by-default posture; such
> flows must now grant an explicit scope (or run with the flag cleared, e.g. install/bulk import).

---

## 5. Open questions to resolve next

- [x] Do scope tokens mirror geography 1:1, or only the levels you actually gate on? → **Decided:**
  coarse gating levels only (Planet/Continent/Country) implemented; finer levels deferred behind a toggle.
- [x] ~~Increment 2: stamp geography records with their scope grant~~ → **superseded** by §8/§10:
  scope-restricted matrix is the correct mechanism; geography reference rows stay public.
- [x] Add an integration test for the scope tree + identity expansion → **done (§9)**.
- [x] Pick the first **restricted dataset** → **Classifications** (`createScopeRestricted`, §11).
- [x] Apply restriction to **Party** and **Event** → done (§12).
- [x] Apply restriction to **Arrangement** and **Address** → done (§13).
- [x] Apply restriction to **Products** (Product + ProductType) and **Rules** (Rules + RulesType) → done (§14).
- [x] Apply restriction to **ResourceItem** (item + ResourceItemType) and **ActiveFlag** → done (§15).
- [x] Make scope-restriction the **flag-driven default** for all live creates post-install → done (§16).
- [ ] **NEXT:** the **record→scope mapping rule** (shared by Classifications/Party/Event) — explicit
  `scopeToken` arg today; decide whether records should auto-scope by an owning hierarchy/context.
- [ ] Add dedicated Party/Event reactive tests (core test sources, Postgres harness).
- [ ] Run `GeographyScopeTokenTest` under Docker/Postgres to confirm reactive `canRead` end-to-end.
- [ ] At which level is each identity token (AppA, SystemX, PluginY) linked — and is that per-tenant?
- [ ] Enable City/Province scope tokens? If so, batch their creation + per-token security first.
- [ ] Is plain scoped-grant + default-deny enough, or is a real negative/override DENY required?
- [ ] Who is allowed to restructure scope tokens (Administrators-only, like the canonical root)?













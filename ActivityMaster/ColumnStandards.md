# Column Standards

This document defines the column naming conventions, data types, and constraints used across all ActivityMaster FSDM entities.

---

## Inheritance Hierarchy

All entities inherit columns through a layered abstract class hierarchy:

```
SCDEntity
  └── WarehouseBaseTable
        └── WarehouseCoreTable
              └── WarehouseTable
                    └── WarehouseSCDTable
                          ├── (Primary Entities)
                          ├── WarehouseRelationshipTable
                          └── WarehouseSecurityTable
```

---

## SCD (Slowly Changing Dimension) Columns — `SCDEntity`

Every entity in the system inherits these four temporal tracking columns.

| Column Name | DB Column | Type | Nullable | Default | Purpose |
|---|---|---|---|---|---|
| `effectiveFromDate` | `EffectiveFromDate` | `OffsetDateTime` | No | Now (UTC) | When the record becomes effective |
| `effectiveToDate` | `EffectiveToDate` | `OffsetDateTime` | No | `2999-12-31 23:59:59.999 UTC` | When the record expires (end-of-time = still active) |
| `warehouseCreatedTimestamp` | `WarehouseCreatedTimestamp` | `OffsetDateTime` | No | Now (UTC) | When the warehouse first ingested the record |
| `warehouseLastUpdatedTimestamp` | `WarehouseLastUpdatedTimestamp` | `OffsetDateTime` | No | Now (UTC) | When the warehouse last modified the record |

---

## Base Table Column — `WarehouseBaseTable`

| Column Name | DB Column | Type | Nullable | Default | Purpose |
|---|---|---|---|---|---|
| `warehouseFromDate` | `warehouseFromDate` | `LocalDate` | Yes | Derived from `effectiveFromDate` | Date-only variant for warehouse partitioning |

---

## Source Tracking Columns — `WarehouseTable`

| Column Name | DB Column | Type | Nullable | Default | Purpose |
|---|---|---|---|---|---|
| `originalSourceSystemUniqueID` | `OriginalSourceSystemUniqueID` | `UUID` | Yes | `00000000-0000-0000-0000-000000000000` | Unique ID from the originating source system |
| `originalSourceSystemID` | `OriginalSourceSystemID` | `UUID` | No | `00000000-0000-0000-0000-000000000000` | FK to the originating `Systems` record (stored as UUID) |
| `enterpriseID` | `EnterpriseID` | `UUID` (FK → `dbo.Enterprise`) | No | — | The owning enterprise for multi-tenant isolation |

---

## SCD Control Columns — `WarehouseSCDTable`

| Column Name | DB Column | Type | Nullable | FK Target | Purpose |
|---|---|---|---|---|---|
| `activeFlagID` | `ActiveFlagID` | `UUID` (FK → `ActiveFlag.ActiveFlagID`) | No | `ActiveFlag` | Active / Deleted / Archived status |
| `systemID` | `SystemID` | `UUID` (FK → `Systems.SystemID`) | No | `Systems` | The system that owns this record |

---

## Primary Key Column Convention

Every primary entity uses a single `UUID` primary key following the pattern `{EntityName}ID`:

| Entity | DB Column | Java Field | Type |
|---|---|---|---|
| Enterprise | `EnterpriseID` | `id` | `UUID` |
| Classification | `ClassificationID` | `id` | `UUID` |
| InvolvedParty | `InvolvedPartyID` | `id` | `UUID` |
| Product | `ProductID` | `id` | `UUID` |
| Arrangement | `ArrangementID` | `id` | `UUID` |
| Event | `EventID` | `id` | `UUID` |
| Address | `AddressID` | `id` | `UUID` |
| Geography | `GeographyID` | `id` | `UUID` |
| ResourceItem | `ResourceItemID` | `id` | `UUID` |
| Rules | `RulesID` | `id` | `UUID` |
| SecurityToken | `SecurityTokenID` | `id` | `UUID` |

All IDs are auto-generated as `UUID.randomUUID()` on construction.

---

## Name & Description Columns

Entities that carry a name and description follow the pattern `{EntityName}Name` / `{EntityName}Desc`:

| Entity | Name Column | Name Length | Desc Column | Desc Length |
|---|---|---|---|---|
| Enterprise | `EnterpriseName` | unbounded | `EnterpriseDesc` | unbounded |
| Classification | `ClassificationName` | 100 | `ClassificationDesc` | 500 |
| Product | `ProductName` | 150 | `ProductDesc` | 250 |
| Geography | `GeographyName` | 500 | `GeographyDesc` | 500 |
| Rules | `RuleSetName` | 150 | `RuleSetDescription` | 250 |
| SecurityToken | `SecurityTokenFriendlyName` | unbounded | `SecurityTokenFriendlyDescription` | unbounded |

All name/description columns are `@NotNull` and `NOT NULL` in the database.

---

## Entity-Specific Columns

### Classification

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `classificationSequenceNumber` | `ClassificationSequenceNumber` | `int` | No | — | Sort ordering within a concept |
| `concept` | `ClassificationDataConceptID` | FK → `ClassificationDataConcept` | No | `@ManyToOne EAGER` | The data concept this classification belongs to |

### Product

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `productCode` | `ProductCode` | `String(10)` | No | `@Size(max=10)` | Short unique product code |

### Event

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `dayID` | `dayID` | `int` | No | — | Calendar day reference |
| `hourID` | `hourID` | `int` | No | — | Hour of day reference |
| `minuteID` | `minuteID` | `int` | No | — | Minute of hour reference |

### Address

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `value` | `Value` | `String` | No | Encrypted at rest | The address value (auto-encrypted/decrypted) |
| `classificationID` | `ClassificationID` | FK → `Classification` | No | `@ManyToOne LAZY` | Address type classification |

### Geography

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `classificationID` | `ClassificationID` | FK → `Classification` | No | `@ManyToOne LAZY` | Geography type classification |

### ResourceItem

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `resourceItemDataType` | `ResourceItemDataType` | `String(150)` | No | `@Size(max=150)` | MIME type or data type descriptor |

### SecurityToken

| Column | DB Column | Type | Nullable | Constraint | Purpose |
|---|---|---|---|---|---|
| `securityToken` | `SecurityToken` | `String(128)` | No | `@Size(max=128)` | The token value |
| `securityTokenClassificationID` | `SecurityTokenClassificationID` | FK → `Classification` | No | `@ManyToOne LAZY` | Token type classification |

---

## Relationship Table Columns — `WarehouseRelationshipTable`

All cross-reference (XRef / "X") tables inherit from `WarehouseRelationshipTable` and add:

| Column | DB Column | Type | Nullable | Purpose |
|---|---|---|---|---|
| `value` | `Value` | `String` | No | The relationship value (text, number, boolean stored as string) |

Plus two FK columns referencing the linked entities (e.g. `InvolvedPartyID` + `ProductID`).

Value access helpers: `getValueAsNumber()`, `getValueAsLong()`, `getValueAsBoolean()`, `getValueAsBigDecimal()`, `getValueAsDouble()`.

---

## Security Table Columns — `WarehouseSecurityTable`

All per-entity security tables inherit from `WarehouseSecurityTable` and add:

| Column | DB Column | Type | Nullable | Converter | Purpose |
|---|---|---|---|---|---|
| `createAllowed` | `CreateAllowed` | `boolean` | No | `NumericBooleanConverter` (0/1) | Whether create is permitted |
| `updateAllowed` | `UpdateAllowed` | `boolean` | No | `NumericBooleanConverter` (0/1) | Whether update is permitted |
| `deleteAllowed` | `DeleteAllowed` | `boolean` | No | `NumericBooleanConverter` (0/1) | Whether delete is permitted |
| `readAllowed` | `ReadAllowed` | `boolean` | No | `NumericBooleanConverter` (0/1) | Whether read is permitted |
| `securityTokenID` | `SecurityTokenID` | FK → `SecurityToken` | No | — | The security token granting this access |

---

## JPA Annotation Standards

| Concern | Standard |
|---|---|
| Access strategy | `@Access(AccessType.FIELD)` on all entities |
| ID generation | Application-assigned `UUID.randomUUID()` (no `@GeneratedValue`) |
| JSON serialization | `@JsonAutoDetect(fieldVisibility=ANY, getterVisibility=NONE, setterVisibility=NONE)` |
| JSON identity | `@JsonIdentityInfo(generator=PropertyGenerator, property="id")` |
| JSON null handling | `@JsonInclude(Include.NON_EMPTY)` |
| JSON unknown props | `@JsonIgnoreProperties(ignoreUnknown=true)` |
| Lombok | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` |
| Collections fetch | `FetchType.LAZY` with `CascadeType.ALL` |
| FK fetch (ManyToOne) | `FetchType.LAZY` (except Classification.concept which is `EAGER`) |
| Scalar fetch | `FetchType.EAGER` for name/description/code columns |
| Caching | `@Cacheable` + `@Cache(NONSTRICT_READ_WRITE)` on reference-data entities (Enterprise, Classification, Product) |

---

## Schema Mapping

Each entity domain uses its own database schema:

| Schema | Entities |
|---|---|
| `dbo` | Enterprise |
| `Classification` | Classification, ClassificationDataConcept |
| `Party` | InvolvedParty, InvolvedPartyNameType, InvolvedPartyIdentificationType, InvolvedPartyType |
| `Product` | Product, ProductType |
| `Arrangement` | Arrangement, ArrangementType |
| `Event` | Event, EventType |
| `Address` | Address |
| `Geography` | Geography |
| `resource` | ResourceItem, ResourceItemType, ResourceItemData |
| `Rules` | Rules, RulesType |
| `Security` | SecurityToken |


# ActivityMaster Entities & Relationships

## Core Entities

| Entity | Schema/Domain |
|--------|--------------|
| ActiveFlag | Active Flag |
| Address | Address |
| Arrangement | Arrangement |
| ArrangementType | Arrangement |
| Classification | Classifications |
| ClassificationDataConcept | Classifications |
| Enterprise | Enterprise |
| Event | Events |
| EventType | Events |
| Geography | Geography |
| InvolvedParty | Involved Party |
| InvolvedPartyIdentificationType | Involved Party |
| InvolvedPartyNameType | Involved Party |
| InvolvedPartyNonOrganic | Involved Party |
| InvolvedPartyOrganic | Involved Party |
| InvolvedPartyOrganicType | Involved Party |
| InvolvedPartyType | Involved Party |
| Product | Product |
| ProductType | Product |
| ResourceItem | Resource Item |
| ResourceItemData | Resource Item |
| ResourceItemDataValue | Resource Item |
| ResourceItemType | Resource Item |
| Rules | Rules |
| RulesType | Rules |
| SecurityToken | Security |
| Systems | Systems |

## Time Entities

| Entity |
|--------|
| DayNames |
| DayParts |
| Days |
| HalfHourDayParts |
| HalfHours |
| Hours |
| MonthOfYear |
| Months |
| PublicHolidays |
| Quarters |
| Time |
| TransFiscal |
| TransMtd |
| TransQtd |
| TransQtm |
| TransYtd |
| Weeks |
| Years |

## Hierarchy Views

| Entity | Description |
|--------|-------------|
| ArrangementsHierarchyView | Arrangement hierarchy |
| ClassificationHierarchyView | Classification hierarchy |
| GeographyHierarchyView | Geography hierarchy |
| InvolvedPartyHierarchyView | Involved Party hierarchy |
| ProductHierarchyView | Product hierarchy |
| ResourceItemHierarchyView | Resource Item hierarchy |
| RulesHierarchyView | Rules hierarchy |
| SecurityHierarchyView | Security hierarchy |
| SecurityHierarchyParents | Security hierarchy parent links |

## Relationships (Cross-Reference Entities)

### ActiveFlag
- ActiveFlagXClassification → ActiveFlag ↔ Classification

### Address
- AddressXClassification → Address ↔ Classification
- AddressXGeography → Address ↔ Geography
- AddressXResourceItem → Address ↔ ResourceItem

### Arrangement
- ArrangementXArrangement → Arrangement ↔ Arrangement (self-referencing)
- ArrangementXArrangementType → Arrangement ↔ ArrangementType
- ArrangementXClassification → Arrangement ↔ Classification
- ArrangementXInvolvedParty → Arrangement ↔ InvolvedParty
- ArrangementXProduct → Arrangement ↔ Product
- ArrangementXResourceItem → Arrangement ↔ ResourceItem
- ArrangementXRules → Arrangement ↔ Rules
- ArrangementXRulesType → Arrangement ↔ RulesType
- ArrangementTypeXClassification → ArrangementType ↔ Classification

### Classification
- ClassificationXClassification → Classification ↔ Classification (self-referencing)
- ClassificationXResourceItem → Classification ↔ ResourceItem
- ClassificationDataConceptXClassification → ClassificationDataConcept ↔ Classification
- ClassificationDataConceptXResourceItem → ClassificationDataConcept ↔ ResourceItem

### Enterprise
- EnterpriseXClassification → Enterprise ↔ Classification

### Event
- EventXAddress → Event ↔ Address
- EventXArrangement → Event ↔ Arrangement
- EventXClassification → Event ↔ Classification
- EventXEvent → Event ↔ Event (self-referencing)
- EventXEventType → Event ↔ EventType
- EventXGeography → Event ↔ Geography
- EventXInvolvedParty → Event ↔ InvolvedParty
- EventXProduct → Event ↔ Product
- EventXResourceItem → Event ↔ ResourceItem
- EventXRules → Event ↔ Rules

### Geography
- GeographyXClassification → Geography ↔ Classification
- GeographyXGeography → Geography ↔ Geography (self-referencing)
- GeographyXResourceItem → Geography ↔ ResourceItem

### InvolvedParty
- InvolvedPartyXAddress → InvolvedParty ↔ Address
- InvolvedPartyXClassification → InvolvedParty ↔ Classification
- InvolvedPartyXInvolvedParty → InvolvedParty ↔ InvolvedParty (self-referencing)
- InvolvedPartyXInvolvedPartyIdentificationType → InvolvedParty ↔ InvolvedPartyIdentificationType
- InvolvedPartyXInvolvedPartyNameType → InvolvedParty ↔ InvolvedPartyNameType
- InvolvedPartyXInvolvedPartyType → InvolvedParty ↔ InvolvedPartyType
- InvolvedPartyXProduct → InvolvedParty ↔ Product
- InvolvedPartyXProductType → InvolvedParty ↔ ProductType
- InvolvedPartyXResourceItem → InvolvedParty ↔ ResourceItem
- InvolvedPartyXRules → InvolvedParty ↔ Rules

### Product
- ProductXClassification → Product ↔ Classification
- ProductXProduct → Product ↔ Product (self-referencing)
- ProductXProductType → Product ↔ ProductType
- ProductXResourceItem → Product ↔ ResourceItem
- ProductTypeXClassification → ProductType ↔ Classification

### ResourceItem
- ResourceItemXClassification → ResourceItem ↔ Classification
- ResourceItemXResourceItem → ResourceItem ↔ ResourceItem (self-referencing)
- ResourceItemXResourceItemType → ResourceItem ↔ ResourceItemType
- ResourceItemDataXClassification → ResourceItemData ↔ Classification

### Rules
- RulesXArrangement → Rules ↔ Arrangement
- RulesXClassification → Rules ↔ Classification
- RulesXInvolvedParty → Rules ↔ InvolvedParty
- RulesXProduct → Rules ↔ Product
- RulesXResourceItem → Rules ↔ ResourceItem
- RulesXRules → Rules ↔ Rules (self-referencing)
- RulesXRulesType → Rules ↔ RulesType
- RulesTypeXClassification → RulesType ↔ Classification
- RulesTypeXResourceItem → RulesType ↔ ResourceItem

### SecurityToken
- SecurityTokenXClassification → SecurityToken ↔ Classification
- SecurityTokenXSecurityToken → SecurityToken ↔ SecurityToken (self-referencing)

### Systems
- SystemsXClassification → Systems ↔ Classification

## Security Token Entities

Each core entity and cross-reference entity has a corresponding SecurityToken entity that controls row-level security access. These are not listed individually above but follow the naming pattern `{Entity}SecurityToken`.


# RSDEV-1066 — Plan: enforce unique field names on Inventory API

**Branch:** `worktree-RSDEV-1066` (rename to `RSDEV-1066-unique-inventory-field-names` before pushing)
**Ticket:** https://researchspace.atlassian.net/browse/RSDEV-1066
**Approach:** TDD (red → green → refactor). Spans two repos: `rspace-core-model` (data only) and `rspace-web` (validator + manager checks). No DB schema migration, no UI changes, no data backfill.

> **Drift check (May 2026):** This plan was refreshed after PR #726 (`RSDEV-1062: Add new Instrument and InstrumentTemplate`) landed on `main`. Key changes accounted for: `ApiSampleField` → `ApiInventoryEntityField` rename; `InventoryEntityField` is now the parent field type used throughout rspace-web; new `Instrument` (POST + GET, no PUT) and `InstrumentTemplate` (no API yet — RSDEV-1059) entity types; new `ApiFieldsHelper` shared service.

> **Policy reminder for the implementer:** any AI-assisted code in this PR (including this plan) must comply with the **AI and Third Party code policy in Drata**. Cite it in the PR description.

---

## 1. Goal

Reject any API request that would result in two fields sharing a name on the same Inventory entity, *after applying all changes*, including names colliding with the entity's hardcoded display labels (mirroring the UI's `fieldNamesInUse` rule).

### 1.1 Combined namespace (what counts as a duplicate)

Per entity, the API computes a **combined namespace** of:
- the entity's **displayed-field labels** (new `getDisplayedFieldNames()` in `rspace-core-model` — see §3)
- the names of all **active SampleFields / InventoryEntityFields** on the entity (where applicable)
- the names of all **active ExtraFields** on the entity

Each populated, **trimmed** entry must be unique under **case-sensitive** equality. Null/blank names are skipped (they have their own validation upstream: `errors.inventory.template.empty.field.name`).

**Comparison rules:**
- **Trim leading/trailing whitespace before comparing** (tighter than the UI; a follow-up should align the UI).
- **Case-sensitive equality** for the duplicate check (matches UI's `new Set` semantics).
- **For the existing reserved-name check** (`validateExtraFieldName`, `validateIncomingTemplateFieldName`): make it the **UNION** of (existing lowercase, case-insensitive set) + (new `getDisplayedFieldNames()`, case-sensitive). Strictly additive — nothing currently rejected becomes acceptable.

### 1.2 Entity coverage

| Entity            | API surface today | SampleField/InventoryEntityField | ExtraField | New code in rspace-web? |
|-------------------|-------------------|----------------------------------|------------|--------------------------|
| `Sample`          | POST + PUT        | template-driven (carried on entity) | yes      | yes — both layers        |
| `SampleTemplate`  | POST + PUT        | user-defined                      | yes      | yes — both layers        |
| `SubSample`       | (created via Sample POST), PUT | —                  | yes      | yes — PUT path           |
| `Container`       | POST + PUT        | —                                 | yes      | yes — both layers        |
| `Instrument`      | POST + GET (feature-flagged: `inventory.instrument.enabled`, default false) | template-driven | yes | **POST only**            |
| `InstrumentTemplate` | none yet (RSDEV-1059 will add) | user-defined         | yes      | **core-model only**      |

`InstrumentTemplate` gets `getDisplayedFieldNames()` in core-model now so RSDEV-1059 has it ready, but no rspace-web validator/manager wiring (no controller to wire into). When RSDEV-1059 adds the API, they plug into the existing core-model method.

### 1.3 Out of scope

- **Liquibase deduper / data backfill.** Existing duplicates stay; documented as breaking change.
- **UI changes** (already enforces a related rule; trim-alignment is a follow-up).
- **`rspace_api_inventory_specs_2_13_0.yaml`** — frozen historical spec. The newer `rspace_api_inventory_specs_beta_instruments.yaml` (added in PR #726) is a beta spec — add a `description` note only if quick; otherwise skip.
- **Instrument PUT validation.** No PUT endpoint exists today; revisit when RSDEV-1059 adds InstrumentTemplate POST/PUT.

---

## 2. Architecture (two-repo, two-layer)

```
┌────────────────────────────────────────────────────────────────────────┐
│  rspace-core-model (PR #1)                                             │
│  ─ Add Set<String> getDisplayedFieldNames() on:                        │
│      InventoryRecord (base, default = BASE_DISPLAYED_FIELD_NAMES)      │
│      Sample (override, branches on isTemplate())                       │
│      Container (override)                                              │
│      SubSample (override)                                              │
│      InstrumentEntity (override or pass-through to base; both          │
│        Instrument and InstrumentTemplate inherit)                      │
│  ─ Unit tests for each entity's returned set                           │
│  ─ Branch: RSDEV-1066-model                                            │
│  ─ Version: RSDEV-1066-model-SNAPSHOT (via JitPack)                    │
└────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ resolved via JitPack/feature-branch SNAPSHOT
┌────────────────────────────────────────────────────────────────────────┐
│  rspace-web (PR #2)                                                    │
│                                                                        │
│  New file:                                                             │
│    com.researchspace.api.v1.controller.InventoryFieldNameUniquenessValidator │
│      static void rejectDuplicatesInPayload(                            │
│          List<ApiInventoryEntityField> incomingFields,                 │
│          List<ApiExtraField> incomingExtraFields,                      │
│          Errors errors)                                                │
│      static void assertNoDuplicateFieldNames(InventoryRecord rec)      │
│          throws ApiRuntimeException("errors.inventory.field.duplicate.name", name) │
│                                                                        │
│  Layer A: validator (request-body sanity → HTTP 400 via BindException) │
│  ─ Extend validateExtraFieldName / validateIncomingTemplateFieldName   │
│    to check UNION(reservedFieldNames CI lowercase, getDisplayedFieldNames CS) │
│  ─ Call rejectDuplicatesInPayload(...) from:                           │
│      • SampleTemplateValidator.validateFields(...) (covers POST + PUT) │
│      • SampleApiPostValidator / SampleApiPutValidator (extraFields)    │
│      • ContainerApiValidator.validateIncomingContainerFields(...)      │
│      • SubSampleApiPutValidator.validate(...)                          │
│      • InstrumentApiPostValidator.validateApiExtraFieldsInNewInstrument(...) │
│                                                                        │
│  Layer B: manager (post-application authoritative → HTTP 422 via       │
│           ApiRuntimeException → ApiControllerAdvice:105)               │
│  ─ assertNoDuplicateFieldNames(rec) called at the END of:              │
│      • SampleApiManagerImpl.createSampleTemplate                       │
│      • SampleApiManagerImpl.updateApiSampleTemplate                    │
│      • SampleApiManagerImpl.createNewApiSample                         │
│      • SampleApiManagerImpl.updateApiSample                            │
│      • InventoryApiManagerImpl.setBasicFieldsFromNewIncomingApiInventoryRecord │
│        (covers ExtraField paths for Container/SubSample/Sample/Instrument) │
│      • SubSampleApiManagerImpl PUT path                                │
│      • ContainerApiManagerImpl create/update                           │
│      • InstrumentApiManagerImpl.createNewApiInstrument                 │
└────────────────────────────────────────────────────────────────────────┘
```

**Why a dedicated class instead of bolting onto `ApiFieldsHelper`:** `ApiFieldsHelper` (added in PR #726) is 484 lines mixing attachment processing, choice-field conversion, permission checks, mandatory-field validation, and content-vs-form-field matching. Single-responsibility class is easier to unit-test and avoids further bloat.

**Why two layers / two HTTP statuses:**
- Validator catches obvious in-payload duplicates fast with **400** (`BindException` via `ApiControllerAdvice.handleBindException`).
- Manager catches DB-side cases the validator can't see (e.g. PUT to a Sample where the payload doesn't carry template-derived field names) with **422** (`ApiRuntimeException` via `ApiControllerAdvice:105`, error code `ApiErrorCodes.ILLEGAL_ARGUMENT`, message resolved from bundle via `ex.getErrorCode()`).

Plain `IllegalArgumentException` is deliberately not used — it has no handler in `ApiControllerAdvice` and would 500.

---

## 3. rspace-core-model changes (PR #1)

### 3.1 New method

In `InventoryRecord.java`:

```java
static final Set<String> BASE_DISPLAYED_FIELD_NAMES =
    Set.of("Name", "Description", "Preview Image", "Tags", "Attachments");

public Set<String> getDisplayedFieldNames() {
  return BASE_DISPLAYED_FIELD_NAMES;
}
```

Per-entity overrides (case-sensitive, Title-Case to mirror UI):

| Entity (Java class) | UI counterpart | Override returns `BASE ∪` … |
|---------------------|----------------|------------------------------|
| `Sample` (isTemplate==false) | `SampleModel.fieldNamesInUse` | `"Sample Template", "Expiry Date", "Source", "Storage Temperature", "Total Quantity", "Subsamples"` |
| `Sample` (isTemplate==true) | `TemplateModel.fieldNamesInUse` | `"Subsample Alias", "Quantity Units", "Fields", "Samples"` (note: TemplateModel does NOT inherit the Sample-extras above; matches UI) |
| `SubSample`         | `SubSampleModel.fieldNamesInUse` | `"Quantity", "Sample", "Notes"` |
| `Container`         | `ContainerModel.fieldNamesInUse` | `"Can Store", "Type", "Locations Image", "Grid Dimensions"` |
| `InstrumentEntity`  | (no UI definition; UI gets it via RSDEV-1059) | base only — i.e. just `BASE_DISPLAYED_FIELD_NAMES` |
| `Instrument`        | inherits `InstrumentEntity` | base only |
| `InstrumentTemplate` | inherits `InstrumentEntity` | base only — future RSDEV-1059 may override to mirror Sample/Template split |

`Sample.getDisplayedFieldNames()` must branch on `isTemplate()` and return the right set with a comment explaining the asymmetry (mirrors UI's `TemplateModel.fieldNamesInUse` not including Sample-specific labels).

### 3.2 Tests in rspace-core-model

One unit test class `InventoryRecordDisplayedFieldNamesTest` with methods:
- `containerDisplayedFieldNames`
- `subSampleDisplayedFieldNames`
- `sampleDisplayedFieldNames` (Sample where `isTemplate==false`)
- `sampleTemplateDisplayedFieldNames` (Sample where `isTemplate==true`)
- `instrumentDisplayedFieldNames`
- `instrumentTemplateDisplayedFieldNames`

Plain JUnit 5 + `assertEquals(Set.of(...), entity.getDisplayedFieldNames())`. Six trivial tests, milliseconds total.

### 3.3 Release & version

- Branch: `RSDEV-1066-model`
- pom.xml version: `RSDEV-1066-model-SNAPSHOT` (matches existing team convention — same shape as `RSDEV-1102-dmp-assistant-SNAPSHOT`, `RSDEV-1062-model-SNAPSHOT`)
- JitPack resolves feature-branch SNAPSHOTs from GitHub
- Before merging rspace-web PR: cut a numbered release (e.g. `2.22.1`) and update rspace-web pom.xml off SNAPSHOT.

---

## 4. rspace-web changes (PR #2)

### 4.1 pom.xml

Bump `rspace-core-model` from `2.22.0` to `RSDEV-1066-model-SNAPSHOT` during development; replace with numbered release before merge.

### 4.2 New class `InventoryFieldNameUniquenessValidator`

Path: `src/main/java/com/researchspace/api/v1/controller/InventoryFieldNameUniquenessValidator.java`

Public API:
```java
// Validator-layer (request-body) check — rejects on Errors.
// Walks both arrays as one namespace. On PUT, callers must filter out deleteFieldRequest=true.
static void rejectDuplicatesInPayload(
    List<ApiInventoryEntityField> incomingFields,
    List<ApiExtraField> incomingExtraFields,
    Errors errors);

// Manager-layer (post-application) assertion — throws ApiRuntimeException → 422.
// Walks getDisplayedFieldNames() ∪ active main fields ∪ active ExtraFields.
static void assertNoDuplicateFieldNames(InventoryRecord rec);
```

Both methods trim populated names, ignore null/blank, compare case-sensitive.

### 4.3 Existing validator changes

**`InventoryRecordValidator.java`:**
- `validateExtraFieldName(...)`: change comparison to UNION:
  - If `trimmed in entity.getDisplayedFieldNames()` (case-sensitive), reject as reserved.
  - If `trimmed.toLowerCase() in entity.getReservedFieldNames()` (case-insensitive, existing), reject as reserved.
  - Pass the entity (or its label set) into this method since the call sites already know the entity type.

**`SampleTemplateFieldValidator.java`:**
- Same UNION change for `validateIncomingTemplateFieldName(...)`.

**`SampleTemplateValidator.java`:**
- After `validateFields(...)`, invoke `InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(incomingFields, incomingExtraFields, errors)`. PUT path filters out `deleteFieldRequest==true` entries.

**`SampleApiPostValidator.java`, `SampleApiPutValidator.java`:**
- After `validateExtraFields(...)`, call the same helper (extra fields only since SampleField names are template-driven on POST/PUT for live samples).

**`ContainerApiValidator.validateIncomingContainerFields(...)`:** same.

**`SubSampleApiPutValidator.validate(...)`:** same.

**`InstrumentApiPostValidator.validate(...)`:** add the helper call after `validateApiExtraFieldsInNewInstrument(...)`.

### 4.4 Manager-layer assertions

Add `InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames(rec)` call at the end of each method in §2 (after all mutations applied, before return). The check is order-independent because it operates on the final state — handles the "rename B→A while deleting A in one PUT" edge case naturally.

### 4.5 Error message bundle

Find the bundle backing `errors.inventory.template.reserved.field.name` (`grep -rn "errors.inventory.template.reserved.field.name" src/main/resources`). Add:

```properties
errors.inventory.field.duplicate.name = Field name ''{0}'' is duplicated. Field names on a record must be unique.
```

Existing reserved-name key already takes `{0}` = offending name, `{1}` = reserved set joined with `/`. The extended reserved check should pass the combined displayed-label list as `{1}` so the error tells the user the full forbidden set.

---

## 5. TDD plan — failing tests first

Total ~22 tests:

### 5.1 rspace-core-model unit tests (PR #1)

`InventoryRecordDisplayedFieldNamesTest` — 6 methods (see §3.2).

### 5.2 rspace-web Spring transactional tests (PR #2)

All extend `SpringTransactionalTest`. Bypass controller so feature flag does not apply.

**In `SampleTemplatesApiManagerTest.java`** (already houses the existing `checkSampleTemplateCannotDuplicateDefaultFieldNames` at the same pattern):
- `templateCreateRejectsDuplicateSampleFieldNames` — two `ApiInventoryEntityField` named `"Foo"` → `ApiRuntimeException` with code `errors.inventory.field.duplicate.name`
- `templateCreateRejectsDuplicateNamesAcrossSampleAndExtraFields`
- `templateCreateRejectsFieldNamedAfterUILabel` — e.g. SampleField named `"Subsample Alias"` rejected by extended reserved check
- `templateCreateTrimsBeforeCompare` — `"Foo"` and `" Foo"` rejected
- `templatePutRejectsAddingDuplicateOfExistingFieldName`
- `templatePutAllowsRenamingBToAWhileDeletingA` (regression guard)
- `templatePutRejectsRenamingTwoFieldsToSameName`

**New test class `InventoryExtraFieldUniquenessTest.java`** (in `src/test/java/com/researchspace/service/inventory/`):
- `sampleCreateRejectsDuplicateExtraFieldNames`
- `sampleUpdateRejectsDuplicateExtraFieldNames`
- `sampleRejectsExtraFieldNamedAfterTemplateFieldName` — cross-collection on a live sample
- `subSampleUpdateRejectsDuplicateExtraFieldNames`
- `containerCreateRejectsDuplicateExtraFieldNames`
- `containerUpdateRejectsDuplicateExtraFieldNames`
- `containerRejectsExtraFieldNamedAfterUILabel` — e.g. ExtraField `"Type"` rejected
- `containerRejectsCaseSensitiveDifferentButTrimEqual` — `"Foo"` and `"Foo "` rejected

**In `InstrumentApiManagerTest.java`** (already exists from PR #726):
- `instrumentCreateRejectsDuplicateExtraFieldNames`

`InstrumentTemplate` has no manager test class because no API surface. Core-model unit test for it is enough.

### 5.3 rspace-web MVCIT smoke (PR #2)

One method on `SampleTemplatesApiControllerMVCIT.java`:
- `postSampleTemplateRejectsDuplicateFieldNames` — POST with two same-named `fields[]` → `is4xxClientError()` (400), parse `ApiError`, assert `"fields[1].name: ..."` error string matches bundle.

Optionally (~2 more, if cheap):
- `postSampleTemplateRejectsLabelCollision` — POST with `fields[0].name = "Subsample Alias"` → 400.
- `putSampleTemplateRejectsDbSideDuplicate` — PUT that introduces duplicate against existing DB field → `status().isUnprocessableEntity()` (422), `ApiError.errorCode == ApiErrorCodes.ILLEGAL_ARGUMENT`.

No Instrument MVCIT needed — Instrument validation is covered by `InstrumentApiManagerTest` (Spring transactional). If we were to add an Instrument MVCIT it would need `@TestPropertySource(properties = {"inventory.instrument.enabled=true"})` like the existing `InstrumentsApiControllerMVCIT`.

### 5.4 Red-phase workflow

1. Write all rspace-core-model tests → red (method doesn't exist).
2. Implement `getDisplayedFieldNames()` → green.
3. Push core-model branch; JitPack picks it up.
4. Bump rspace-web pom.xml to `RSDEV-1066-model-SNAPSHOT`; `mvn -U dependency:resolve`.
5. Write all rspace-web Spring tests + MVCIT → red.
6. Implement `InventoryFieldNameUniquenessValidator`, extend existing validators, add manager assertions → green.
7. Refactor — extract any per-entity validator duplication.
8. Pre-merge: cut numbered core-model release; bump rspace-web pom.xml off SNAPSHOT.

---

## 6. Risk & rollback

- **Breaking change for API clients with existing duplicate or label-colliding names.** Prominent note in PR description / changelog. Users who hit this rename via UI.
- **Internal seed-data templates** (`SyntheticWaxSampleTemplate`, `BacterialSampleTemplate`, `FFPESampleTemplate`, `AntibodySampleTemplate` — all in `src/main/java/com/axiope/model/record/init/`, all touched by PR #726): grep their `addSampleField` / `addExtraField` calls for any name colliding with the new displayed-label sets. Fix in this PR if any are caught.
- **JitPack flakiness during development.** `mvn -U clean install` if SNAPSHOT resolves stale.
- **`@TestPropertySource` for any future Instrument MVCIT.** Not needed by this PR's test set, but flag for the implementer.
- **Rollback:** revert both PRs (web first, then core-model). No data persisted, no schema touched.

---

## 7. Commit / PR shape

### PR #1: rspace-core-model
- **Branch:** `RSDEV-1066-model`
- **Title:** `RSDEV-1066: expose getDisplayedFieldNames() per Inventory entity`
- **Commits:** (1) failing tests, (2) implementation, optional (3) refactor.

### PR #2: rspace-web
- **Branch:** rename `worktree-RSDEV-1066` → `RSDEV-1066-unique-inventory-field-names`
- **Target:** `main` on `rspace-os/rspace-web` (upstream OSS), per CLAUDE.md
- **Commits:**
  1. Red: failing manager + MVCIT tests with pom.xml on `RSDEV-1066-model-SNAPSHOT`.
  2. Green: `InventoryFieldNameUniquenessValidator` + extended existing validators + manager assertions.
  3. Pre-merge: pom.xml to numbered release.
- **Description** starts with the AI-agent disclosure, references RSDEV-1066, links the Drata AI and Third Party code policy.

---

## 8. Acceptance criteria

1. POST `/sampleTemplates` with two same-named `fields[]` → **400**, `ApiError.errors` lists `fields[1]: <message>`.
2. POST with `fields[0].name = "Subsample Alias"` → **400** (extended reserved check).
3. POST with `fields[0].name = "Foo"` and `fields[1].name = " Foo"` → **400** (trim).
4. PUT `/sampleTemplates/{id}` that introduces a duplicate against existing DB field → **422** with errorCode `ILLEGAL_ARGUMENT`.
5. PUT that renames `B→A` *and* deletes `A` in one request → **200** (regression guard).
6. POST/PUT for Sample / SubSample / Container with duplicate `extraFields[].name` → 400 or 422 as appropriate.
7. POST `/instruments` with duplicate `extraFields[].name` → 400 (via validator) when feature flag enabled; manager-level assertion covers the case regardless.
8. `SampleApiManager.createSampleTemplate(...)` invoked directly with duplicates → `ApiRuntimeException`.
9. `Container.getDisplayedFieldNames()` returns exactly the set listed in §3.1 (and equivalently for the other five entity classes).
10. All existing tests still pass; `mvn spotless:apply` clean; no UI changes.
11. rspace-core-model PR merged and tagged; rspace-web pom.xml on release version (not SNAPSHOT) before PR #2 merges.

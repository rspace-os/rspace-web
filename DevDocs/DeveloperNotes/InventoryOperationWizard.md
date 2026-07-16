# Inventory Operation Wizard Framework

A framework for Inventory "operations": a user picks a subsample, a wizard gathers
input, and the system atomically creates one new Sample that parents N new
subsamples, links the new records back to the origin, and adjusts the origin's
quantity. Derive and Cryopreserve ship with it.

The design rationale is in the top-level ADRs: `adr/0001` (frontend-declared
operations, thin atomic backend), `adr/0002` (amount-taken decrement model),
`adr/0003` (user-chosen template), `adr/0004` (every operation has a process name),
and `adr/0005` (over-removal is rejected, not clamped). The shared vocabulary is in
the top-level `CONTEXT.md`.

## The one thing to know

**Adding a new operation is a frontend-config change. No Java.** You add an entry
to `operations_config.json` and some i18n strings. The backend endpoint and the
wizard UI are generic and never change per operation.

## How it fits together

```
operations_config.json ──► operationsConfig.ts (valibot-validated) ──► OperationWizard (UI)
                                                                          │  collects inputs
                                                                          ▼
                                              buildOperationRequest.ts ──► OperationRequest
                                                                          │  POST
                                                                          ▼
                                    POST /api/inventory/v1/operations (thin, generic, atomic)
                                    InventoryOperationsApiController ──► InventoryOperationManager
                                       validate                           @Transactional:
                                                                          create Sample + N subsamples
                                                                          + links + set origin quantity
```

Files:

- Config + logic: `src/main/webapp/ui/src/Inventory/components/Operations/`
  - `operations_config.json` — the operation definitions (the file you edit).
  - `operationsConfig.ts` — valibot schema (single source of truth for the shape)
    + `operations` / `operationsForSelectionSize`.
  - `buildOperationRequest.ts` — pure: (operation + collected values + origin) →
    request body. Puts provenance and documentation links on the new sample **and
    every created subsample**; text fields (e.g. Cryomedium) on the sample only.
  - `types.ts` — request/response types mirroring the backend DTO.
  - the wizard components (`OperationWizard`, `OperationPicker`,
    `OperationDetailsStep`, `DocumentationStep`, `OperationConfirmation`,
    `ProcessAction`).
- Backend (generic, do not edit per operation):
  `com.researchspace.api.v1.controller.InventoryOperationsApiController`,
  `InventoryOperationPostValidator`,
  `com.researchspace.service.inventory.InventoryOperationManager(+Impl)`,
  DTOs `ApiInventoryOperationPost` / `ApiInventoryOperationOriginUpdate`.

## Adding a new operation

1. **Add an entry to `operations_config.json`.** Schema:

   | field | meaning |
   | --- | --- |
   | `key` | stable id (also sent as `operationType`, for audit only) |
   | `labelKey`, `descriptionKey` | i18n keys shown in the picker |
   | `minSelected`, `maxSelected` | selection size the operation applies to (Derive/Cryo: 1/1) |
   | `documentationStep` | `true` to offer the optional `IsDocumentedBy` SOP-link step |
   | `inputs[]` | wizard fields: `{ key, type, labelKey, required?, min?, default? }`; `type` is `text` \| `integer` \| `quantity` \| `temperature` |
   | `effect.nameFrom` | input key holding the new sample's name |
   | `effect.countFrom` | input key holding N (number of new subsamples) |
   | `effect.eachAmountFrom` | input key holding each subsample's amount (unit category follows the chosen template, else the origin's — see the amounts step below) |
   | `effect.amountTakenFrom` | input key holding the amount to remove from the origin (a positive decrement; the wizard blocks and the backend rejects taking more than the origin holds — see adr/0005); omit for operations that never change the origin |
   | `effect.processNameFrom` | input key holding a user-entered process name (Derive). **Omit** for a fixed process name equal to the operation `key` (Cryopreserve → `"cryopreserve"`); every operation has a process name (adr/0004) |
   | `effect.storageTempFrom` | input key holding a temperature → set as the new sample's `storageTempMin/Max` (Cryopreserve) |
   | `effect.links[]` | `{ relationType, fieldNameKey }`; a DataCite relation link back to the origin. `fieldNameKey` may interpolate an input, e.g. `"Is Derived From using process: {processName}"` |
   | `effect.textFields[]` | `{ nameKey, contentFrom }`; plain-text field on the new sample (e.g. Cryomedium) |

   Order the `inputs[]` with the process name first and the name second: the wizard
   derives the sample name from the process name (see the Details step below).

2. **Add the i18n keys** you referenced to the `inventory` namespace
   (`src/modules/common/i18n/locales/en-US/inventory.json`), then run
   `pnpm run i18n:check` → fill English → `pnpm run i18n:types` →
   `pnpm run i18n:lint`. Field names written onto records are resolved in the
   user's locale on the frontend and stored as data; use ICU interpolation
   (single braces), never string concatenation. See `FrontendI18nKeys.md`.

3. That's it. The picker, wizard, request builder, and backend pick the new
   operation up automatically. Add a case to
   `operationsConfig.test.ts` / `buildOperationRequest.test.ts` if the operation
   has novel effect wiring.

### Worked example: Cryopreserve

Cryopreserve is Derive plus a Cryomedium text field and a storage temperature. It
required **only** a config entry: a `cryomedium` text input written to a
`Cryomedium` text field, a `storageTemp` temperature input mapped via
`storageTempFrom`, and a `"Frozen from"` link name. No backend or wizard code.
That is the framework's acceptance test: if a new operation needs Java or bespoke
wizard code, the framework has a gap worth fixing rather than working around.

## What the backend does

`POST /api/inventory/v1/operations` is a thin, generic coordinator. It validates
the request (a named new sample; each origin identifies a subsample with a positive
amount-taken that does **not** exceed the origin's current quantity — unit-aware,
see adr/0005) and, in one transaction, **reduces each origin by its amount-taken
first** and then creates the new sample + subsamples (reusing `SampleApiManager`).
The decrement-before-create order (adr/0005) makes the new subsample the
most-recently-modified record, so it sorts first in a modification-date-descending
listing (the generic listing default is name-asc, so this only shows when that sort
is requested). Reducing reuses `SubSampleApiManager.registerApiSubSampleUsage`, which
subtracts unit-aware and clamps at zero as defence-in-depth, so an origin can never be
increased. The endpoint never branches on the operation. Because the request is
client-built, permissions and invariants (including over-removal) are enforced
server-side; it coordinates, it does not blindly trust.

The over-removal check lives in the controller, not the stateless
`InventoryOperationPostValidator`, because it needs each origin's live quantity (loaded
via `SubSampleApiManager` with the request user). It uses the pure, unit-aware helper
`InventoryOperationPostValidator.amountTakenExceedsOrigin(...)` and reports through the
same `rejectValue` → `BindException` → HTTP 400 path as the structural rules.

## Wizard steps

Five steps: **Details → Template → Amounts → Documentation (optional) → Confirm**.

1. **Details** — the **process name** (a free-solo autocomplete of the user's saved
   names for this operation; fixed and non-editable for operations without one), the
   **derived sample name**, and the single **remember** checkbox (below). The sample
   name is auto-derived as `"<origin sample name> <process name>"` and de-duplicated
   against existing sample names with a `_1`, `_2`, … suffix (`firstAvailableName` in
   `sampleNaming.ts` probes each candidate via `operationsApi.sampleNameAvailable`,
   which calls the exact, own-scoped `samples/validateNameForNewSample` endpoint — **not**
   the tokenised full-text search, which cannot do an exact multi-word name check;
   degrades to no-dedup on error). Its field is **disabled until a process name is
   entered**, then
   editable (a manual edit stops further auto-derivation for the run). Next is disabled
   until the process name and sample name are present.
2. **Template** — its own step now (see below). Next is disabled until a choice is made.
3. **Amounts** — the number of new subsamples (full width) and the two quantities
   (each-amount and amount-taken, sharing a row). For a fresh process name (nothing
   remembered), the numeric fields default to 1 and the unit dropdowns start **blank**,
   which blocks Next until the user picks a unit. Unit categories differ per field: the
   **created** amount (each-amount) uses the chosen template's category when a specific
   template is picked, otherwise the origin subsample's — so deriving a volume sample
   from a mass subsample offers volume units. The **amount taken from the origin** always
   uses the origin subsample's own category (you remove mass from a mass sample),
   regardless of the template, and must not exceed the origin's current quantity
   (adr/0005): over-removal is flagged inline and blocks Next
   (`amountTakenExceedsOrigin`).

Details and Amounts are two slices of the same `OperationDetailsStep` (a `section`
prop selects which inputs render); the `count`/each-amount/amount-taken inputs are the
Amounts slice, everything else is Details. `detailsValid(operation, values, keys)`
validates each step's own inputs.

### Remembered process values (single checkbox)

One "Remember values for this process: {name}" checkbox on the Details step (in an
Alert-style box) governs everything kept for a process name — the template choice, the
documentation link, and the collected amounts — as a single bundle
(`processValues.ts`, preference `INVENTORY_OPERATION_PROCESS_VALUES`; supersedes the
earlier per-item template/doc/amount preferences). Ticking it loads the saved bundle
into the form; unticking resets the form to defaults **without deleting** what was
saved. The checkbox reflects the saved state as the process name changes (checked +
loaded when that name has a bundle, unchecked + defaults otherwise). On a successful
Perform, and only when ticked, the bundle is saved, the name added to the operation's
autocomplete list (`INVENTORY_OPERATION_PROCESS_NAMES`), and recorded as the
most-recently-used name (`INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS`, pre-filled on the
next run).

## The amount model (adr/0002, adr/0005)

The wizard captures the **amount taken from the origin** (a **positive** decrement).
The backend reduces the origin by it, so an operation can only ever decrease the
origin, never increase it. Taking **more than the origin holds is rejected** (adr/0005),
unit-aware, both in the wizard (Next blocked, inline message) and at the endpoint (HTTP
400) — the zero-clamp in `registerApiSubSampleUsage` remains only as defence-in-depth.
Each created subsample's amount is an independent input; the created total need not
equal what was taken (material may be added). The unit is part of the amount: it must be
chosen (a blank unit blocks the amounts step), and switching to a new process name clears
both the numbers and the units.

## Links

Every link (provenance and the optional documentation link) is placed on the new
sample and on every created subsample. Links reuse the RSDEV-1131 `link` field
(`{ relationType, targetGlobalId, versionPin }`); relation types come from
`DataCiteRelationType`. The documentation link targets an ELN document
(`IsDocumentedBy`); it is remembered as part of the single per-process bundle (see
"Remembered process values" above), not a separate preference.

## Template for the new sample (adr/0003)

The template choice is its **own framework-level step** (present for every operation,
not per-operation config), between Details and Amounts. Its category also governs the
Amounts step's units (above). Three choices, in this order:

- **From this sample's parent Sample** — reuses the origin subsample's parent Sample's
  own template (`origin.sample.templateId`). The wizard **never creates** a template
  (adr/0003); when the parent has none this option is **disabled with a hint** and the
  user must pick an existing template or none, or create a template separately first.
- **An existing template** — chosen with the shared `TemplatePicker`.
- **No template** — an ad-hoc sample (`templateId: null`).

The choice resolves to a single `templateId` (or null) via `resolveTemplateId` and is
passed into `buildOperationRequest`; the backend already forwards `newSample.templateId`,
so this needs no backend change. The choice is remembered as part of the single
per-process bundle (see "Remembered process values"), not a separate per-operation
preference.

The template-choice logic lives in pure, tested helpers (`templateResolution.ts`):
`resolveTemplateId` (reuse-or-none, no create) and `templateSelectionBlock` (the
mandatory-field guard). Picking a template whose mandatory fields have no default is
**blocked in the Template step** with a message naming those fields, so it can never
fail at submit; the user picks a different template. Collecting values for such template
fields in the wizard is deferred.

## Testing

- Frontend logic (pure): `buildOperationRequest.test.ts`, `operationsConfig.test.ts`,
  `sampleNaming.test.ts` (derive + dedup), `processValues.test.ts` (the remember
  bundle), `templateResolution.test.ts`, `operationValidation.test.ts` (incl.
  `amountTakenExceedsOrigin`). Component/flow: `OperationWizard.test.tsx`,
  `OperationDetailsStep.test.tsx`, `TemplateStep.test.tsx`. `pnpm test <path>` from the
  repo root.
- Backend: `InventoryOperationManagerImplTest` (incl. decrement-before-create order),
  `InventoryOperationPostValidatorTest` (incl. over-removal helper)
  (`mvn test -Dtest=... -Dfast=true`), plus `InventoryOperationsApiControllerMVCIT`
  (end-to-end, incl. over-removal rejection; run with `mvn verify`).

## Out of scope (current)

Multi-origin operations (Pool), operations that mutate the origin's own fields in
place (Passage's passage-number, Dispose's date field), link-field de-duplication
across consecutive in-place operations, and list-view entry points. The request
schema was designed to admit multi-origin and origin field-adds later.

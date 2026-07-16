# Inventory Operation Wizard Framework

A framework for Inventory "operations": a user picks a subsample, a wizard gathers
input, and the system atomically creates one new Sample that parents N new
subsamples, links the new records back to the origin, and adjusts the origin's
quantity. Derive and Cryopreserve ship with it.

The design rationale is in the top-level ADRs: `adr/0001` (frontend-declared
operations, thin atomic backend) and `adr/0002` (amount-taken decrement model).
The shared vocabulary is in the top-level `CONTEXT.md`.

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
   | `effect.amountTakenFrom` | input key holding the amount to remove from the origin (a positive decrement; the backend reduces the origin by it, clamped at zero, never increasing it); omit for operations that never change the origin |
   | `effect.storageTempFrom` | input key holding a temperature → set as the new sample's `storageTempMin/Max` (Cryopreserve) |
   | `effect.links[]` | `{ relationType, fieldNameKey }`; a DataCite relation link back to the origin. `fieldNameKey` may interpolate an input, e.g. `"Is Derived From using process: {processName}"` |
   | `effect.textFields[]` | `{ nameKey, contentFrom }`; plain-text field on the new sample (e.g. Cryomedium) |

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
the request (a named new sample; each origin identifies a subsample with a
positive amount-taken) and, in one transaction, creates the new sample +
subsamples (reusing `SampleApiManager`) and reduces each origin by its amount-taken
(reusing `SubSampleApiManager.registerApiSubSampleUsage`, which subtracts unit-aware
and clamps at zero, so an origin can never be increased). It never branches on the
operation. Because the request is client-built, the endpoint enforces permissions
and invariants server-side; it coordinates, it does not blindly trust.

## Wizard steps

The wizard collects data across two steps before the optional documentation step and
the confirm step:

1. **Details** — the new sample's name, the process name (for operations that have
   one), and the **template** choice (see below). Next is disabled until the required
   names are filled *and* a template choice is made.
2. **Amounts** — the number of new subsamples and the quantities (each-amount,
   amount-taken). For a fresh process name (nothing remembered), the numeric fields
   default to 1 and the unit dropdowns start **blank**, which blocks Next until the
   user picks a unit. Unit categories differ per field: the **created** amount
   (each-amount) uses the chosen template's category when a specific template is
   picked, otherwise the origin subsample's — so deriving a volume sample from a mass
   subsample offers volume units. The **amount taken from the origin** always uses the
   origin subsample's own category (you remove mass from a mass sample), regardless of
   the template.

Details and Amounts are two slices of the same `OperationDetailsStep` (a `section`
prop selects which inputs render); the `count`/each-amount/amount-taken inputs are the
Amounts slice, everything else is Details. `detailsValid(operation, values, keys)`
validates each step's own inputs.

## The amount model (adr/0002)

The wizard captures the **amount taken from the origin** (a **positive** decrement).
The backend reduces the origin by it (unit-aware, clamped at zero), so an operation can
only ever decrease the origin, never increase it. Each created subsample's amount is
an independent input; the created total need not equal what was taken (material may be
added). The unit is part of the amount: it must be chosen (a blank unit blocks the
amounts step), and switching to a new process name clears both the numbers and the
units.

## Links

Every link (provenance and the optional documentation link) is placed on the new
sample and on every created subsample. Links reuse the RSDEV-1131 `link` field
(`{ relationType, targetGlobalId, versionPin }`); relation types come from
`DataCiteRelationType`. The documentation link targets an ELN document
(`IsDocumentedBy`); the "remember this and make default" choice is a per-user,
per-operation preference stored in `UI_JSON_SETTINGS` via `useUiPreference` (no
backend change per operation).

## Template for the new sample (adr/0003)

The template choice is a framework-level part of the **Details step** (present for
every operation, not per-operation config), rendered after the name/process-name
fields. Its category also governs the Amounts step's units (above). The user
optionally chooses the Derived Sample's template:

- **No template** (default) — an ad-hoc sample (`templateId: null`).
- **An existing template** — chosen with the shared `TemplatePicker`.
- **From this sample's parent Sample** — reuses the origin subsample's parent
  Sample's own template when it has one (`origin.sample.templateId`); only when the
  sample is template-less does the frontend create one from it
  (`POST /sampleTemplates`). So repeated runs do not pile up duplicate templates.

The choice resolves to a single `templateId` (or null) passed into
`buildOperationRequest`; the backend `/operations` endpoint already forwards
`newSample.templateId`, so this needs no backend change. A "remember this choice"
checkbox persists the selection per user, per operation (in `UI_JSON_SETTINGS`).

The template-choice logic lives in pure, tested helpers (`templateResolution.ts`):
`resolveTemplateId` (reuse-or-create for option c) and `templateSelectionBlock`
(the option-a guard). Picking a template whose mandatory fields have no default is
**blocked in the Details step** with a message naming those fields, so it can
never fail at submit; the user picks a different template or uses option (c).
Collecting values for such template fields in the wizard is deferred.

## Testing

- Frontend logic: `buildOperationRequest.test.ts`, `operationsConfig.test.ts`
  (`pnpm test <path>` from the repo root).
- Backend: `InventoryOperationManagerImplTest`, `InventoryOperationPostValidatorTest`
  (`mvn test -Dtest=... -Dfast=true`), plus `InventoryOperationsApiControllerMVCIT`
  (end-to-end; run with `mvn verify`).

## Out of scope (current)

Multi-origin operations (Pool), operations that mutate the origin's own fields in
place (Passage's passage-number, Dispose's date field), link-field de-duplication
across consecutive in-place operations, and list-view entry points. The request
schema was designed to admit multi-origin and origin field-adds later.

# Inventory Operation Wizard Framework

A framework for Inventory "operations": a user picks a subsample, a wizard gathers
input, and the system atomically creates one new Sample that parents N new
subsamples, links the new records back to the origin, and adjusts the origin's
quantity. Derive, Cryopreserve, Aliquot, and Revive ship as pure config; **Passage**
adds an **operation function** (registry code) plus a backend relaxation; **Pool** is
multi-origin (2+ subsamples → one pooled sample, `HasPart` links back to each); **Destroy**
is **terminal** - it creates no sample, empties the origin, and stamps a disposal date on
the origin itself (adr/0008). Passage is the worked example that the "no Java" promise holds
only within the vocabulary of config primitives and registered functions. See the schema
table below and, for the full reasoning, `.claude/remaining-operations-plan.md` ("The limits
of config-only").

The design rationale is in the top-level ADRs: `adr/0001` (frontend-declared
operations, thin atomic backend), `adr/0002` (amount-taken decrement model),
`adr/0003` (user-chosen template), `adr/0004` (every operation has a process name),
`adr/0005` (over-removal is rejected, not clamped), `adr/0006` (operation-function
registry for computed values), `adr/0007` (multi-origin operations / Pool), and `adr/0008`
(terminal operations / Destroy). The shared vocabulary is in the top-level `CONTEXT.md`.

## The one thing to know

**Adding a new operation is a frontend-config change - *if* its every effect is already
a primitive in the config vocabulary.** For such operations you add an entry to
`operations_config.json` and some i18n strings; the backend endpoint and wizard UI are
generic and never change. This holds for Derive, Cryopreserve, Aliquot, and Revive.

It does **not** hold when an operation needs an effect the vocabulary cannot express -
then you add an operation function (or change the backend), i.e. you write a small amount
of code. Passage was the first such case (a `computed` value backed by the `increment`
operation function, plus a backend change to allow a linked-but-not-decremented origin).
Before assuming a new operation is config-only, decompose its effects and check each against
the schema table below; if one is missing, it is a (usually small) code story. The full
reasoning is in `.claude/remaining-operations-plan.md` ("The limits of config-only").

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
    request body. Puts provenance and documentation links, and text fields (e.g.
    Cryomedium), on the new sample only, **never on the created subsamples**.
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
   | `requiresMultiple` | `true` for a multi-origin operation (Pool: consumes 2+ subsamples); omit/false = single-origin. The picker shows every operation and enables single-origin ones for exactly one subsample, a `requiresMultiple` one for two or more of the same measurement category (adr/0007) |
   | `noOutput` | `true` for a **terminal** operation that creates no new sample and only acts on its origins (Destroy). The wizard builds no `newSample`, the validator makes `newSample` optional, and the backend creates nothing and returns null (adr/0008). Its `effect` omits `nameFrom`/`countFrom`/`eachAmountFrom` |
   | `documentationStep` | `true` to offer the optional `IsDocumentedBy` SOP-link step |
   | `steps[]` | explicit ordered subset of wizard steps to show, from `details` \| `template` \| `amounts` \| `documentation` \| `confirm`. Optional; when omitted the default sequence is used (details, template, amounts, documentation if `documentationStep`, confirm). Destroy sets `["confirm"]` — it needs no input, so it goes straight to confirmation (adr/0008) |
   | `inputs[]` | wizard fields: `{ key, type, labelKey, required?, min?, maxCelsius?, minCelsius?, default? }`; `type` is `text` \| `integer` \| `quantity` \| `temperature`. `maxCelsius`/`minCelsius` bound a `temperature` input (Cryopreserve's `storageTemp` is `≤ -18`; Revive's is `4..120`): an out-of-bounds value shows an inline error and blocks the step. `default` (a number) seeds a `temperature` input's opening value, e.g. Revive's `4` so it starts in range (an unconfigured one opens at `-80`) |
   | `effect.nameFrom` | input key holding the new sample's name (omit for a `noOutput` operation) |
   | `effect.countFrom` | input key holding N (number of new subsamples) (omit for a `noOutput` operation) |
   | `effect.eachAmountFrom` | input key holding each subsample's amount (unit category follows the chosen template, else the origin's — see the amounts step below) (omit for a `noOutput` operation) |
   | `effect.amountTakenFrom` | input key holding the amount to remove from the origin (a positive decrement; the wizard blocks and the backend rejects taking more than the origin holds — see adr/0005); omit for operations that never change the origin. For a multi-origin operation (Pool) this is a single shared amount taken from **each** origin (adr/0007) |
   | `effect.emptiesOrigin` | `true` to set the amount taken from each origin to that origin's **own full current quantity**, so its volume ends at zero (Destroy). Reuses the decrement path (clamps at zero; taking the full amount is not over-removal). Mutually exclusive with `amountTakenFrom` (adr/0008) |
   | `effect.originFields[]` | `{ nameKey, contentFrom, type? }`; a custom field added to the origin subsample **itself** (not the created sample), e.g. Destroy's disposed date. `type` is `text` (default) \| `number` — subsample fields have no native date type, so a date is a text field holding an ISO date. `contentFrom` is usually a `computed` value (adr/0008) |
   | `effect.links[]` (multi-origin) | each link spec fans out to **one link per origin**, so a single-origin operation yields one link and Pool yields one `HasPart` link back to every pooled subsample (adr/0007). A link's `fieldNameKey` may interpolate `{originName}` (the origin subsample's name) so the per-origin names are **distinct** — Pool uses `"Pooled from: {originName}"` because a record cannot hold two fields with the same name |
   | `effect.processNameFrom` | input key holding a user-entered process name (Derive). **Omit** for a fixed process name equal to the operation `key` (Cryopreserve → `"cryopreserve"`); every operation has a process name (adr/0004) |
   | `effect.storageTempFrom` | input key holding a temperature → set as the new sample's `storageTempMin/Max` (Cryopreserve, Revive) |
   | `effect.computed[]` | `{ fn, into, args }`; a **computed value** (adr/0006). At submit the wizard applies operation function `fn` (from the registry in `operationFunctions.ts`) to the bound `args` and writes the single result into input `into`, which other wiring (e.g. `textFields`) then consumes. Each arg is sourced by `{ parentSampleField: <i18nKey> }` (that field's content on the origin's parent sample, or absent), `{ constant: <n\|str> }`, or `{ input: <inputKey> }`. Evaluated in array order (a later entry can read an earlier `into` via `input`). Results never enter the remembered bundle. **A new computation is a new registry function** (code) referenced here — see "Operation functions" below |
   | `effect.links[]` | `{ relationType, fieldNameKey }`; a DataCite relation link back to the origin. `fieldNameKey` may interpolate an input, e.g. `"Is Derived From using process: {processName}"` |
   | `effect.textFields[]` | `{ nameKey, contentFrom }`; plain-text field on the new sample (e.g. Cryomedium) |
   | `confirmSummary[]` | ordered list of rows the confirmation step shows, from `process` \| `template` \| `subsamples` \| `amountTaken` \| `storageTemp` \| `linkBack` \| `documentation` \| `originEmptied` \| `originFields` (Cryopreserve lists `storageTemp`; Destroy lists `originEmptied`, `originFields`). A configured row whose value is absent (e.g. `documentation` with no linked doc) is skipped. Optional; a default order is used when omitted |

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

## Operation functions (computed values) — adr/0006

When an operation needs a value the declarative config cannot express (a computation),
you write an **operation function** rather than a one-off config primitive. The registry
is `operationFunctions.ts`: each entry is a named pure function that declares its
parameter names and returns a single value. Config selects it via `effect.computed[]` and
binds each argument to a source; the wizard resolves the arguments at submit
(`computedValues.ts`) and writes the result into the `into` input, which normal wiring
(usually `textFields`) then persists.

**To add a computation:** add a function to the registry, then reference it from config.

```ts
// operationFunctions.ts — the computation, in code
increment: {
  params: ["current", "start"],
  fn: ({ current, start }) => {
    const n = Number(current);
    return Number.isFinite(n) ? n + 1 : Number(start);
  },
},
```

```json
// operations_config.json — which function, how to source its args, where the result goes
"effect": {
  "computed": [{
    "fn": "increment",
    "into": "passageNumber",
    "args": {
      "current": { "parentSampleField": "operations.passage.numberField" },
      "start":   { "constant": 1 }
    }
  }],
  "textFields": [{ "nameKey": "operations.passage.numberField", "contentFrom": "passageNumber" }]
}
```

That is Passage's passage number: read the "Passage number" field on the origin's parent
sample, add one (or start at 1 when absent), and write it back onto the new sample under
the same field name, so successive passages increment. The reference is validated at module
load — an unknown function or a mismatched argument throws at startup, not at submit. The
registry is **dev-only**: it is not an end-user expression language, so config carries no
executable logic (adr/0006).

The registry also has `today` (no arguments): the user's local date as an ISO calendar date
(`YYYY-MM-DD`). Destroy writes it into the origin's disposed field via `effect.originFields`
(adr/0008). A `computed` result can flow into an `originFields` content just as it flows into
a `textFields` content — the difference is only where the field lands (the origin vs the new
sample).

## What the backend does

`POST /api/inventory/v1/operations` is a thin, generic coordinator. It validates
the request (each origin identifies a subsample with a non-negative amount-taken that
does **not** exceed the origin's current quantity — unit-aware, see adr/0005; and a
new sample, when present, is named) and, in one transaction, **reduces each origin by
its amount-taken first**, applies any custom fields the request adds to an origin
(Destroy's disposed date, via `updateApiSubSample`), and then creates the new sample +
subsamples (reusing `SampleApiManager`). The new sample is **optional**: a terminal
operation (`noOutput`, e.g. Destroy) sends none, so the endpoint creates nothing and
returns null (adr/0008). The decrement-before-create order (adr/0005) makes the new
subsample the most-recently-modified record, so it sorts first in a
modification-date-descending listing (the generic listing default is name-asc, so this
only shows when that sort is requested). Reducing reuses
`SubSampleApiManager.registerApiSubSampleUsage`, which subtracts unit-aware and clamps
at zero as defence-in-depth, so an origin can never be increased. The endpoint never
branches on the operation. Because the request is client-built, permissions and
invariants (including over-removal) are enforced server-side; it coordinates, it does
not blindly trust.

The over-removal check lives in the controller, not the stateless
`InventoryOperationPostValidator`, because it needs each origin's live quantity (loaded
via `SubSampleApiManager` with the request user). It uses the pure, unit-aware helper
`InventoryOperationPostValidator.amountTakenExceedsOrigin(...)` and reports through the
same `rejectValue` → `BindException` → HTTP 400 path as the structural rules.

## Wizard steps

The wizard is a modal dialog rendered via `ContextDialog` with `disableBackdropClick`,
so a click outside does not dismiss it and discard progress (Escape and Cancel still
close it).

Five steps: **Details → Template → Amounts → Documentation (optional) → Confirm**.
An operation may show a subset by declaring `steps` (adr/0008): a terminal operation
(Destroy) uses **Confirm** only, skipping Details, Template and Amounts (it needs no input,
creates no sample, and empties the origin, so none applies). Its description is shown on the
Confirm step as an info panel, and its "cannot operate on an empty subsample" guard is
enforced there too (Perform is blocked with the reason shown).

1. **Details** — the **process name** (a free-solo autocomplete of the user's saved
   names for this operation; fixed and non-editable for operations without one), the
   **derived sample name**, and the single **remember** checkbox (below). The sample
   name is auto-derived as `"<origin sample name> <process name>"` — but if the process
   name is already the tail of the origin name (ignoring any `_N` dedup and `.NN`
   subsample-serial suffixes, matched case-insensitively) it is **not** appended again,
   so repeated runs of the same process do not grow the name (`SUB PROC` stays `SUB PROC`
   and de-dups to `SUB PROC_1`, `SUB PROC_2`, … rather than becoming `SUB PROC PROC`).
   The name is then de-duplicated
   against existing sample names with a `_1`, `_2`, … suffix (`firstAvailableName` in
   `sampleNaming.ts` probes each candidate via `operationsApi.sampleNameAvailable`,
   which calls the exact, own-scoped `samples/validateNameForNewSample` endpoint — **not**
   the tokenised full-text search, which cannot do an exact multi-word name check;
   degrades to no-dedup on error). Deleted samples do not count as a name clash
   (`SampleDao.entityNameExistsForUser` filters `deleted=false`), so a name freed by
   deletion is reused without a suffix. Its field is **disabled until a process name is
   entered**, then
   editable (a manual edit stops further auto-derivation for the run). Next is disabled
   until the process name and sample name are present. If the origin subsample has **no
   amount** (0, or a quantity never set), this step shows an error
   (`operations.fields.originAmountZero`) and blocks Next: you cannot operate on an empty
   subsample.
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

One "Remember values for this process: {name}" checkbox on the Details step (a plain
checkbox with explanatory helper text beneath it, `rememberProcessValuesHelp`) governs
everything kept for a process name — the template choice, the
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
sample only, never on the subsamples it creates. Links reuse the RSDEV-1131 `link` field
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
- **An existing template** — chosen from `WizardTemplatePicker`, a single-select,
  server-backed autocomplete (same interaction as the process-name field, but the user
  cannot enter free text: typing re-queries the backend, debounced, and only a returned
  template can be selected). Each option shows the template name and its global id as
  plain text; reopening it pre-fills the currently-selected template.
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

Per-origin (unequal) pooling amounts, link-field de-duplication across consecutive
in-place operations, and list-view entry points. Multi-origin operations (Pool) are
supported (adr/0007), and terminal operations that create no new sample and add a custom
field to the origin (Destroy) are supported (adr/0008): the request schema carries origin
field-adds and an optional new sample. General in-place editing of arbitrary existing
origin fields (beyond adding new ones) is still out of scope.

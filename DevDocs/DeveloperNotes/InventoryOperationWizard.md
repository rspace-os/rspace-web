# Inventory Operation Wizard Framework

A framework for Inventory "operations": a user picks a subsample, a wizard gathers
input, and the system atomically creates one new Sample that parents N new
subsamples, links the new records back to the origin, and adjusts the origin's
quantity. Aliquot, Passage, Pool, Derive, Cryopreserve, Revive and Destroy ship.
**Pool** is multi-origin (2+ subsamples into one pooled sample, `HasPart` links back
to each) and can pour every origin out completely; **Destroy** is **terminal**, it
creates no sample, empties the origin, and stamps a disposal date on the origin
itself (DevDocs/adr/0011).

The design rationale is in the single consolidated ADR `DevDocs/adr/0011`, whose
2026-09-16 amendment records why the operations stopped being configuration. The
shared vocabulary is in the top-level `CONTEXT.md`.

## The one thing to know

**An operation is a Java class, and the wizard's copy of it is a TypeScript
constant.** Both are hand-written and hand-kept in step; nothing pins them to each
other, so a drift between them shows up as a 400 at Perform.

There is no configuration file, no definition registry and no interpreter. The
backend class decides what the operation takes from its origins and what sample it
builds; the frontend entry decides what the wizard asks for. Adding an operation
means writing both, plus a typed request body and an endpoint.

## Enabling

The feature ships off. `inventory.operations.available` is a system property
seeded `DENIED` (`changeLog-rsdev-1231.xml`, changeSet `2026-09-15a`); until a
sysadmin sets it to `ALLOWED` at **System > Configuration**, every
`/api/inventory/v1/operations` route answers 404 with errorCode
`CONFIGURED_UNAVAILABLE` (`InventoryOperationsApiController.assertOperationsAvailable`)
and the Process entry is hidden from every Inventory context menu
(`ContextMenu.tsx` reads the property, `ContextActions.tsx` hides the entry).
There is no sysadmin bypass. Any test or e2e spec that exercises the wizard has
to turn the property on first, as the operation MVCITs do in their `setup()`.

## How it fits together

```
operations.ts (a hand-typed constant)
          │
          ▼
OperationWizard (UI) ──collects the operation's values──► buildFacadeRequest.ts
                                                                  │
                                                                  ▼
                                    POST /api/inventory/v1/operations/<key>
                                    InventoryOperationsApiController
                                      shape rules, edit lock
                                                  │
                                                  ▼
                                    InventoryOperationManager
                                      @Transactional: snapshot the origins,
                                      op.validate, op.build, live-state rules,
                                      create Sample + N subsamples + links,
                                      decrement each origin
```

Files:

- Backend, one class per operation, in
  `com.researchspace.service.inventory.operations`:
  - `InventoryOperation<R>` — what an operation is: its key, whether it needs
    several origins, whether it takes a chosen amount or empties them, the value
    rules its request body cannot state, and the request it builds.
  - `CreatingOperation<R>` — the six that create a sample. A subclass supplies its
    provenance relation and, if it has them, its text fields or storage temperature.
  - `AliquotOperation`, `PassageOperation`, `PoolOperation`, `DeriveOperation`,
    `CryopreserveOperation`, `ReviveOperation`, `DestroyOperation`.
  - `OperationQuantityRules` — every rule that needs `RSUnitDef` to say what a unit id
    means: a real amount or temperature unit, storable at 3dp, positive, inside the
    operation's Celsius bounds, and a `count x eachAmount` total the column can hold.
  - `OperationOriginRules` — the origin list and the documentation target: no
    duplicates, an amount exactly where the operation takes one, a documentable record
    kind, and a well-formed subsample global id.
  - `OperationFieldNames` — builds the generated fields and keeps their names unique
    and inside the column. `LabelResolver`, `OriginState` — what an operation is given.
- Backend, shared: `InventoryOperationsApiController` (shape rules, the edit-session
  lock, renaming the core's error paths back to the caller's fields),
  `InventoryOperationManager(+Impl)` (the transactional core), the request bodies
  `ApiInventoryOperationRequests`, and the internal carrier
  `ApiInventoryOperationPost` / `ApiInventoryOperationOriginUpdate`.
- Frontend: `src/main/webapp/ui/src/Inventory/components/Operations/`
  - `operations.ts` — the `operations` constant, one entry per Java class, plus
    the helpers the wizard derives from it. Label and field-name keys are typed as the
    `inventory:` catalog's own key union, so a mistyped key fails at compile time.
    `MAX_ORIGINS` repeats the Pool body's own ceiling.
  - `buildFacadeRequest.ts` — pure: (operation + collected values + origins) into the
    body that operation's endpoint takes. `withUniqueFieldNames` lives here too, for
    the confirmation preview; it is the same rule as `OperationFieldNames`, pinned to
    it by `FieldNameUniquenessParityTest` over the shared cases in
    `src/test/resources/inventory/fieldNameUniquenessCases.json`.
  - `computedValues.ts`, `operationFunctions.ts` — the preview's model of the values
    the server computes. They render the confirmation card; they never build a request.
  - `types.ts` — the wizard's own types, mirroring the request bodies.
  - the wizard components (`OperationWizard`, `OperationPicker`,
    `OperationDetailsStep`, `DocumentationStep`, `OperationConfirmation`,
    `ProcessAction`).

## Adding a new operation

1. **Write the Java class** in `com.researchspace.service.inventory.operations`.
   Extend `CreatingOperation<R>` if it creates a sample: supply `key()`,
   `linkRelation()` and `linkFieldNameKey()`, and override only what differs.
   The hooks are `textFields` (a field on the created sample, e.g. Cryopreserve's
   cryomedium), `storageTemp`, `amountTakenFrom` (what this operation takes from one
   origin: the default is the amount the caller chose), `requiresMultiple`,
   `takesAmount`, `emptiesOrigin` and `validate`. Implement `InventoryOperation<R>`
   directly for a terminal operation, as `DestroyOperation` does.

   Call `super.validate(...)` from an override: it checks the created amount and the
   total. Put a rule needing `RSUnitDef` in `OperationQuantityRules` rather than
   writing it out, so every operation judges a unit the same way.

2. **Write the request body** as a nested class of `ApiInventoryOperationRequests`,
   extending `SingleOriginCreating` or `Creating`. Every rule about one field in
   isolation belongs here as a jakarta annotation with a catalog key for its message:
   presence, bounds, and the length of the column the value lands in. A generated
   client can then enforce them, and the endpoint rejects them before any code runs.

3. **Add the endpoint**: one method on `InventoryOperationsApi`, one on the
   controller delegating to `perform(theOperation, request, errors, user)`, and one
   `@Autowired` field for the operation. The controller has no per-operation logic.

4. **Add the wizard entry** to the `operations` constant in `operations.ts`,
   mirroring the Java class: which inputs the wizard collects, the effect wiring the
   confirmation preview reads, and which summary rows to show. The picker, the steps,
   the request builder and the preview pick it up from there.

5. **Add the i18n keys** to the `inventory` namespace
   (`src/modules/common/i18n/locales/en-US/inventory.json`), then run
   `pnpm run i18n:check`, fill English, `pnpm run i18n:types`, `pnpm run i18n:lint`.
   Field names written onto records are resolved in the user's locale and stored as
   data; use ICU interpolation (single braces), never concatenation. Any new error
   message needs an entry in `server.inventory.json`, which
   `InventoryOperationsErrorCatalogTest` enforces. See `FrontendI18nKeys.md`.

6. **Add it to the published OpenAPI spec**
   (`src/main/webapp/resources/rspace_api_inventory_specs_2_27_0.yaml`, tag
   `Operations`): one path and one request schema, alongside the seven already there.

7. **Test it**: a `*OperationTest` for what it builds and what it rejects, a case in
   `ApiInventoryOperationRequestsBeanValidationTest` for any new annotation, and an
   end-to-end case in `InventoryOperationFacadesMVCIT`.

## Computed values in the confirmation preview

Two operations record a value the user never types: Passage's passage number and
Destroy's disposal date. **The server computes both** (`PassageOperation`,
`DestroyOperation`), because the value that matters is the one that gets stored.

The wizard keeps its own model of those computations so the confirmation card can show
the user what the operation will record before they commit. `operationFunctions.ts` is
a small registry of named pure functions, `computedValues.ts` applies them, and an
operation's `effect.computed[]` entry says which function, where each argument comes
from, and which slot the result lands in:

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

```ts
// operations.ts — which function, how to source its args, where the result goes
effect: {
  computed: [{
    fn: "increment",
    into: "passageNumber",
    args: {
      current: { parentSampleField: "operations.passage.numberField" },
      start: { constant: 1 },
    },
  }],
  textFields: [{ nameKey: "operations.passage.numberField", contentFrom: "passageNumber" }],
}
```

The registry also has `today` (no arguments): the user's local date as an ISO calendar
date, which is what Destroy's preview shows for the disposed field.

This is a preview, not a request: nothing computed here is ever posted. If the preview
and the server disagree, the server wins and the user sees a value they were not shown,
so a change to `PassageOperation`'s counter or `DestroyOperation`'s date belongs on both
sides.

## What the backend does

`POST /api/inventory/v1/operations/<key>` is the only way in. There are seven, one
per operation, and the only difference between them is which operation the controller
names.

The controller checks what it can before touching anything: the body's own
annotations have already run at binding, then `OperationOriginRules` checks the origin
list and the documentation target, then each origin's global id is parsed to a
subsample id. It claims every origin as in flight (`InventoryOperationInFlightOrigins`,
409 if another request still holds one, whoever sent it), takes the Inventory
edit-session lock on every origin and every parent sample, in ascending global-id
order, and calls the manager. Claim and locks are released after the manager's
transaction has ended.

The manager runs in one transaction. It asserts edit permission on each origin while
snapshotting its live state (`OriginState`: id, global id, name, quantity, and the
parent sample's fields), lets the operation check its own values and the documentation
target's readability, then asks the operation to build the request the core executes:
one update per origin with the amount to take, and the sample to create. The core
enforces the live-state rules, runs the same template-conformance check as the public
samples endpoint, decrements each origin and creates the sample, then reads every
origin back. Everything the caller sees comes from that one transaction.

Generated field names are resolved server-side in the request's locale from the same
i18next catalogs the wizard uses (they are on the backend classpath under the
`inventory:` namespace), interpolating `{originName}`. Every generated field is stamped
with `operationFieldKey`, which is persisted and returned on GET so a later run matches
the previous generation by key; the property is read-only on the API, so no request on
any endpoint can set it. A date an operation records (Destroy's disposed) resolves in
the session's timezone, so it is the user's local date rather than the server's.

The live-state rules are in `InventoryOperationManagerImpl` rather than in a stateless
validator because each needs the origin's live quantity, read in the mutation's own
transaction: an origin must currently hold something, the amount taken may not exceed
what it holds or be too fine to subtract from it, all origins must share one
measurement category, and an emptying operation must take exactly what is there. They
report through the same `rejectValue` to `BindException` to HTTP 400 path as the shape
rules.

The decrement-before-create order (DevDocs/adr/0011) makes the new subsample the
most-recently-modified record, so it sorts first in a modification-date-descending
listing. Reducing reuses `SubSampleApiManager.registerApiSubSampleUsage`, which
subtracts unit-aware and clamps at zero as defence-in-depth, so an origin can never be
increased. A terminal operation builds no sample and the endpoint creates nothing.

### What each request looks like

Six operations send a singular `origin`, identified by global id (`"SS1234"`); Pool
sends `origins`. An origin carries `amountTaken` exactly when the operation takes a
chosen amount: never for Passage, which leaves the origin alone, never for Destroy,
which takes all of it, and never on a Pool that sets `takeAll`. Sending one anyway is
a 400. The operation's own values are top-level fields named after what they are
(`sampleName`, `count`, `eachAmount`, `processName`, `cryomedium`, `storageTemp`),
alongside `templateId` and `documentedByGlobalId`.

`count` may be omitted and defaults to 1; Revive's `storageTemp` may be omitted and
defaults to 4 degrees Celsius. Both defaults are applied by the operation class.

The core works with an origin LIST and a server-built sample, so the controller
renames its error paths back to what the caller sent on the way out
(`InventoryOperationsApiController.facadeField`): `origins[0].amountTaken` to
`origin.amountTaken`, `origins[1].id` to `origins[1].globalId`,
`newSample.templateId` to `templateId`, `newSample.subSamples[i].quantity` to
`eachAmount`, `newSample.name` to `sampleName`, `newSample.storageTempMin`/`Max` to
`storageTemp`. The caller's own field names pass through untouched.

All seven answer with one envelope (`ApiInventoryOperationResult`: the created
`sample`, null for Destroy, and each `origin` as it stands afterwards), 201 with a
`Location` at the new sample for the six creating operations, 200 for Destroy. All
seven are in the published OpenAPI spec
(`src/main/webapp/resources/rspace_api_inventory_specs_2_27_0.yaml`, tag `Operations`).

## Wizard steps

The wizard is a modal dialog rendered via `ContextDialog` with `disableBackdropClick`,
so a click outside does not dismiss it and discard progress (Escape and Cancel still
close it).

Five steps: **Details → Template → Amounts → Documentation (optional) → Confirm**.
An operation may show a subset by declaring `steps` (DevDocs/adr/0011): a terminal operation
(Destroy) uses **Confirm** only, skipping Details, Template and Amounts (it needs no input,
creates no sample, and empties the origin, so none applies). Its description is shown on the
Confirm step as an info panel, and its "cannot operate on an empty subsample" guard is
enforced there too (Perform is blocked with the reason shown).

1. **Details** — the **process name** (a free-solo autocomplete of the user's saved
   names for this operation; fixed and non-editable for operations without one) and the
   **derived sample name** (the single **remember** checkbox lives on the Confirm step,
   below). The sample
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
2. **Template** — its own step now (see below). For a first-time run the parent
   sample's own template is preselected when it has one
   (`initialTemplateSelection`); otherwise Next is disabled until a choice is made.
3. **Amounts** — the number of new subsamples (full width) and the two quantities
   (each-amount and amount-taken, sharing a row). For a fresh process name (nothing
   remembered), the numeric fields default to 1 and the unit dropdowns are
   **prefilled with the origin subsample's own unit** (the user may pick another in
   the category). Unit categories differ per field: the
   **created** amount (each-amount) uses the chosen template's category when a specific
   template is picked, otherwise the origin subsample's — so deriving a volume sample
   from a mass subsample offers volume units (picking a template in a different
   category resets the created amount's prefilled unit to blank, so a stale unit
   cannot survive into the request). The **amount taken from the origin** always
   uses the origin subsample's own category (you remove mass from a mass sample),
   regardless of the template, and must not exceed the origin's current quantity
   (DevDocs/adr/0011): over-removal is flagged inline and blocks Next
   (`amountTakenExceedsOrigin`).

Details and Amounts are two slices of the same `OperationDetailsStep` (a `section`
prop selects which inputs render); the `count`/each-amount/amount-taken inputs are the
Amounts slice, everything else is Details. `detailsValid(operation, values, keys)`
validates each step's own inputs.

### Remembered process values (single checkbox)

One "Remember values for this process: {name}" checkbox beneath the summary card on
the Confirm step — and on the step-one fast path, which renders the same
confirmation — (a plain
checkbox with explanatory helper text beneath it, `rememberProcessValuesHelp`) governs
everything kept for a process name — the template choice, the
documentation link, and the collected amounts — as a single bundle
(`processValues.ts`, preference `INVENTORY_OPERATION_PROCESS_VALUES`; supersedes the
earlier per-item template/doc/amount preferences). Ticking it only marks the current
form for saving; it never reloads the stored bundle, so untick, edit, re-tick saves the
edited values. Unticking resets the form to defaults **without deleting** what was
saved. The checkbox reflects the saved state as the process name changes (checked +
loaded when that name has a bundle, unchecked + defaults otherwise). On a successful
Perform, and only when ticked, the bundle is saved, the name added to the operation's
autocomplete list (`INVENTORY_OPERATION_PROCESS_NAMES`), and recorded as the
most-recently-used name (`INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS`, pre-filled on the
next run).

## The amount model (DevDocs/adr/0011)

DevDocs/adr/0011 D5 has the model: a positive decrement, over-removal rejected rather
than clamped, and created amounts independent of the amount taken. The wizard enforces
it a step earlier than the endpoint does, and adds two rules of its own: the unit is part
of the amount, so a blank unit blocks the amounts step, and switching to a new process
name clears both the numbers and the units.

## Links

Every link (provenance and the optional documentation link) is placed on the new
sample only, never on the subsamples it creates. Links reuse the RSDEV-1131 `link` field
(`{ relationType, targetGlobalId, versionPin }`); relation types come from
`DataCiteRelationType`. The documentation link (`IsDocumentedBy`) targets an ELN
document, notebook or Gallery file, the kinds the picker offers; the endpoint
rejects any other target. Generated field names interpolate caller-supplied values
("Pooled from: {originName}"), so the builder fits each one to `EditInfo.name`'s 255
characters BEFORE appending the uniqueness suffix, and re-checks uniqueness on the
fitted name: truncated rather than rejected, because pooling two origins already at the
column limit has to remain possible and the link target carries the meaning. It is remembered as part of the single per-process bundle (see
"Remembered process values" above), not a separate preference.

## Template for the new sample (DevDocs/adr/0011)

The template choice is its **own framework-level step** (present for every operation,
not per-operation config), between Details and Amounts. Its category also governs the
Amounts step's units (above). Three choices, in this order:

- **From this sample's parent Sample** — reuses the origin subsample's parent Sample's
  own template (`origin.sample.templateId`). The wizard **never creates** a template
  (DevDocs/adr/0011); when the parent has none this option is **disabled with a hint** and the
  user must pick an existing template or none, or create a template separately first.
- **An existing template** — chosen from `WizardTemplatePicker`, a single-select,
  server-backed autocomplete (same interaction as the process-name field, but the user
  cannot enter free text: typing re-queries the backend, debounced, and only a returned
  template can be selected). Each option shows the template name and its global id as
  plain text; reopening it pre-fills the currently-selected template.
- **No template** — an ad-hoc sample (`templateId: null`).

The choice resolves to a single `templateId` (or null) via `resolveTemplateId` and is
sent as the request's top-level `templateId`, from which the server builds the sample
(the template-conformance check runs on what it built). The choice is remembered as part of the single
per-process bundle (see "Remembered process values"), not a separate per-operation
preference.

The template-choice logic lives in pure, tested helpers (`templateResolution.ts`):
`resolveTemplateId` (reuse-or-none, no create) and `templateSelectionBlock` (the
mandatory-field guard). Picking a template whose mandatory fields have no default is
**blocked in the Template step** with a message naming those fields, so it can never
fail at submit; the user picks a different template. Collecting values for such template
fields in the wizard is deferred.

## Testing

- Backend unit and validator tests: `src/test/java/com/researchspace/service/inventory/operations`,
  plus the `InventoryOperation*Test` classes elsewhere under `src/test/java` (the request
  bodies' annotations, the controller, the transactional core, the error catalog).
  `mvn test -Dtest='*Operation*Test' -Dfast=true`.
- Backend, end to end: the operation `*MVCIT` classes. Run with
  `mvn verify -Denvironment=drop-recreate-db -Dtest=A,B`; this resets the database.
- Frontend: `src/main/webapp/ui/src/Inventory/components/Operations/__tests__`, plus the
  pure helpers' own tests beside the helpers. `pnpm test <path>` from the repo root.
- The field-name uniqueness rule is implemented in both languages, and
  `src/test/resources/inventory/fieldNameUniquenessCases.json` is the only thing tying
  them together: `FieldNameUniquenessParityTest` and `buildOperationRequest.test.ts` both
  assert it, so changing the rule on one side alone turns the other red.

## Out of scope (current)

Per-origin (unequal) pooling amounts, link-field de-duplication across consecutive
in-place operations, and list-view entry points. Multi-origin operations (Pool) are
supported (DevDocs/adr/0011), and terminal operations that create no new sample and add a custom
field to the origin (Destroy) are supported (DevDocs/adr/0011): the server adds the declared
origin field itself. General in-place editing of arbitrary existing
origin fields (beyond adding new ones) is still out of scope.

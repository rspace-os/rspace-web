# Inventory Operation Wizard: Requirements and Implementation

Traceability for RSDEV-1231 (biobank workflow), against the proposal in epic
RSDEV-1228. This maps *what was asked for* to *how it was built*. The mechanics
live in `InventoryOperationWizard.md`; the rationale lives in `adr/0001`..`adr/0005`
and the vocabulary in the top-level `CONTEXT.md`. This file does not repeat them.

## One-line summary

A generic, frontend-declared operation framework: a user picks a subsample, a
modal wizard gathers input, and one atomic backend endpoint creates a new Sample
parenting N subsamples, links them to the origin, and decrements the origin.
Derive, Cryopreserve, and Aliquot ship on it; new operations are config-only.

## Requirements traceability

| # | Requirement (RSDEV-1228/1231) | Status | How implemented |
| --- | --- | --- | --- |
| 1 | Reusable/extensible operation "recipe" framework | Done | Operations are data, not code: `operations_config.json` validated by the valibot schema in `operationsConfig.ts`. Adding one is a config + i18n change, no Java (adr/0001). |
| 2 | Per-instance/per-operation configurability | Done | Each config entry declares inputs, effect wiring, links, text fields, and the confirm summary. The wizard and endpoint never branch on the operation. |
| 3 | Ship initial operations | Done | `operations_config.json` ships **Derive**, **Cryopreserve**, and **Aliquot**, all config-only. Cryopreserve = Derive + a Cryomedium text field + a `storageTemp` bounded at `maxCelsius: -18`. Aliquot takes equal-volume aliquots and links with `IsPartOf`. |
| 4 | Remaining operations (Pool, Revive, Passage, Dispose) | Out of scope | Deferred (see "Out of scope"). The request schema was designed to admit multi-origin and in-place field edits later. |
| 5 | Eligible-resource determination | Done (subsample-only) | `operationsForSelectionSize` filters by `minSelected`/`maxSelected`; all shipped operations are subsample-only, single selection. |
| 6 | Parameter/details steps | Done | `OperationDetailsStep` renders Details and Amounts slices (a `section` prop selects inputs); `detailsValid` validates each step's own inputs. |
| 7 | Relation types (IsDerivedFrom, IsPartOf, IsVariantFormOf) | Done (via config) | `effect.links[]` carries a `relationType` from `DataCiteRelationType`. In use: `IsDerivedFrom` (Derive, Cryopreserve) and `IsPartOf` (Aliquot); `IsVariantFormOf` is available by config, no code change. |
| 8 | Provenance link back to the origin | Done | `buildOperationRequest` puts every link (provenance + optional doc link) on the **new sample only**, never the subsamples. Reuses the RSDEV-1131 `link` field. |
| 9 | Creation-complete confirmation screen | Done | `OperationConfirmation`: a preview Card of the sample to be created (header = name + operation; body = a `DescriptionList` label:value grid). Rows are picked/ordered by the operation's `confirmSummary` (RSDEV-1231 summary redesign). |
| 10 | Atomic creation (sample + N subsamples + links + origin adjust) | Done | `POST /api/inventory/v1/operations`, one `@Transactional` in `InventoryOperationManagerImpl`: decrement each origin first, then create (adr/0005 ordering), reusing `SampleApiManager`. |
| 11 | Origin quantity is only ever decreased | Done | Wizard captures a **positive** amount-taken (adr/0002); backend subtracts it. `registerApiSubSampleUsage` clamps at zero as defence-in-depth. |
| 12 | Reject taking more than the origin holds | Done | Unit-aware, both sides: inline block in the Amounts step (`amountTakenExceedsOrigin` in `operationValidation.ts`) and HTTP 400 at the endpoint via `InventoryOperationPostValidator.amountTakenExceedsOrigin` (adr/0005). |
| 13 | Template for the new sample | Done | Own framework step (adr/0003): parent-sample template, an existing template (`WizardTemplatePicker`), or none. Never creates a template; a template with undefaulted mandatory fields is blocked in-step (`templateResolution.ts`). |
| 14 | Optional documentation (SOP/protocol) link | Done | `DocumentationStep`, gated by `documentationStep` in config; targets an ELN document with `IsDocumentedBy`. |
| 15 | Server-side enforcement of permissions/invariants | Done | The request is client-built, so the endpoint validates rather than trusts: structural rules in the stateless validator; over-removal in the controller (needs live origin quantity). |
| 16 | Bounded storage temperature (Cryopreserve) | Done | A `temperature` input may declare `maxCelsius` (Cryopreserve: `-18`); a warmer value shows the inline `storageTempMax` error and blocks the step. Frontend-enforced; there is no backend temperature rule. |

## RSDEV-1231-specific work (this branch)

- **Every operation has a process name** (adr/0004): user-entered for Derive (a
  free-solo autocomplete of saved names), fixed to the operation key for
  Cryopreserve. It is the single key for remembered values and seeds the sample
  name (`processNames.ts`, `sampleNaming.ts`).
- **Single remember bundle**: one "Remember values for this process" checkbox keeps
  template + documentation + amounts as one per-process bundle
  (`processValues.ts`, `INVENTORY_OPERATION_PROCESS_VALUES`), superseding the earlier
  per-item preferences.
- **Templates chosen before amounts**: the Template step precedes Amounts so the
  chosen template's unit category governs the each-amount units.
- **Confirmation redesign**: prose summary replaced by the config-driven preview
  card above (`OperationConfirmation.tsx`, `confirm.labels.*` / `confirm.values.*`
  i18n keys).
- **Aliquot**: a third operation, added config-only (equal-volume aliquots, parent
  volume decremented, `IsPartOf` link).
- **Bounded temperature input**: `maxCelsius` on a `temperature` input, with the
  `storageTempMax` inline error (Cryopreserve's `storageTemp` at or below `-18` °C).

## Deviations from the proposed flow

The epic proposed *picker to eligible-source to parameters to location/destination
to confirmation*. As built the flow is **Details to Template to Amounts to
Documentation (optional) to Confirm**:

- No separate **eligible-source** step: the operation is launched from the selected
  subsample (contextual picker in item view), which is the source.
- No **location/destination** step: destination/placement is out of scope for this
  slice; the new sample lands per the normal creation defaults.
- Template is an explicit framework step (adr/0003), not folded into "parameters".

## Out of scope (confirmed)

Per the epic's scope boundaries and as carried into this branch:

- Backend/data-model changes beyond the thin generic endpoint; lineage
  visualisation; reservation/request workflow (RPD-183); consent-status fields.
- Multi-origin operations (Pool); Revive; operations that mutate the origin's own
  fields in place (Passage, Dispose); link-field de-duplication across consecutive
  in-place operations; **list-view entry points** (item-view picker only for now).

## Acceptance-criteria status

The parent ticket's own criteria (review the proposal, document decisions, outline
implementation stories) are satisfied by the shipped ADRs `0001`..`0005`, this
branch's implementation, and the mechanics doc. Key decisions and the deviations
above are documented; remaining operations are enumerated under "Out of scope" as
future stories.

## Verification

Frontend pure/logic and component tests and backend unit/validator/MVCIT tests are
listed under "Testing" in `InventoryOperationWizard.md`. Frontend gate for changes
here: `pnpm tsc`, focused Vitest, `pnpm lint`, plus the i18n cycle
(`i18n:check` to `i18n:types` to `i18n:lint`).

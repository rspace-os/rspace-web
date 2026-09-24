# Inventory Operation Wizard: Requirements and Implementation

What RSDEV-1231 (biobank workflow) delivered against the proposal in epic
RSDEV-1228, and where it knowingly departs from it. Requirement-by-requirement
status lives in Jira; the mechanics live in `InventoryOperationWizard.md`, the
rationale in `DevDocs/adr/0011` and the vocabulary in the top-level `CONTEXT.md`.
This file does not repeat them.

## One-line summary

A generic, frontend-declared operation framework: a user picks a subsample, a
modal wizard gathers input, and one atomic backend endpoint creates a new Sample
parenting N subsamples, links them to the origin, and decrements the origin.
Aliquot, Passage, Pool, Derive, Cryopreserve, Revive, and Destroy ship on it.
Adding an operation means writing a Java class and a TypeScript constant, plus a
typed request body and an endpoint; see `InventoryOperationWizard.md`.

## Deviations from the proposed flow

The epic proposed *picker to eligible-source to parameters to location/destination
to confirmation*. As built the flow is **Details to Template to Amounts to
Documentation (optional) to Confirm**:

- No separate **eligible-source** step: the operation is launched from the selected
  subsample (contextual picker in item view), which is the source.
- No **location/destination** step: destination/placement is out of scope for this
  slice; the new sample lands per the normal creation defaults.
- Template is an explicit framework step (DevDocs/adr/0011), not folded into "parameters".

## Out of scope (confirmed)

Per the epic's scope boundaries and as carried into this branch:

- Backend/data-model changes beyond the operation classes and their shared core; lineage
  visualisation; reservation/request workflow (RPD-183); consent-status fields.
- Link-field de-duplication across consecutive in-place operations; **list-view
  entry points** (item-view picker only for now). (Pool, Revive, Passage, and
  Destroy, previously deferred here, now ship on this branch.)

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

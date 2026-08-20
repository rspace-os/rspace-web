# 15. The backend validates operation requests against a copy of the operations config

Date: 2026-08-20

## Status

Accepted. Amends DevDocs/adr/0006 ("operationType is carried for audit/logging only and is
never branched on"). DevDocs/adr/0016 records the stage-2 direction that will replace this
ADR's config-copy mechanism with user-editable definitions; the validation behaviour
decided here survives that replacement.

## Context

`POST /api/inventory/v1/operations` is public API. DevDocs/adr/0006 made the backend
deliberately operation-agnostic: `operationType` is audit-only, and only the
invariants shared by every operation are enforced (origins present and unique,
amount-taken non-negative with a unit, over-removal rejected per DevDocs/adr/0010, edit
permission per origin). Everything that makes a request a *particular* operation
lives only in the frontend: `operations_config.json` plus the wizard's gating.

An API client bypasses all of that. Today the endpoint accepts an `operationType` of
"banana", a cryopreserve stored at +20 °C, a pool with one origin, a derive with no
provenance link, and a destroy that also creates a sample. The frontend rules are
trust-boundary rules and must hold server-side.

Separately, the operations path skips `SampleApiPostValidator` entirely: the manager
calls `createNewApiSample` directly, so an operation-created sample dodges the
checks `POST /api/inventory/v1/samples` applies (reserved field names, temperature
unit validity, min not above max, subsample quantities).

Stage 2 (DevDocs/adr/0016) will make operations user-configurable with validation read from
the user-editable config. Stage 1 must not build validation that stage 2 throws
away.

## Decision

- `operationType` is required and must name a defined operation; anything else is a
  400.
- The backend owns a **verbatim copy** of `operations_config.json` at
  `src/main/resources/inventory/operations_config.json`, parsed once at startup into
  a registry keyed by operation key. Boot fails fast on a missing or malformed file.
  The Jackson model binds only validation-relevant keys and ignores UI-only ones
  (labels, icons, steps, confirm summaries).
- `InventoryOperationPostValidator` interprets the config entry **generically**;
  there is still no per-operation Java:
  - `requiresMultiple`: at least two origins; otherwise exactly one.
  - `noOutput`: `newSample` must be absent; otherwise it is required, with at least
    one subsample, each subsample quantity positive and carrying a real unit.
  - The `newSample` is also run through `SampleApiPostValidator`, closing the gap
    above: an operation-created sample obeys everything the plain samples endpoint
    enforces.
  - `effect.amountTakenFrom` present: every origin's `amountTaken` must be positive.
    Absent: exactly zero (Passage acts on, but does not reduce, its origin).
  - Temperature inputs: the corresponding `storageTempMin`/`storageTempMax` are
    required and checked against `minCelsius`/`maxCelsius` unit-aware via the
    existing `QuantityUtils` comparator, so Kelvin and Fahrenheit values convert
    correctly and a non-temperature unit is rejected (existing
    `RSUnitDef.isTemperature()` machinery).
  - Links: the `newSample` must carry, for each origin, a link field of the
    configured relation type targeting that origin's global id. Extra fields and
    links beyond the definition remain allowed (the sample machinery validates them
    generically). Field *names* are never checked: names are locale-resolved, so a
    name check would reject non-English payloads.
- Live-state rules stay in the controller beside the DevDocs/adr/0010 over-removal pass,
  with the same advisory-under-concurrency contract: every origin's current quantity
  must be positive ("an origin with no amount cannot be operated on"), and an
  `emptiesOrigin` operation's `amountTaken` must equal the origin's current
  quantity.
- One unit test asserts **byte equality** of the frontend and backend config files.
  Any drift is a red build; the fix is copying the file across. The duplication is
  deliberate and temporary (DevDocs/adr/0016).
- The wizard's step gate is fixed to check every origin's quantity rather than only
  the representative one, so the UI never offers a pool run the API rejects.

## Consequences

- Malformed operation requests get field-pathed 400s with message keys under
  `errors.inventory.operation.*`, the same contract as the existing checks.
- Two copies of the config exist until stage 2. The byte-equality test makes the
  sync mechanical: editing an operation now means editing the frontend file and
  copying it across.
- Some frontend rules are structurally unenforceable server-side and are consciously
  left so: Derive's required process name never appears on the wire (it only shapes
  names client-side), and computed values (passage number, disposal date) cannot be
  re-derived cheaply.
- The OpenAPI spec is updated: `operationType` becomes a required enum and the
  per-operation 400 rules are documented.

## Alternatives considered

- **A hard-coded Java enum/spec registry.** Rejected: a second hand-maintained
  representation of the rules with no mechanical drift protection (writing a drift
  test would mean writing this Jackson model anyway), and its constants would be
  rewritten as config parsing in stage 2 regardless.
- **Serving the config from the backend now (single source).** Rejected for stage 1:
  it pulls stage-2 plumbing (frontend loading, caching, test rewiring) into a
  validation-only change. It is the stage-2 shape, recorded in DevDocs/adr/0016.
- **Semantic checks without link enforcement.** Rejected: a derive that links to
  nothing silently loses the provenance trail the operation exists to record.
- **Full lockdown (reject unknown fields, verify field names).** Rejected: field
  names are locale-resolved so name checks break non-English users, and rejecting
  extra fields blocks legitimate API enrichment of the created sample.
- **Tolerating empty origins in multi-origin take-all runs.** Rejected: the
  invariant becomes conditional and hard to state; the UI fix (deselect the empty
  tube) is trivial.

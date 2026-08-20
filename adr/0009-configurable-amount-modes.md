# 9. Configurable amount modes for multi-origin operations

Date: 2026-07-21

## Status

Accepted. Extends adr/0007 (multi-origin operations), which deferred per-origin amounts
("rejected for now... deferred until a real need appears"). That need has arrived.

## Context

adr/0007 gave Pool a single **shared** amount taken from every origin, and built the
wizard's "representative origin" (the smallest) shortcut on that assumption: because the
same amount is removed from each, the smallest origin is the binding constraint, so the
single-origin over-removal / empty checks are correct against it alone. That is only true
while the amount is shared.

Real pooling is not always equal-volume: a user may want to empty every source, or take a
different amount from each. The request needs to make the amount across origins a per-run
choice, expose it as config so future multi-origin operations inherit it, and keep
single-origin operations completely unchanged.

## Decision

- **`takeAmountPerSubsample: boolean` operation config.** Applies only to operations that
  can take multiple origins (`requiresMultiple`); for those it **defaults to true**. When
  true, the amounts step gains an **"amount to take" mode** choice. For single-origin
  operations the flag is ignored and the amounts step is unchanged. Pool sets it true.
- **`defaultAmountMode` operation config.** The mode a multi-origin operation starts on
  before the user changes it or a remembered bundle supplies one. Defaults to **Same
  amount** when omitted; **Pool sets "Take all"**. Ignored for single-origin operations
  (always Same amount), so a stray value can never empty their origin.
- **Three amount modes** (a per-run choice on the amounts step; initial choice per the
  operation's `defaultAmountMode`, above):
  - **Same amount** — the existing UI and behaviour (adr/0007): one shared amount taken
    from every origin, checked against the smallest origin. Unchanged.
  - **Take all** — the shared amount-taken input is disabled and **every origin is emptied
    to zero** (each origin's amount taken is its own full current quantity). The Derived
    Sample's Created amount (count x each-amount) stays independent and its inputs remain
    enabled (adr/0002).
  - **Per subsample** — the shared amount-taken input is disabled and the step shows **one
    amount field per origin**, each starting blank with the unit prefilled to that origin's
    own unit, each validated against **that origin's** own current quantity (per-origin
    over-removal, not the representative one). The step is blocked until every amount is
    entered.
- **The representative-origin shortcut stays only for "Same amount".** "Take all" is never
  over-removal (it takes exactly what is there); "Per subsample" checks each origin
  directly.
- **Remembered process values carry the amount mode and, for "Per subsample", the
  per-origin amounts keyed by origin Global ID.** A re-run restores an amount only for a
  selected origin whose Global ID matches; a Global ID not in the current selection is
  ignored, and a newly-selected origin starts blank (so "Per subsample" only fully restores
  on the same origins). The chosen mode itself is always remembered.
- **Step-one fast path (all operations).** When the wizard opens on a Process name whose
  remembered bundle already forms a complete, valid operation, step one shows the operation
  **confirmation summary and a Perform button** plus a "Review / edit" button that drops
  into the normal stepper. The user can perform a remembered run in one click without
  stepping through. It appears only while the remembered values are valid; an incomplete
  bundle (e.g. "Per subsample" amounts that did not all match this selection) falls back to
  the normal wizard.
- **No backend change.** `origins[]` already carries a per-origin `amountTaken`, and the
  controller already validates each origin against its own loaded quantity, so per-origin
  amounts and take-all map straight through the existing request shape and over-removal
  check.

## Consequences

- Amount handling is now config-driven per operation: a future multi-origin operation
  inherits the mode choice, or opts out with `takeAmountPerSubsample: false`.
- `buildOperationRequest` computes each origin's amount from the mode (shared value / full
  quantity / per-origin map) rather than always the shared value.
- The wizard's over-removal check branches on the mode: representative origin for "Same
  amount", per-origin for "Per subsample", none for "Take all".
- The remembered bundle grows two optional fields (amount mode; per-origin amounts by
  Global ID); older bundles without them normalise to "Same amount", so nothing breaks.
- The step-one fast path is a presentation of step one, not a new step: it reuses the
  confirmation component and the existing submit path (computed values, parent-field loads).

## Alternatives considered

- **Remember per-subsample amounts by subsample name** (instead of Global ID). Rejected:
  names are not unique and can be edited; Global ID is stable and unambiguous. The cost is
  that a re-run on differently-named-but-same-material subsamples recalls nothing, which is
  the safe default.
- **Take-all also drives the pooled sample's amount to the total taken.** Rejected: it
  conflates the Origin decrement with the Created amount, which adr/0002 keeps independent;
  the count/each-amount inputs stay in control of the Created amount.
- **Restrict the step-one fast path to multi-origin operations.** Rejected: a remembered
  single-origin run (e.g. a repeated Derive) benefits equally; the trigger is "a complete,
  valid remembered bundle", not the operation kind.
- **A fourth backend endpoint / per-mode request shapes.** Rejected: the array request
  already expresses per-origin amounts, so the modes are purely a frontend concern.

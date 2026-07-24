# 7. Multi-origin operations (Pool): a shared amount from each, disabled-with-reason picker

Date: 2026-07-20

## Status

Accepted. The shared-amount simplification below (one amount from every origin, checked
against the smallest "representative" origin) is **extended by adr/0009**, which makes the
amount across origins a per-run choice (same amount / take all / per subsample). "Same
amount" keeps this decision unchanged; the other modes take a per-origin amount and so
validate every origin rather than only the representative one.

## Context

The operation wizard was single-origin end to end: launched from exactly one selected
subsample, `buildOperationRequest` emitted one origin and one link, and the amounts step
checked over-removal against that one origin. Pool combines **two or more** subsamples into
one new pooled sample, decrementing each and linking the new sample back to every source.

The backend was already multi-origin ready (adr/0001): `origins` is an array the validator,
over-removal check, and manager all loop over. Only the frontend was single-origin. Three
questions shaped the design: how the amount taken is expressed across origins, how the
config marks an operation multi-origin, and how the picker presents an operation that does
not fit the current selection.

## Decision

- **`requiresMultiple: boolean`** replaces the always-`1/1` `minSelected`/`maxSelected` pair
  (dead weight that only ever meant "single-select"). Pool sets it true. The picker now shows
  **every** operation and enables each for the current selection: a single-origin operation
  for exactly one subsample, a `requiresMultiple` operation for two or more. A disabled
  operation shows the reason as its secondary line (`operations.picker.*`).
- **Pool takes one shared amount from each origin** (equal-volume pooling), not a per-origin
  amount. Consequently every pooled origin must share one measurement category, and the
  amount must not exceed the smallest origin. Mixed-category selections **disable Pool at the
  picker** with a hint, rather than letting the user proceed and fail later.
- **The wizard accepts `origins[]`.** Its representative origin is the one holding the
  **least** material. Because the shared amount is taken from every origin, the smallest is
  the binding constraint, so the existing single-origin checks (over-removal, the empty-origin
  block, unit category) are correct against it unchanged - the multi-origin specifics are
  threaded in only for the request and the links.
- **`buildOperationRequest` fans out:** one origin update per origin (each carrying the shared
  amount) and, per `effect.links` spec, one link per origin - so Pool produces N `HasPart`
  links back. Each link's name interpolates the origin's own name (`{originName}`, e.g.
  "Pooled from: Vial A") so the N names are **distinct** - a record cannot hold two fields
  with the same name. A single-origin operation (one-element array) is unchanged.
- **The pooled sample name seeds from the operation label ("Pool")**, since there is no single
  origin sample to derive from, and **"use this sample's parent template" is disabled** for a
  multi-origin operation (its origins have several, ambiguous, parents).

## Consequences

- Multi-origin is now a config capability: a new `requiresMultiple` operation with a link that
  fans out to each origin needs no wizard or backend change.
- Single-origin operations are untouched: they pass a one-element `origins` array and every
  existing behaviour (naming, template-from-parent, over-removal) is unchanged.
- The shared-amount + same-category rule is a deliberate simplification. Per-origin amounts
  (unequal pooling) would need a per-origin amounts UI and per-origin over-removal; deferred
  until a real need appears.
- The backend is unchanged; the array it always accepted now actually carries more than one
  origin.

## Alternatives considered

- **A separate amount per origin.** Rejected for now: it needs an N-row amounts step and
  per-origin over-removal, and equal-volume pooling is the common biobank case (the ticket
  framed a single amount).
- **Enable Pool on count alone, block mixed categories at the amounts step.** Rejected:
  disabling at the picker with a reason fails earlier and is clearer than a late wall.
- **Keep `minSelected`/`maxSelected`.** Rejected: they were always `1/1`; a single
  `requiresMultiple` boolean captures the only distinction that exists and deletes dead config.
- **A dedicated multi-select entry point separate from the single-subsample one.** Rejected:
  the one Process action now serves both (shown for one or more subsamples), and the wizard's
  picker gates by selection size, so there is one path, not two.

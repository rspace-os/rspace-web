# 5. Amount taken rejects over-removal (revises ADR-0002)

Date: 2026-07-16

## Status

Accepted. Revises the over-taking behaviour of ADR-0002.

## Context

ADR-0002 established the amount taken from the origin as a positive decrement,
applied by `SubSampleApiManager.registerApiSubSampleUsage`, which is unit-aware and
**clamps at zero**. Under that decision, asking to remove more than the origin holds
(e.g. 101 ml from a 100 ml origin) silently clamps the origin to zero.

The RSDEV-1231 changes require the opposite: the user must be *prevented* from
removing more than the origin contains, with clear feedback, rather than silently
zeroing the origin. Silently clamping is surprising and destroys material state the
user did not intend to consume.

## Decision

- **Frontend:** on the amounts step, the wizard blocks progress when the amount taken
  (converted into the origin's own unit, so a cross-unit entry within the same
  category is compared correctly) exceeds the origin's current quantity, showing an
  inline message. This is in addition to the existing "must be > 0" rule.
- **Backend:** the `/operations` endpoint gains a validator that **rejects** a request
  whose amount taken exceeds the origin's current quantity, returning an error rather
  than clamping. This protects direct API callers, consistent with ADR-0001's
  "validate server-side, do not trust the client" stance.
- The zero-clamp in `registerApiSubSampleUsage` remains as defence-in-depth, but it is
  no longer the primary behaviour for over-removal: over-removal is a rejection.
- The "an operation can never increase the origin" invariant from ADR-0002 is
  unchanged; only the handling of over-removal changes from clamp to reject.

## Consequences

- A user cannot accidentally zero out an origin by over-removing; they get a clear
  error and must correct the amount.
- Direct API callers receive a validation error instead of a silent clamp, so the
  API's behaviour matches the UI's.
- The backend validator must load each origin's current quantity to compare
  (unit-aware). This is a read the thin coordinator already has the identifiers for.
- ADR-0002 stays correct except for its final over-taking sentence; that sentence is
  superseded here.

## Alternatives considered

- **Keep clamping (ADR-0002 unchanged).** Rejected: silently zeroing an origin is
  surprising and loses state the user did not intend to consume; the ticket explicitly
  wants over-removal prevented.
- **Frontend-only block.** Rejected: direct API callers would still clamp, so the API
  and UI would disagree; the invariant is enforced server-side too.

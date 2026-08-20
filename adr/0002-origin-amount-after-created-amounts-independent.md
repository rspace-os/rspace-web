# 2. Operations reduce the origin by the amount taken; created amounts are independent

Date: 2026-07-10 (revised 2026-07-14)

## Status

Accepted. The over-taking behaviour (clamp at zero) is revised by ADR-0005, which
rejects over-removal instead of silently clamping.

## Context

The RSDEV-1231 ticket table frames an operation's effect on the origin subsample
as "decrement the original volume by a specific user-defined amount", and implies
the created subsamples' quantities come out of what was removed from the origin.

Two things must hold:

- A user must never be able to **increase** the origin's volume through an
  operation. You can only take material out of a subsample, never conjure it in.
- The created total need not equal what was removed: during Derive, fresh medium
  can be added, so the created subsamples' quantities are independent of the amount
  taken from the origin.

An earlier revision of this decision captured an **absolute amount-after** for the
origin (which could be lower, equal, or higher than the current amount). That
allowed increasing the origin, which is wrong, so it was reversed.

## Decision

- The wizard captures the **amount taken from the origin** (a **positive** quantity,
  > 0), labelled "Amount taken from original". The field starts at 0 and the user
  must enter a positive amount before continuing: an operation must actually remove
  something from the origin. This is enforced in the wizard and in the backend
  validator.
- Each created subsample's amount is an **independent input**, expressed in the
  **same measurement unit as the origin**. The created total need not equal the
  amount taken from the origin.
- The backend **reduces** each origin by its amount-taken, reusing
  `SubSampleApiManager.registerApiSubSampleUsage` (a unit-aware subtraction that
  clamps at zero). An operation can therefore only ever decrease the origin; it can
  never increase it. Over-taking clamps the origin to zero rather than going
  negative.
- The API carries `amountTaken` (a decrement), not an absolute after-value, so the
  "cannot increase" invariant is enforced server-side by construction.

## Consequences

- One uniform model across every operation (reduce-by-a-positive-amount), with no
  special cases and no way to increase the origin.
- Reuses the existing, tested usage-registration primitive, including its unit
  conversion and zero-clamping.
- Re-aligns with the ticket table's "decrement by amount" wording.
- Operations that must leave the origin untouched omit `amountTakenFrom`; the frontend
  then sends that origin with a **zero** amount taken. The backend treats a zero decrement
  as a no-op (`registerApiSubSampleUsage` returns early - no quantity change, no timestamp
  bump), so the origin stays untouched while remaining linked and permission-checked. The
  amount taken is therefore a **non-negative** decrement: positive for operations that
  consume the origin (which the wizard still requires to be > 0), zero for those that only
  link to it (e.g. Passage). Negative is never valid. (Revises the earlier "must be
  positive / no origin update is sent" wording once Passage introduced a link-only origin.)

## Alternatives considered

- **Capture an absolute amount-after** (the previous revision). Rejected: it let an
  operation increase the origin's volume, which must not be possible.
- **Capture a signed delta** ("decrement/increment by X"). Rejected: increment must
  not be allowed, so only a non-negative decrement is meaningful.

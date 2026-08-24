# 7. Inventory operation wizard: consolidated decisions (RSDEV-1231)

Date: 2026-08-24 (consolidates decisions made 2026-07-10 to 2026-08-20)

## Status

Accepted. The single ADR for the operation wizard; consolidates and supersedes
the former ADRs 0006 (frontend-declared operations, thin atomic backend) through
0016. The mechanics of each decision live in the code and its tests; this ADR
records only the WHY and the traps.

## Foundation: operations are config, executed by one thin atomic endpoint

Every operation (Derive, Cryopreserve, Aliquot, Pool, Revive, Passage, Destroy)
is declared in `operations_config.json`: key, label, applicability, wizard
inputs, and declarative effect. One generic backend endpoint,
`POST /api/inventory/v1/operations`, executes whatever mutation the request
describes in a single transaction, orchestrating existing service managers.
There is no per-operation Java, no operationType switch, and no per-operation
wizard code.

Why: adding an operation must be cheap (a config entry plus i18n, no backend
change), and the composite effect (create sample + subsamples + links, decrement
origins) must roll back as a unit. Client-side orchestration of the existing
endpoints was rejected as non-atomic; one endpoint or Java branch per operation
was rejected as defeating the config goal. The backend does resolve
`operationType` against the operation definitions to validate requests (added
2026-08-20), but still generically, interpreting config rather than branching.

## The one thing a future reader must know first

**User-configurable operations are the decided end state, deliberately not built
yet.** The whole framework exists so that operations eventually become
server-owned, user-editable data (storage, editing surface, and authorization are
undecided stage-2 questions). Stage-1 code was written to survive that migration,
which explains its most surprising artifact:

**`operations_config.json` is deliberately duplicated.** The frontend owns the
authoritative copy; the backend holds a verbatim copy at
`src/main/resources/inventory/operations_config.json`, parsed at startup, and a
unit test asserts byte equality of the two files. Editing an operation means
editing the frontend file and copying it across. Do not "fix" this by deleting a
copy or deriving one from the other: the duplication is the cheapest bridge until
stage 2, when the backend serves definitions from a user-editable store and both
checked-in copies plus the drift test are deleted. A hard-coded Java rule
registry was rejected because stage 2 would throw it away and nothing would
mechanically catch drift in the meantime.

## Why the backend validates against that config copy

The endpoint is public API, so every rule the wizard enforces is a trust-boundary
rule and must hold server-side; the validator interprets the config entry
generically, preserving the no-per-operation-Java foundation. Consciously
unenforceable server-side: the process name (never on the wire) and
frontend-computed values (passage number, disposal date). Field names are never
validated because they are locale-resolved; a name check would reject
non-English payloads.

## Origin quantity model

The API carries `amountTaken`, a non-negative decrement, never an absolute
after-value or signed delta: an operation must be structurally unable to
increase an origin's quantity. Over-removal is rejected with an error, not
clamped to zero: silent clamping destroys material state the user did not intend
to consume. Created amounts are independent of the amount taken (fresh medium
can be added during Derive). A zero decrement is a complete no-op, for
operations that link to an origin without consuming it (Passage).

## Computations are a dev-only code registry, not an expression language

Values config cannot express (passage number = parent's + 1, disposal date =
today) come from named pure functions in `operationFunctions.ts`, selected and
fed by config. An expression language in config was rejected as a security
surface (executable logic in data) and unnecessary, since only developers author
operations today; one-off declarative primitives per computation were rejected
as unbounded schema sprawl. Computing on the frontend (not the server) keeps the
backend operation-agnostic: the server never interprets a field as "today".

## Smaller decisions worth keeping

- **Every operation has a process name, even without a visible field** (fixed to
  the operation key when not user-entered): remembered values and derived sample
  naming key off it, and one uniform key beats per-operation branches. A future
  reader will be surprised that Cryopreserve "has a process name"; this is why.
- **Disposal dates are text fields holding an ISO date**: subsample extra fields
  have no date type (text/number/link only), and adding one was out of scope.
- **"Template from the origin's sample" may leave a stray template on failure**:
  template creation happens before the atomic operation transaction. Accepted as
  harmless and deletable rather than adding backend code for marginal atomicity.
- **Multi-origin support, amount modes, and terminal (no-output) operations are
  config capabilities**, added so new operations of those shapes need a config
  entry, not wizard or backend code. Their mechanics are in the config schema,
  wizard, validator, and tests.

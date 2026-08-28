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
undecided stage-2 questions). Stage-1 code was written to survive that migration.

**`operations_config.json` is backend-owned; the frontend has no copy.** The
single authoritative file lives at
`src/main/resources/inventory/operations_config.json`. The backend parses it at
startup (failing fast on a bad build) to validate requests, and serves it
verbatim from `GET /api/inventory/v1/operations/config`; the wizard fetches that
endpoint and validates the payload against its own schema (valibot) before
rendering, so a config/schema mismatch fails when the picker loads, not at
submit. Stage 1 initially duplicated the file into the frontend behind a
byte-equality drift test; review (2026-08-27) rejected frontend ownership — the
backend must validate strictly against a schema it owns — so the frontend copy
and the drift test were deleted. Stage 2 swaps the classpath file for a
user-editable store without changing the endpoint or its consumers. A hard-coded
Java rule registry was rejected because stage 2 would throw it away.

## Why the backend validates against that config copy

The endpoint is public API, so every rule the wizard enforces is a trust-boundary
rule and must hold server-side; the validator interprets the config entry
generically, preserving the no-per-operation-Java foundation.

**The request must match the complete definition (decided 2026-08-28,
superseding the earlier "consciously unenforceable server-side" stance).** The
2026-08-27/28 review overruled partial validation: `operationType` names an
authoritative definition, so the backend rejects, with field-scoped 400s,
anything the definition does not declare (undeclared extra fields on the new
sample or origins, sharing, placement, tags, and other properties the wizard
never sends). No silent stripping: a rejected request tells the caller what to
remove.

Locale-proofing is by identity, not by name. Resolved field names interpolate
user input ({processName}, {originName}), so they can never be matched
server-side; instead every wizard-generated extra field carries its config
identity in a write-only `operationFieldKey` (the definition's
nameKey/fieldNameKey; the optional documentation link uses the fixed key
`operations.documentationLink`). The backend matches keys against the parsed
definition; display names remain unvalidated free text. Server-side resolution
of i18n catalogs was rejected (catalogs are frontend build assets, and
interpolation defeats it anyway).

Computed values (passage number, disposal date) are shape-checked, not
recomputed: each computed `fn` maps to a content rule (`increment` = positive
integer, `today` = valid ISO date). Recompute was rejected because persisted
parent fields are findable only by their localized names, and a server-side
"today" fights client/server timezone boundaries. The one thing that remains
unvalidatable is the process name itself: it is never on the wire, only its
interpolation into free-text display names.

## Origin quantity model

The API carries `amountTaken`, a non-negative decrement, never an absolute
after-value or signed delta: an operation must be structurally unable to
increase an origin's quantity. Over-removal is rejected with an error, not
clamped to zero: silent clamping destroys material state the user did not intend
to consume. An amount finer than the 3 decimal places quantities persist at
(`QuantityInfo`) is rejected the same way, not rounded: the stored decrement
would differ from the validated one (0.0004 ml would take nothing). Created amounts are independent of the amount taken (fresh medium
can be added during Derive). A zero decrement is a complete no-op, for
operations that link to an origin without consuming it (Passage).

## Computations are a dev-only code registry, not an expression language

Values config cannot express (passage number = parent's + 1, disposal date =
today) come from named pure functions in `operationFunctions.ts`, selected and
fed by config. An expression language in config was rejected as a security
surface (executable logic in data) and unnecessary, since only developers author
operations today; one-off declarative primitives per computation were rejected
as unbounded schema sprawl. Computing on the frontend (not the server) keeps the
backend operation-agnostic: the server never interprets a field as "today". It
does shape-check computed content per function (see the validation section
above) without computing it.

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
- **Live-state rules are enforced inside the operation's transaction** (review,
  2026-08-27): the empty-origin, over-removal and must-empty checks run in
  `InventoryOperationManagerImpl` against the same state the mutation sees,
  before anything is written. This supersedes the earlier advisory pre-check in
  the controller's separate read transaction, whose race let a concurrent
  decrement produce a 201 with a silently clamped origin instead of the
  documented 400.
- **The operations endpoint is JSON-only** (`consumes = application/json`): the
  app registers a global YAML message converter, whose laxer parsing (duplicate
  keys, alternate numeric forms) would bypass the JSON contract the validator
  assumes. YAML bodies get HTTP 415.
- **Bean Validation cascades through the request DTO graph** (`@Valid` on
  `newSample`, `origins`, `subSamples`, `notes`), so the image-size and
  note-length constraints ordinary sample creation enforces also hold for
  operation payloads, and an explicit `subSamples` list is capped at 100 like
  `newSampleSubSamplesCount`.

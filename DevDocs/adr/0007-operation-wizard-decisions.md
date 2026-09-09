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

`amountMode` says how the client decided the amount, and turns the amount into a
compare-and-swap guard when the answer is "all of it". A client that means to
empty an origin ("take all", and Destroy, whose whole promise is to empty it)
still serialises the full quantity it read, and declares `amountMode: "all"`.
The endpoint then checks that amount against the live locked quantity and
rejects a mismatch with **409** (`EDIT_CONFLICT`,
`errors.inventory.operation.amountTakenStale`).

409 rather than 400 because nothing about the request is malformed: it was valid
against the state the client saw, and the user typed no amount for them to
correct. The remedy is to reload, not to fix a field. Resolving "all"
server-side was rejected: a Perform must never do something other than what the
user saw, and reading the live quantity at Perform time would silently empty an
origin someone else had just topped up. This replaces the old 400
`mustEmptyOrigin` for a DECLARED whole-origin claim only: that key is still raised when the
mode is absent, because an absent mode earns no compare-and-swap. The structural half of that rule
(an origin-emptying operation may not declare `amountMode: "explicit"`) moved to
the request validator and is still a 400. The comparison is numeric after unit
conversion, so 0.01 l and 10 ml are both "all" of a 10 ml origin. The field is
optional on the wire and absent means EXPLICIT, so every request accepted before
it existed keeps its meaning; absent on an origin-emptying operation is still a
whole-origin claim, because that operation cannot mean anything else.

## An unrecognised property is captured at binding and rejected by the validator

The API's ObjectMapper comes from `Jackson2ObjectMapperBuilder`, which turns
`FAIL_ON_UNKNOWN_PROPERTIES` off, so a key matching no DTO property is dropped
silently before any validator runs. After binding, a dropped key is
indistinguishable from an optional property the caller omitted, so no presence
rule can recover it. That default is right for endpoints promising nothing about
strictness and wrong for this one, whose contract is that the request conforms to
the operation definition: `newSample.storageTemperature`, a plausible typo for
`storageTempMin`/`storageTempMax`, returned 201 with the value absorbed.

Capturing is separated from rejecting because the payload's DTOs are not all
ours. `ApiSampleWithFullSubSamples`, `ApiSubSample` and `ApiExtraField` are bound
by POST /samples and the subsample endpoints too, and Jackson annotations act per
CLASS, not per endpoint. So every object in the payload CAPTURES the names it
does not recognise (`UnknownPropertyCapturing`, one `@JsonAnySetter` on
`IdentifiableNameableApiObject` plus the two operation-owned DTOs), which is
inert: bound values are unchanged, the list is never serialized, and it is
excluded from equals and toString. Only `InventoryOperationPostValidator` reads
it, as one walk of the payload emitting a field-scoped error per captured name,
so no other endpoint's behaviour changes and every unknown property is reported
together with the request's other errors in the one 400.

The capture is declared on the five DTOs the payload contains, NOT on their
shared base class. A single `@JsonAnySetter` on `IdentifiableNameableApiObject`
looked tidier and was wrong twice over: Jackson stops registering a class's
ignorable property names once it has an any-setter, and it deserializes the
any-setter's value before the method can discard it. Both effects would have
applied to every API DTO beneath that base for a feature scoped to one endpoint,
turning a body of junk keys into heap amplification API-wide and quietly killing
the guard that stops `USE_GETTERS_AS_SETTERS` making a read-only collection
writable. The value parameter is `Void` for the same reason: that routes Jackson
to `NullifyingDeserializer`, which skips the subtree as unknown-property
handling did before.

Reporting is bounded twice over. Per object, because each captured name becomes
a field error; and per request, because nothing bounds the number of OBJECTS a
caller sends and the walk deliberately runs before the list ceilings so a typo is
reported even in a structurally broken request. Past the request ceiling the
response says how many went unlisted rather than truncating silently, and each
echoed name is abbreviated, since a property name is bounded only by Jackson's
50,000-character limit.

One consequence worth knowing: Jackson routes a property that is declared but
`@JsonIgnore`d to the any-setter exactly as it routes an invented one, so those
are reported as unknown. That is acceptable because a `@JsonIgnore` property is
absent from every response too, so no client echoing a GET can send one, and a
round-tripped response body is pinned by test to capture nothing.

Rejected: enabling `FAIL_ON_UNKNOWN_PROPERTIES` globally, which breaks clients of
every endpoint that promised nothing; a rejecting `@JsonAnySetter`, which is safe
only on the two endpoint-owned classes and so cannot see `newSample.*` at all,
and which fails on the FIRST unknown key with a message-conversion 400 that
cannot aggregate or name a field path; and a private duplicate of the sample DTO
family, which is a permanent sync burden plus a mapping layer back to
`ApiSampleWithFullSubSamples` for sample creation.

## An operation-generated field is identified by its definition key, not its name

A generated field's NAME is a localized resolution of a definition key
("Passage number" from `operations.passage.numberField`), so it changes with the
locale and with any rewording of a translation. `operationFieldKey` is therefore
persisted on `ExtraField` (nullable; null means "not generated by any operation
definition", which is every hand-created field) and returned on GET, and the
next run of an operation matches the previous generation by key first.

Matching on the name alone forked a lineage into one counter per locale: a
Passage whose previous generation was created under different wording was simply
not found, and `increment` restarted at 1. The localized-name fallback is kept
permanently rather than as migration cover, because it is how a user's own
hand-created "Passage number" (no key at all) is picked up on the first Passage
of an existing culture.

Only the operations endpoint may SET the key, and that is enforced at the single
point which writes the column (`ApiExtraFieldsHelper`), gated on a `@JsonIgnore`
flag that only the operations validator sets — after it has already checked the
key against that operation's own definition. No client can set the flag, so no
other endpoint can persist a key.

Policing it the other way round, by having every other endpoint's validator
reject a non-null key, was tried first and was wrong twice over. It leaked: the
sample- and instrument-template validators never call the shared extra-field
validation at all, so a template request still persisted a forged key, which
then propagated to every sample created from that template. And it broke
read-modify-write: the API returns the key, so any client that GET a sample,
edited one field and PUT it back got a 400 on a field it never touched. A rule
that has to be remembered in six sibling validators is a rule the seventh will
miss.

Ignoring an unverified key is also truthful rather than merely convenient: no
update path can change a persisted key, so there is nothing a caller could have
meant by sending one. The client never echoes it back anyway —
`paramsForBackend` builds an explicit allowlist that omits it.

**Scope.** This covers fields an operation adds as extra fields. Where the
created sample inherits a template field of the same name,
`mergeOperationFieldsIntoInheritedTemplateFields` folds the generated content
into that inherited `InventoryEntityField` and drops the extra field, and that
entity has no such column — so a template-based lineage still falls back to
localized-name matching. Giving `InventoryEntityField` the same column is the
follow-up.

## Operation definitions are validated at registry construction

`InventoryOperationConfigRegistry` rejects a definition that parses yet means
something the request validator does not implement, collecting every violation
into one failure so a bad config file is one round trip to fix. Without it each
such mistake was invisible until a user hit it: an unknown
`originFields[].type` 500s that operation forever, a dangling `contentFrom`
silently disables the field's checks, and an uninterpreted `inputs[].type`
leaves that input's declared constraints unenforced while the definition still
advertises them.

The resolvable value namespace is `inputs[].key` **union** `computed[].into`: a
computed value is written into its own slot rather than into a declared input
(the wizard derives it instead of asking for it), so a generated field's
`contentFrom` legitimately names a computed slot. A `computed[].into` that
collides with an input key is rejected, since it would silently overwrite what
the user entered. The closed sets of interpreted input types and computed
function names live on `InventoryOperationConfig` so the service-layer registry
can use them without importing the controller layer, and a validator test pins
them against the validator's own function table. This becomes the save-time
validation when Stage 2 makes definitions user-editable.

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
- **The template-conformance check also runs inside that transaction** (review,
  2026-09-08): the check itself stays controller code (it delegates to the
  shared samples validator, a controller-layer class the service must not
  import), but the controller hands it to the manager as an
  `InTransactionValidation` callback, run before any origin is read or locked.
  Run in its own transaction it only narrowed the window: a template edited
  between the check and the operation could fail mid-mutation or create the
  sample against a definition different from the one validated.
- **Permission is asserted before the first lock** (review, 2026-09-08): the
  operations manager, the List of Materials lock hoist and the Stoichiometry
  deduction hoist all resolve caller-supplied ids into parent-sample sibling
  sets to lock. Each now asserts (or filters on) the caller's permission per
  record BEFORE taking any lock, so an under-permissioned request cannot lock
  other users' sibling sets and delay their writers until it fails; the
  existing locked re-checks before mutation are retained.
- **Locking and freshness are separate guarantees** (code review, 2026-09-08,
  superseding the 2026-09-03/04 lock bullets). `GenericDao.lockRowForUpdate`
  takes `lock_mode X` on exactly one row of the entity's own table, via a
  scalar `select id ... for update`, and then returns the entity from an
  ordinary `session.get`. It deliberately never locks the loaded entity:
  Hibernate would emit the entity's eager-fetch SELECT (a ~20-table join for
  a subsample: `Container`, `User`, `FileProperty`, `Barcode`, ...) with
  `FOR UPDATE` on the end, taking exclusive locks on rows in every joined
  table (measured: "mysql tables in use 21, locked 20"). Graph-wide locks made
  every earlier ordering claim here meaningless (the join decided the order)
  and deadlocked inventory operations against unrelated features such as
  container moves. `GenericDaoLockScopeIT` pins the narrow scope.

  The returned entity holds the transaction's snapshot: under REPEATABLE READ
  the lock serialises writers but does not make entity reads current. Every
  value that must be computed from the last committed state is therefore read
  as a SCALAR under the lock, which the persistence context cannot serve or
  re-stale: origin quantities in `checkOriginLiveState` and
  `registerApiSubSampleUsage` and the Stoichiometry over-use check
  (`SubSampleDao.getQuantityForUpdate`), the sibling quantities behind a
  parent total (`getActiveQuantitiesForUpdate`), and the UI-settings blob in
  `mergeUiJsonSetting` (`UserDao.getPreferenceValueForUpdate`). An earlier
  refresh-based design (re-read the row under the lock so the entity is
  current) was measured failing: a refreshed sibling reverted to its pre-lock
  value later in the same request, and the refresh discarded a caller's
  unflushed changes.

  Accepted tradeoff of dropping the refresh: the entity's OTHER columns are
  snapshot values too, and with no `@Version` and no `@DynamicUpdate` a flush
  writes all columns, so a locked writer can write a stale unrelated column
  (a name, a description) back over a concurrent committed edit. This is the
  same field-level last-write-wins every unlocked write path already has
  (`InventoryEditLockTracker` does not exclude a user's own concurrent
  requests, and there is no If-Match anywhere in api/v1); the refresh had
  been shielding only these three call sites from it, by accident. Fixing it
  properly is a global optimistic-locking/`@DynamicUpdate` change, which the
  no-mapping-changes constraint rules out here; it belongs to the separate
  concurrency ticket alongside the If-Match discussion.
- **Lock acquisition order in an operation**: first every distinct parent
  sample's subsample rows as a set, ascending by sample id (the locked scalar
  read in `SampleApiManager.recalculateTotalFromLockedRows`); then each origin
  row, ascending by subsample id (a re-ask for a row the sibling set already
  holds); then the parent sample rows themselves, ascending, matching every
  other writer's subsample-then-sample order. The sibling set comes first
  because each origin's own row belongs to it: acquiring the set after the
  per-origin locks means two operations on two siblings each hold the row the
  other wants, and InnoDB kills one (measured: 30 deadlocks per run).
- **The parent total is recomputed from locked rows, not from sibling
  entities** (code review finding 2, closed 2026-09-08):
  `SubSample.setQuantity`'s cascade still sums the sibling entities, which a
  transaction sees as of its own snapshot, so two operations on two siblings
  of one sample each computed the total from stale stock (reproduced: children
  held 14, stored total 17). `recalculateTotalFromLockedRows` re-derives the
  total from `getActiveQuantitiesForUpdate` scalars and assigns it onto the
  sample so the commit flush writes it over the cascade's value.
  `parallelAliquotsOnSiblingSubSamplesKeepTheParentTotalExact` in
  `InventoryOperationsApiControllerMVCIT` settles it (proven red-green on
  2026-09-08 against the pre-narrowing tree: both requests 201, total exact,
  0 deadlocks, and failing with the recompute call removed; not yet re-run
  since the lock was narrowed). The total stays denormalised because deriving it on read
  would touch every sample listing and search projection. The 409 mapping in
  `ApiControllerAdvice` stays as the fallback for anything the lock does not
  cover. `InventoryEditLockTracker` is not a substitute: it is process-local
  and treats a same-user re-lock as an extension.
- **Every stock decrement takes the row lock, not just the operations one**
  (code review, 2026-09-04): Stoichiometry deduction and List of Materials
  usage decrement through the same `registerApiSubSampleUsage`, so the locked
  read lives there and all three callers share it rather than each learning to
  lock. The operations path locks the same row twice in one transaction, which
  is harmless: the lock statement is a scalar query, so a repeat never
  refreshes or upgrades the entity and a caller's unflushed changes survive.
  Stoichiometry and List of Materials follow the same canonical acquisition
  order as the operations endpoint (2026-09-08): every distinct parent
  sample's sibling set up front, ascending by sample id, then the individual
  rows. The sibling-set lock is row locks on all of a sample's subsamples, so
  once the sets are held every later row lock is a re-acquisition and cannot
  form a deadlock cycle across the three writers. Stoichiometry additionally
  processes its links ordered by inventory record id; a lock failure still
  aborts the whole deduction rather than becoming one failed row (Hibernate
  leaves the session unusable afterwards) and surfaces as the 409 the
  Stoichiometry refresh-and-retry UI already handles, which remains the
  backstop for anything unforeseen (e.g. rows outside the set query, such as
  deleted or quantity-less subsamples). The
  whole-value `POST /userform/ajax/preference` path takes no lock, so until
  every client sends a key, a cached-JS tab writing the whole UI settings blob
  can still clobber a concurrent keyed merge.
- **The lock-taking transactions run at READ COMMITTED** (live test against
  MariaDB 12.3, 2026-09-08). Every writer above does at least one plain read
  before its first `FOR UPDATE` (resolving an origin's parent sample, loading a
  link, looking the user up). Under REPEATABLE READ that read fixes the
  transaction's snapshot, and MariaDB's `innodb_snapshot_isolation` (on by
  default from 11.6) then makes a locking read of a row committed since fail
  with error 1020 instead of waiting: the second of two concurrent writers got
  a 409 on every contended row (C1 to C14 and the keyed preference merge in
  the live run; 87 of them, 0 deadlocks), while on 10.11 the same code
  serialised correctly. At READ COMMITTED the read view closes after each
  statement, so the locking read that waited sees the committed row and the
  lock order above does its job on both versions. Set per method in the
  `txAdvice` of `applicationContext-service.xml` and its test twin
  (`performOperation`, `deductStock`, `createNewListOfMaterials`,
  `updateListOfMaterials`, `mergeUiJsonSetting`), pinned by
  `StockWriterTransactionIsolationTest`. Nothing else changes: the scalar
  locked reads still supply freshness, and entity reads inside these
  transactions were already treated as snapshot values.
- **Category and precision rules also apply to the created subsamples** (code
  review, 2026-09-03): the amount taken must be a real amount unit in the
  origin's category; each new subsample quantity must be in the origin's
  category (or the template's, when a template is chosen, since the wizard then
  offers the template's units) and storable at 3dp; a new subsample carries
  only its quantity, so `name` and `iconId` are undeclared like the rest. The
  top-level `newSample.quantity` is not cross-checked against the children: the
  server derives the total from the children and the wizard computes it in
  floating point, so an equality rule would reject the wizard's own requests.
- **The documentation link targets an ELN document, notebook or Gallery file**
  (the set `ElnFolderBrowser` already offers); a link "documented by" an
  Inventory record is rejected.
- **Process is offered in every Inventory context menu except the picker**:
  epic RSDEV-1228 names both the list view and the item view as entry points,
  so the shared action list is not narrowed to item-view menus.
- **The operations endpoint is JSON-only** (`consumes = application/json`): the
  app registers a global YAML message converter, whose laxer parsing (duplicate
  keys, alternate numeric forms) would bypass the JSON contract the validator
  assumes. YAML bodies get HTTP 415.
- **Bean Validation cascades through the request DTO graph** (`@Valid` on
  `newSample`, `origins`, `subSamples`, `notes`), so the image-size and
  note-length constraints ordinary sample creation enforces also hold for
  operation payloads, and an explicit `subSamples` list is capped at 100 like
  `newSampleSubSamplesCount`. `origins` carries the same cap at binding, so an
  over-long list is rejected before Jackson materialises and cascades over every
  element (code review, 2026-09-03).
- **Sample-name uniqueness ignores soft-deleted samples** (code review,
  2026-09-03): the wizard de-duplicates a derived sample name against
  `SampleApiManager.nameExistsForUser`, which counted deleted samples and so
  kept suggesting a suffix for a name that was actually free. The DAO query now
  excludes them. This also changes the already-shipped advisory check behind
  `POST /samples`: deleting sample "X", creating a new "X", then restoring the
  deleted one now leaves two live samples of that name for one owner. There is
  no unique constraint on `Sample.name`, so nothing breaks; the advisory check
  was never a guarantee.

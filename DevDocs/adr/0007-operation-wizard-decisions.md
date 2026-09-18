# 7. Inventory operation wizard: consolidated decisions (RSDEV-1231)

Date: 2026-08-24 (consolidates decisions made 2026-07-10 to 2026-08-20)

## Status

Accepted, and amended three times since. The single ADR for the operation
wizard; consolidates and supersedes the former ADRs 0006 (frontend-declared
operations, thin atomic backend) through 0016. The mechanics of each decision
live in the code and its tests; this ADR records only the WHY and the traps.

**Read the amendments at the end first.** The sections dated 2026-08-24 below
describe operations as entries in `operations_config.json` executed by one
generic `POST /api/inventory/v1/operations`. Neither survived: the endpoint was
replaced by seven typed endpoints (amended 2026-09-11) and the config by Java
classes (amended 2026-09-16). Superseded sections are marked as such where they
begin, and are kept because the amendments are only legible against the reasoning
they overturned.

## Foundation: operations are config, executed by one thin atomic endpoint

> **Superseded** by [Amended 2026-09-11: the seven typed endpoints are the public API](#amended-2026-09-11-the-seven-typed-endpoints-are-the-public-api)
> and [Amended 2026-09-16: operations are code, not config](#amended-2026-09-16-operations-are-code-not-config).
> Kept for the reasoning it records, not as a description of the system.

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

> **Superseded** by [Amended 2026-09-16: operations are code, not config](#amended-2026-09-16-operations-are-code-not-config).
> Kept for the reasoning it records, not as a description of the system.

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

> **Superseded** by [Amended 2026-09-16: operations are code, not config](#amended-2026-09-16-operations-are-code-not-config).
> Kept for the reasoning it records, not as a description of the system.

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

> **Superseded from here to the end of this section** by D5 of
> [Amended 2026-09-16](#amended-2026-09-16-operations-are-code-not-config):
> `amountMode` and `expectedQuantity` are gone from the wire entirely, and Pool's
> take-all is `takeAll: true` on the body (D4). The `amountTaken` rules above still
> hold.

`amountMode` says how the client decided the amount: `explicit` for a value the
user typed, `all` for a declared claim on the origin's whole quantity (Destroy's
whole promise is to empty its origin). The structural rule stays a 400: an
origin-emptying operation may not declare `amountMode: "explicit"`. The field is
optional on the wire and absent means EXPLICIT, so every request accepted before
it existed keeps its meaning; absent on an origin-emptying operation is still a
whole-origin claim, because that operation cannot mean anything else.

**No concurrency control (RSDEV-1231-no-concurrency).** `amountMode` and the
per-origin `expectedQuantity` are still accepted on the wire and threaded
through the request builder, but neither is compared against a locked read of
the origin's live quantity: this branch strips the row-locking and
compare-and-swap work entirely, so a "take all" or an `expectedQuantity` is
never rejected as stale (409 `EDIT_CONFLICT`) no matter what the origin holds
when the request is processed. Two requests decrementing the same origin from
one read is a real, accepted risk here, not a scenario this branch defends
against; see the branch's own commit history for the removal.

**Off by default (RSDEV-1231).** The wizard and every `/operations` endpoint are
gated on the system property `inventory.operations.available`, seeded `DENIED`,
so a sysadmin turns the feature on per deployment at System > Configuration. The
API answers 404 `CONFIGURED_UNAVAILABLE` while it is off, as
`inventory.available` does, and the Process context-menu entry is hidden.
`DENIED` rather than `DENIED_BY_DEFAULT` so a community admin cannot enable it.

**An edit-session lock, and only that (RSDEV-1231).** What the wizard does take
is the same courtesy lock the subsample edit form takes. When the wizard opens
it locks every origin through `InventoryEditLockTracker`, holds them while it is
open, extends them on each step, and releases them on close; the origins do NOT
enter edit state. `InventoryOperationsApiController` independently locks every
origin AND every distinct parent sample, ascending, outside the manager's
transaction, and releases in a `finally` only the locks it created itself. A
lock held by another user is `InventoryEditLockHeldException`, a 409
`EDIT_CONFLICT` naming the holder.

That closes two UI users Performing on one origin, two UI users Performing on
siblings of one sample, and a wizard racing an open edit form. It does not
close, and is not meant to:

1. The same user in two tabs, or the wizard against their own open form: a
   re-lock by the same user is an extension (`WAS_ALREADY_LOCKED`), which is a
   pass. Identical to the form. The wizard refuses to open when any origin is
   already locked, and the controller's in-flight claim (below) refuses the
   second of two overlapping requests, so this is closed for the UI.
2. Writers that never call `lockItemForEdit`, such as `split` and `duplicate`.
   Pre-existing and out of scope.
3. Two API scripts with no client lock, which are serialised only for the
   seconds the controller holds its locks, i.e. the request duration. Good
   enough for the UI, not a guarantee for automation.
4. Anything outside this JVM. The tracker is process-local, so this is a
   single-node guarantee, like the form's.
5. An abandoned wizard tab, which holds its origins for up to five minutes until
   the tracker expires them. Same as the form.
6. The quantity the user saw versus the quantity the server acts on. A Destroy
   after a colleague's top-up still empties the topped-up amount. Nothing here
   is a compare-and-swap; see the note above.

**An in-flight claim, owner-blind (RSDEV-1231).** `InventoryOperationInFlightOrigins`
is a process-local map of the origins a request is acting on right now. The
controller claims every origin (ascending, all or none) before taking the edit
locks and releases the claim after the manager's transaction has ended. A second
request naming a claimed origin is `InventoryOperationInProgressException`, a 409
`EDIT_CONFLICT`, whoever sent it. Presence is the whole rule: no timing window,
no row lock, no compare-and-swap. A 30-second staleness limit only frees a claim
orphaned by a crash. This is what stops one user's double submit from decrementing
an origin twice from one read, the case the edit lock cannot see.

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

> **Superseded** by [Amended 2026-09-16: operations are code, not config](#amended-2026-09-16-operations-are-code-not-config).
> Kept for the reasoning it records, not as a description of the system.

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

> **Superseded** by [Amended 2026-09-16: operations are code, not config](#amended-2026-09-16-operations-are-code-not-config).
> Kept for the reasoning it records, not as a description of the system.

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
  2026-09-08): the manager calls `OperationTemplateConformanceValidator` itself,
  before any origin is read. It was first passed in from the controller as an
  `InTransactionValidation` callback; once the validator moved to the service
  layer the callback had one caller and one implementation, and was inlined
  (review, 2026-09-16).
  Run in its own transaction it only narrowed the window: a template edited
  between the check and the operation could fail mid-mutation or create the
  sample against a definition different from the one validated.
- **RSDEV-1231-no-concurrency: this branch has no concurrency control.** An
  earlier iteration of this work (RSDEV-1231 proper) added row locking
  (`GenericDao.lockRowForUpdate`), locked-scalar reads for quantities and the
  UI-settings blob, a sibling-set lock ordering to keep a sample's
  denormalised total consistent under concurrent writers, `READ COMMITTED`
  isolation on the writing methods, and a 409 `EDIT_CONFLICT` mapping for a
  detected conflict. All of it is deliberately absent here: permission is
  still asserted per origin before any mutation, and the live-state rules
  (empty origin, over-removal, must-empty, category mismatch) still run
  inside the transaction, but nothing locks a row, nothing compares a request
  against a fresher read, and two requests decrementing the same origin from
  one read can both succeed. See the branch's commit history for what was
  removed and why; a future concurrency effort starts from `rspace-os/main`,
  not from reintroducing these bullets.
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

## Amended 2026-09-11: the server builds the sample

Implemented as DevDocs/adr/0007 steps M1 to M5 (RSDEV-1231). The
client no longer assembles the new sample. The request carries the origins with the
amount taken from each, the values the user typed (`inputs`, keyed by the definition's
input keys), the template and the documentation target. The server validates the inputs
against the definition (`InventoryOperationInputValidator`), builds the sample and every
generated field itself (`InventoryOperationRequestBuilder`, resolving names in the
request's locale) and runs the same transactional core. Why: every rule above that
polices a client-built object graph existed only because the client built it; with the
server building it, ~1,100 lines of interpretive validation, the property rejection
list, the unknown-property capture and the verified flag have nothing to police.

What that changes in the sections above:

- **"Why the backend validates against that config copy"**: the request no longer has
  to match a client-built sample, so the field whitelist matched by `operationFieldKey`,
  the declared-link and declared-text-field rules, the undeclared-property rejection
  list and the computed-content shape checks are gone. Computed values are computed
  server-side (`increment` reads the parent's fields by key, then by localized name;
  `today` resolves in the session's timezone, which the login flow records from the
  browser). Server-side resolution of the i18n catalogs, rejected there, is now how
  names are produced: the catalogs are on the backend classpath (`JsonMessageSource`,
  `inventory:` namespace) and ICU named-argument formatting matches `i18next-icu`. The
  structural validator keeps the origin rules and the documentation target's kind; the
  input validator checks presence, type, `min`/`max`, Celsius bounds and precision by
  the bare input key.
- **"An unrecognised property is captured at binding and rejected by the validator"**:
  removed in full. Nothing a client sends beyond the declared inputs can change what the
  server builds, so an unknown property is ignored exactly as on every other endpoint.
  The five DTOs no longer carry an any-setter, which also restores Jackson's
  ignorable-property handling on them (probed: the API's mapper ignores an unknown
  property, a strict mapper rejects one, and both skip an `@JsonIgnore` or read-only
  property present in the body).
- **"An operation-generated field is identified by its definition key"**: unchanged in
  substance. The key is now READ_ONLY on `ApiExtraField`, so no request on any endpoint
  can set it, and the `operationFieldKeyVerified` flag with its persistence gate in
  `ApiExtraFieldsHelper` is gone: the persisted key is itself the evidence that the
  server generated the field, which is also what the template-field merge keys on.
- **"Bean Validation cascades through the request DTO graph"**: `newSample` and an
  origin's `extraFields` are no longer on the wire (server-side fields the builder
  fills, `@JsonIgnore`), so the cascade covers `origins` only; the shared sample DTOs
  keep their constraints for POST /samples.
- **"Category and precision rules also apply to the created subsamples"**: the created
  amounts come from the `eachAmount` input (unit and precision checked by the input
  validator, category against the origin by the core) and there is no top-level
  quantity on the wire.

## Amended 2026-09-11: the seven typed endpoints are the public API

DevDocs/adr/0007 M6 (RSDEV-1231). The generic `POST /operations` stays
internal and unpublished; the public contract is one typed endpoint per operation,
`POST /operations/<key>`, in the shapes frozen by M0 (DevDocs/adr/0007):
origins by global id, a singular `origin` for the six single-origin operations and
`origins` for Pool, input fields named exactly after the definition's input keys, numeric
`templateId`, `documentedByGlobalId`, an optional per-origin `expectedQuantity`
(accepted on the wire but not enforced; see RSDEV-1231-no-concurrency above), and one
response envelope of the created sample plus each origin's remaining state. The facades
validate shape only
and reuse the structural validator and the manager unchanged; error paths are renamed to
the caller's fields on the way out. Three things the core had to learn for this: an origin
element may carry no `amountTaken` where the definition decides it (Passage takes nothing,
Destroy takes everything, the builder supplies it), a declared input `default` is applied to
an absent optional input, and Cryopreserve's `storageTemp` is now `required` in the config
as M0 specified.

## Amended 2026-09-16: operations are code, not config

RSDEV-1231-no-configuration. Supersedes "Foundation: operations are config,
executed by one thin atomic endpoint", "The one thing a future reader must know
first", "Why the backend validates against that config copy", "Operation
definitions are validated at registry construction" and "Computations are a
dev-only code registry". Everything else in this ADR still holds.

### Why

The config layer existed for a stage 2 that is not happening: user-editable
operations, authored in the app and saved as definitions. Nothing else asked for
it. With that gone, `operations_config.json` was a data format read by an
interpreter (`InventoryOperationConfigRegistry`, `InventoryOperationRequestBuilder`,
`InventoryOperationInputValidator`) that no user could write, whose authoring
mistakes needed their own boot-time validator, and whose seven entries were
written once and never edited. Both languages then parsed that file and could
disagree about it. An operation is a paragraph of Java; expressing it as
interpreted JSON cost three classes, a schema, a registry validator and a
valibot copy, and bought nothing.

### Decisions

- D1 Each operation is a Java class in `com.researchspace.service.inventory.operations`,
  implementing `InventoryOperation<R>`: what it takes from its origins, the value rules
  its request body cannot state, and the request the transactional core executes. The
  six creating operations share `CreatingOperation`. There is no registry: each endpoint
  names its own operation, which is what makes the generics type-safe.
- D2 The manager's transactional core is unchanged and still shared: live-state
  rules, template conformance, create sample, decrement origins, read back.
- D3 Value rules split three ways by what each needs. A rule about one field in
  isolation (presence, bounds, the length of the column it lands in) is a jakarta
  annotation on the request body, where a generated client can see it. A rule needing
  `RSUnitDef` to say what a unit id means is `OperationQuantityRules`. A rule about the
  origin list is `OperationOriginRules`. Anything left is the operation's own `validate`.
- D4 Pool's take-all is `takeAll: true` on the body, not a per-origin mode. The
  server then reads each origin's live quantity at processing time, as Destroy does,
  so the client never has to state an amount it cannot know. An `amountTaken` sent
  alongside is a 400.
- D5 `expectedQuantity` and `amountMode` are gone from the wire. Neither was ever
  compared against anything: both were shape-checked and discarded, which is a
  contract that promises concurrency control and delivers none.
- D6 The wizard's operations are a hand-typed TypeScript array, not a fetched
  document: no request, no schema, no load state, no failure mode. Its label and
  field-name keys are typed as the `inventory:` catalog's own key union, so a
  mistyped key fails at compile time.

### The cost, accepted

The definitions are now two hand-maintained copies, one per language. Nothing
pins the input keys, bounds and defaults together; a drift shows as a 400 at
Perform rather than at build time. This is the same exposure every other endpoint
in this application has with its frontend, and it replaces a runtime interpreter
that could drift in more ways and more quietly.

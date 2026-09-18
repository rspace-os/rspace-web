---
status: accepted
---

# Inventory operation wizard: operations as code, typed endpoints, no row locks (RSDEV-1231)

Date: 2026-09-18. Replaces the branch-local ADR `0007-operation-wizard-decisions.md`,
which recorded three months of decisions as a config-driven design plus the amendments
that overturned it. Only the reasoning that still describes the shipped system is kept
here. The mechanics live in the code and its tests; this ADR records the WHY and the
traps. The developer notes are `DevDocs/DeveloperNotes/InventoryOperationWizard.md` and
`InventoryOperationWizardRequirements.md`.

## Context

Inventory needed seven subsample operations (Aliquot, Passage, Pool, Derive, Cryopreserve,
Revive, Destroy): each creates a sample with subsamples and provenance links and decrements
the origin subsamples, as one unit. The first design declared each operation in
`operations_config.json`, executed by one generic `POST /api/inventory/v1/operations`, with
the client building the sample and the server validating that built object against the
config. That design existed for a stage 2 of user-editable operations that is not
happening. Without it the config was a data format read by a Java interpreter and a
valibot copy, written once and never edited, whose authoring mistakes needed their own
boot-time validator. This branch removed it.

## Decisions

### D1 Operations are code, not config

Each operation is a Java class in `com.researchspace.service.inventory.operations`
implementing `InventoryOperation<R>`: what it takes from its origins, the value rules its
request body cannot state, and the request the transactional core executes. The six
creating operations share `CreatingOperation`. There is no registry: each endpoint names
its own operation, which is what makes the generics type-safe. The wizard's operations
are a hand-typed TypeScript array (`operationsConfig.ts`), not a fetched document: no
request, no schema, no load state. Its label and field-name keys are typed as the
`inventory:` catalog's key union, so a mistyped key fails at compile time.

The cost, accepted: the definitions are two hand-maintained copies, one per language.
Nothing pins input keys, bounds and defaults together, so a drift shows as a 400 at
Perform rather than at build time. Every other endpoint in this application has the same
exposure with its frontend, and it replaces a runtime interpreter that could drift in more
ways and more quietly.

### D2 The server builds the sample

The request carries the origins with the amount taken from each, the typed inputs, the
template and the documentation target. The server builds the sample and every generated
field itself, resolving names in the request's locale from the same catalogs the frontend
uses (`JsonMessageSource`, `inventory:` namespace, ICU formatting matching `i18next-icu`).
Computed values are computed server-side: Passage's `increment` reads the parent's fields
by key, then by localized name; Destroy's disposal `today` resolves in the session's
timezone, which the login flow records from the browser.

Why: about 1,100 lines of interpretive validation, an unknown-property capture and a
property rejection list existed only to police a client-built object graph. With the server
building it, nothing a client sends beyond the declared inputs can shape the result, so an
unknown property is ignored exactly as on every other endpoint.

### D3 Seven typed endpoints are the public API

`POST /api/inventory/v1/operations/<key>`, one per operation, JSON only
(`consumes = application/json`: the app registers a global YAML converter whose laxer
parsing would bypass the JSON contract, so YAML bodies get 415). A singular `origin` for
the six single-origin operations and `origins` for Pool, input fields named after the
definition's input keys, numeric `templateId`, `documentedByGlobalId`, and one response
envelope of the created sample plus each origin's remaining state. The generic endpoint is
gone.

### D4 Value rules split by what each needs

A rule about one field in isolation (presence, bounds, the length of the column it lands
in) is a jakarta annotation on the request body, where a generated client can see it. A
rule needing `RSUnitDef` to say what a unit id means is `OperationQuantityRules`. A rule
about the origin list is `OperationOriginRules`. Anything left is the operation's own
`validate`. Everything needing live state (empty origin, over-removal, one measurement
category, must-empty for Destroy, template conformance) runs in
`InventoryOperationManagerImpl` inside the operation's own transaction, against the same
state the mutation sees. An advisory pre-check in a separate read transaction was tried
and removed: its race let a concurrent decrement produce a 201 with a clamped origin
instead of the documented 400.

### D5 Origin quantity model

The API carries `amountTaken`, a positive decrement, never an absolute after-value or a
signed delta: an operation must be structurally unable to increase an origin. Over-removal
is a 400, not clamped to zero, because clamping destroys material state the user did not
intend to consume. An amount finer than the 3 decimal places `QuantityInfo` persists is
rejected, not rounded, because the stored decrement would differ from the validated one. A
zero amount is rejected everywhere: an operation that takes an amount needs more than
zero, and one that does not (Passage) rejects the field whatever its value. Created
amounts are independent of the amount taken.

Pool's take-all is `takeAll: true` on the body, not a per-origin mode. The server reads
each origin's live quantity at processing time, as Destroy does, so the client never states
an amount it cannot know. An `amountTaken` sent alongside is a 400. `expectedQuantity` and
`amountMode` were removed from the wire: both were shape-checked and discarded, a contract
that promised concurrency control and delivered none.

### D6 No row locks

An earlier iteration added `GenericDao.lockRowForUpdate`, locked scalar reads, a
sibling-set lock ordering for the sample's denormalised total, `READ COMMITTED` on the
writing methods and a 409 for a detected stale read. All of it was deliberately stripped
and must not come back on this branch. Nothing locks a row and nothing compares a request
against a fresher read. The quantity the user saw versus the quantity the server acts on is
an accepted gap: a Destroy after a colleague's top-up empties the topped-up amount. A
future concurrency effort starts from `main`, not from reintroducing those bullets.

What the branch does instead is two process-local, in-memory guards, one per failure mode.

**An edit-session lock (two users).** The wizard takes the same courtesy lock the subsample
edit form takes: on open it locks every origin through `InventoryEditLockTracker`, holds
them while open, extends them each step and releases them on close. The origins do not
enter edit state. The controller independently locks every origin and every distinct parent
sample, ascending, outside the manager's transaction, and releases in a `finally` only the
locks it created. A lock held by another user is a 409 `EDIT_CONFLICT` naming the holder.
Known limits, all shared with the form: writers that never lock (`split`, `duplicate`),
API scripts serialised only for the request duration, a single JVM, and an abandoned tab
holding its origins for up to five minutes.

**An in-flight claim, owner-blind (one user, double submit).** A same-user re-lock is an
extension (`WAS_ALREADY_LOCKED`), so the edit lock cannot see one user's two overlapping
requests, and that pair decrementing an origin twice from one read was the remaining lost
update. `InventoryOperationInFlightOrigins` is a `ConcurrentHashMap` of the origins a
request is acting on right now. The controller claims every origin (ascending, all or
none) before taking the edit locks and releases the claim after the edit locks are given
back, so the claim outlives the manager's transaction. A second request naming a claimed
origin is a 409 `EDIT_CONFLICT`, whoever sent it. Presence is the whole rule: no timing
window, no expiry. A time-based eviction was built and removed: no transaction timeout
bounds the manager call, so a slow request is indistinguishable from an abandoned one, and
guessing would hand a live request's origins to a second one. try-with-resources releases
on every exit, and a process that dies takes the map with it.

On the UI side `ProcessAction` refuses to open the wizard when any origin was already
locked by this user elsewhere, because the holder may be this user's own edit form in
another tab, which will save the quantity it holds over whatever the operation commits.
The wizard also holds a busy ref across the whole open-to-close lifecycle and a submitting
guard on Perform, so a double click cannot start a second acquisition or a second POST.

### D7 Off by default

The wizard and every operations endpoint are gated on the system property
`inventory.operations.available`, seeded `DENIED`, so a sysadmin turns the feature on per
deployment at System > Configuration. The API answers 404 `CONFIGURED_UNAVAILABLE` while it
is off, as `inventory.available` does, and the Process context-menu entry is hidden.
`DENIED` rather than `DENIED_BY_DEFAULT` so a community admin cannot enable it.

### D8 A generated field is identified by its definition key, not its name

A generated field's name is a localized resolution of a key ("Passage number" from
`operations.passage.numberField`), so it changes with the locale and with any rewording.
`operationFieldKey` is persisted on `ExtraField` (null means hand-created), returned on GET
and read-only on the DTO, so no request on any endpoint can set it. The next run of an
operation matches the previous generation by key first, then by localized name. Matching on
the name alone forked a lineage into one counter per locale. The name fallback is kept
permanently because it is how a user's own hand-created "Passage number" is picked up on
the first Passage of an existing culture.

Scope: where the created sample inherits a template field of the same name,
`mergeOperationFieldsIntoInheritedTemplateFields` folds the generated content into that
`InventoryEntityField`, which has no such column, so a template-based lineage still falls
back to name matching. Giving `InventoryEntityField` the same column is the follow-up.

### D9 The remember checkbox marks the form, it never reloads the store

One checkbox on the confirmation governs everything kept for a process name (template
choice, documentation link, amounts) as a single bundle, saved only on a successful
Perform. Ticking it only marks the current form for saving. It must not reload the stored
bundle, because the supported flow is untick, edit, re-tick to save the edited values; an
earlier implementation reloaded the old bundle at re-tick and lost the edits. The bundle is
loaded once when the process name changes to a name that has one, and unticking resets the
form to defaults without deleting what was saved. A bundle is keyed by operation and
process name only, so one saved on a volume origin is offered on a mass one; a restored
amount whose category no longer fits is repaired before it reaches the form.

## Smaller decisions worth keeping

- **Every operation has a process name**, fixed to the operation key when there is no
  visible field: remembered values and derived sample naming key off it, and one uniform
  key beats per-operation branches.
- **Disposal dates are text fields holding an ISO date**: extra fields have no date type.
- **"Template from the origin's sample" may leave a stray template on failure**: template
  creation happens before the operation transaction. Accepted as harmless and deletable.
- **Category and precision rules also apply to the created subsamples**: each new subsample
  quantity must be in the origin's category (or the template's, when one is chosen) and
  storable at 3dp. There is no top-level quantity on the wire; the server derives it.
- **The documentation link targets an ELN document, notebook or Gallery file**; a link to an
  Inventory record is rejected.
- **Process is offered in every Inventory context menu except the picker**: epic RSDEV-1228
  names both the list view and the item view as entry points.
- **Bean Validation cascades over `origins`** only; the shared sample DTOs are not on the
  wire. `origins` carries the 100 cap at binding so an over-long list is rejected before
  Jackson materialises every element.
- **Sample-name uniqueness ignores soft-deleted samples**: the wizard de-duplicates a
  derived name against `SampleApiManager.nameExistsForUser`, which counted deleted samples
  and kept suggesting a suffix for a free name. The DAO query now excludes them. This also
  changes the advisory check behind `POST /samples`; there is no unique constraint on
  `Sample.name`, so nothing breaks.
- **The wizard remounts on every open** (`key` toggled with `open` in `ProcessAction`), so
  no state from a cancelled run survives into the next one.

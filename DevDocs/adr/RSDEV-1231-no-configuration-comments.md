# Comments relocated out of source on branch RSDEV-1231-no-configuration

Per-branch relocation bin from /trim-comments. Not a numbered ADR. Each entry is a comment
removed from source because it duplicated an existing ADR or was review-tool provenance,
anchored on the class or method it sat on.

## src/main/java/com/researchspace/api/v1/InventoryOperationsApi.java

- `InventoryOperationsApi` (class javadoc, second paragraph) — one endpoint per operation, singular/plural origins, one response envelope, "a new operation is a new Java class plus one shape here". Already ADR 0011 D1 and D3.

## src/main/java/com/researchspace/api/v1/controller/InventoryOperationsApiController.java

- `InventoryOperationsApiController` (class Javadoc) — removed the class-level summary; its one
  non-redundant clause, "no per-operation logic lives here: each endpoint names its operation and
  nothing else", is already ADR 0011 D1/D3, which the comment itself pointed at.

## src/main/java/com/researchspace/api/v1/model/ApiExtraField.java

- `operationFieldKey` (field javadoc, trimmed) — removed the `{processName}`/`{originName}` examples and "the next run of an operation recognises the previous generation's field by this key". Already ADR 0011 D8.

## src/main/java/com/researchspace/api/v1/model/ApiInventoryOperationRequests.java

- `ApiInventoryOperationRequests` (class javadoc) — described the seven typed endpoints as the public API contract and the split between annotation-expressible rules and operation-class rules. Already ADR 0011 D3 and D4.
- `Pool` (class javadoc) — described `takeAll` taking every origin's whole live quantity at processing time, with no per-origin amount allowed. Already ADR 0011 D5.
- `Destroy` (class javadoc) — "empties the origin and creates nothing (200, not 201)". Already ADR 0011 D3 plus the controller's own response handling.

## src/main/java/com/researchspace/model/inventory/field/ExtraField.java

- `ExtraField.operationFieldKey` — removed two Javadoc paragraphs that restate ADR 0011 D8: that only the server sets the key (`ApiExtraField.operationFieldKey` is read-only on the DTO, so a client value is dropped at binding with no second check at the write point), and that the column only covers new extra fields, a field merged into an inherited template field having no such column and falling back to name matching.

## src/main/java/com/researchspace/service/inventory/InventoryEditLockHeldException.java

- `InventoryEditLockHeldException` (class javadoc) — "A conflict the caller can retry once the holder is done, not a bad request: the API boundary maps it to 409." Removed from source: already stated in `DevDocs/adr/0011-inventory-operation-wizard.md`, which records that a lock held by another user is a 409 `EDIT_CONFLICT` naming the holder.

## src/main/java/com/researchspace/service/inventory/impl/InventoryOperationInFlightOrigins.java

- Class javadoc: the second paragraph (no age-based expiry, why a time-based eviction was removed,
  try-with-resources on every exit, process-local) and the "the edit-session lock cannot, because a
  re-lock by the same user is an extension" clause were removed as duplicates of ADR 0011, section
  "An in-flight claim, owner-blind (one user, double submit)". The surviving javadoc points there.

## src/main/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImpl.java

- `checkOriginLiveState` — the enumeration of the live-state rules ("every origin must currently
  hold something, the amount taken may not exceed what an origin holds, and an origin-emptying
  operation (e.g. Destroy) must take exactly what the origin holds", plus "violations surface as
  the same field-scoped 400 (BindException) the structural validator produces, under
  `origins[i]` in request order") duplicated ADR 0011 D4/D5 and was removed from the javadoc; the
  ADR pointer and the permission-ordering sentence were kept.

## src/main/java/com/researchspace/service/inventory/operations/InventoryOperation.java

- `InventoryOperation` (interface javadoc) — "Operations are code, not configuration (DevDocs/adr/0011)", each implementation owning its request body, value rules and built sample. Already ADR 0011 D1.
- `validate` (javadoc) — the value rules the request body's annotations cannot express (anything needing `RSUnitDef`, a cross-field comparison, the operation's own semantics). Already ADR 0011 D4.

## src/main/java/com/researchspace/service/inventory/operations/PassageOperation.java

- `nextPassageNumber` — the Javadoc paragraph "Matched by operation key first (stable across locale
  and rewording), falling back to the localized name, which picks up a field the user created by
  hand" duplicated ADR 0011 D8 ("A generated field is identified by its definition key, not its
  name"); removed from source, already recorded there.

## src/main/webapp/ui/src/Inventory/components/ContextMenu/ContextActions.tsx

- `ContextActionsArgs.processAvailable` — "inventory.operations.available is ALLOWED. Seeded DENIED,
  so Process is off by default" duplicated ADR 0011 D7 (the system-property gate and its DENIED
  seed); removed from source, already recorded there.

## src/main/webapp/ui/src/Inventory/components/ContextMenu/ProcessAction.tsx

- `openWithOriginsLocked` (WAS_ALREADY_LOCKED branch, trimmed) — removed "Refusing to start is the only guard: the lock cannot tell the two sessions apart". Already ADR 0011 D6.

## src/main/webapp/ui/src/Inventory/components/ContextMenu/__tests__/ContextActions.process.test.tsx

- `is offered in every menu that acts on records, and never in the picker` — the picker chooses a record for another form, so an operation run from it would leave that form pointing at consumed material. Already ADR 0011 "Smaller decisions worth keeping" (Process is offered in every Inventory context menu except the picker).
- `is hidden while inventory.operations.available is not ALLOWED` — the property is seeded DENIED so a sysadmin turns the feature on. Already ADR 0011 D7.

## src/main/webapp/ui/src/Inventory/components/ContextMenu/__tests__/ProcessAction.test.tsx

- `describe("ProcessAction lock acquisition")` — "The wizard holds the edit-session lock on each
  origin for as long as it is open (RSDEV-1231)." Restates ADR 0011 D6 (edit-session lock);
  deleted from the test file.

## src/main/webapp/ui/src/Inventory/components/Operations/OperationWizard.tsx

- `stateForKey` — removed comment restating ADR 0011 D9: a bundle is keyed by operation +
  process name only, so one saved on a volume origin is offered on a mass one, and a restored
  amount whose category no longer fits is repaired before it reaches the form.
- `onRememberChange` — removed comment restating ADR 0011 D9: ticking only marks the current
  form for saving and must never reload the stored bundle, because the supported flow is
  untick, edit, re-tick.

## src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationWizard.lockLifecycle.test.tsx

- `describe("OperationWizard lock renewal and close ordering")` — removed the describe Javadoc
  carrying the ticket citation `(RSDEV-1231)`; it was attached to the statement that a renewal POST
  recreates a five-minute lock and so must be ordered against the close's release. Each test's own
  comment still states its failure mode.

## src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationWizard.test.tsx

- `OperationWizard remember bundle` > `resets a restored bundle's amounts when its units belong to
  another category` — the comment explaining that a bundle is keyed by operation + process name
  only, so one saved on a millilitre origin is offered on a gram one and would arm one-click
  Perform on a request the endpoint rejects (`amountTakenCategoryMismatch`). Already recorded in
  `DevDocs/adr/0011-inventory-operation-wizard.md`, decision D9; removed from source.
- `blocks Next on the amounts step when the amount taken exceeds the origin (over-removal)`,
  `blocks the details step for Pool when ANY pooled origin is empty, not just the smallest`,
  `sends Destroy as a whole-origin claim with no inputs, leaving the disposed date to the server`,
  `pre-fills the last-used process name and, on Review / edit, shows its bundle` — inline
  `(DevDocs/adr/0011)` pointers stripped; the rules they pointed at (over-removal is a 400,
  empty origins rejected, disposal date stamped server-side, the step-one fast path) are in
  ADR 0011 decisions D5, D6 and D9.

## src/main/webapp/ui/src/Inventory/components/Operations/__tests__/buildOperationRequest.test.ts

- `it("asks the server to take everything in \`all\` mode, sending no amounts")` — removed
  "the server reads each origin's live quantity at processing time, so the client never has to
  guess what 'everything' was"; this is ADR 0011 D5 (Pool's take-all).

## src/main/webapp/ui/src/Inventory/components/Operations/__tests__/operationValidation.test.ts

- `describe("reconcileRestoredQuantities")` — removed "a remembered bundle is keyed by operation
  plus process name only, so the same bundle can be offered on a different-category origin"; this
  is ADR 0011 D9.

## src/main/webapp/ui/src/Inventory/components/Operations/__tests__/processNames.test.ts

- `describe("processNameDefaultAfterPerform")` — "The wizard calls this only for a remembered
  Perform, so there is no 'remember off' branch: unticking never deletes what was saved" duplicated
  ADR 0011 D9 ("unticking resets the form to defaults without deleting what was saved"); removed
  from source, already recorded there.

## src/main/webapp/ui/src/Inventory/components/Operations/buildOperationRequest.ts

- Module header: "Each amount taken is a positive decrement; the backend rejects taking more than
  the origin holds." Removed as a duplicate of ADR 0011 D5 (Origin quantity model).
- `buildFacadeRequest`, amount block: "An operation that decides what it takes (Passage, Destroy,
  and Pool under takeAll) sends no amount at all: the server reads the origin's live quantity
  instead, and an amount sent alongside is a 400." Removed as a duplicate of ADR 0011 D5, which
  states the take-all wire contract and the 400.

## src/main/webapp/ui/src/Inventory/components/Operations/operations.ts

- `OriginFieldSpec` — removed comment "Subsample custom fields support only text and number (no
  native date type), so a date value is stored as a text field holding an ISO date." Already
  recorded in `DevDocs/adr/0011-inventory-operation-wizard.md` ("Smaller decisions worth keeping":
  *Disposal dates are text fields holding an ISO date: extra fields have no date type*).

## src/main/webapp/ui/src/stores/definitions/ExtraField.ts

- `ExtraFieldAttrs.operationFieldKey` — removed the Javadoc paragraph restating ADR 0011 D8: that a generated field's name is a localized resolution of the key, so a later run of the same operation matches on the key to recognise the previous generation. The read-only/`paramsForBackend` note was kept, truncated to one line.

## src/test/java/com/researchspace/api/v1/controller/InventoryOperationFacadesMVCIT.java

- `InventoryOperationFacadesMVCIT` (class Javadoc) — the class summary restated ADR 0011 D2/D3
  (the seven typed endpoints, the single envelope carrying the created sample and each origin's
  remaining quantity, 201 + Location for a creating operation and 200 for Destroy) plus the
  repo-wide convention that an `*IT` class extending a real-transaction MVC base is not run by
  `mvn test`. See `DevDocs/adr/0011-inventory-operation-wizard.md` D2 and D3.

## src/test/java/com/researchspace/api/v1/controller/InventoryOperationsApiControllerMVCIT.java

- `InventoryOperationsApiControllerMVCIT` (class Javadoc) — the class-level narrative of the
  operation endpoints (one POST carries origins, amounts and typed values; the server builds one
  Sample parenting N subsamples, writes a provenance link back to each origin, and reduces each
  origin subsample, all in one transaction) restated ADR 0011 and was removed from source.
- `rejectsTakingMoreThanTheOriginHolds` — "DevDocs/adr/0011: taking more than the origin currently
  holds must be rejected (400), not clamped, and must leave the origin untouched." Duplicated
  ADR 0011; the test name and its assertions already pin the behaviour.

## src/test/java/com/researchspace/api/v1/model/ApiExtraFieldOperationFieldKeyTest.java

- `ApiExtraFieldOperationFieldKeyTest` (class javadoc) — the key, not the localized name, is a generated field's stable identity; persisted, returned on GET, read-only on the API. Already ADR 0011 D8 and the test method names.

## src/test/java/com/researchspace/service/inventory/InventoryOperationTransactionRuleTest.java

- `noTransactionRuleDeclaresATimeout` — removed Javadoc restating ADR 0011 D6: txAdvice is
  shared by every `*Manager` advisor so a timeout here would cap unrelated manager calls, and
  the in-flight claim is released by its owner rather than on age, so nothing depends on an
  operation being time-bounded. (The first half also survives in the test's assertion message.)

## src/test/java/com/researchspace/service/inventory/OperationFieldKeyPersistenceTest.java

- class Javadoc — removed; restated ADR 0011 D8: only an Inventory operation may persist an
  `operationFieldKey`, enforced at binding via a READ_ONLY DTO property rather than in each
  validator, because the sample- and instrument-template validators never call the shared
  extra-field validation.
- `theKeyCannotBeSetFromJson` — removed comment restating the same D8 point: the forgery route
  is any endpoint binding `extraFields`, so the key is dropped at binding itself.

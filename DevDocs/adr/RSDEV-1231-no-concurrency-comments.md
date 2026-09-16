# RSDEV-1231-no-concurrency: relocated comments

Comments removed from source by `/trim-comments` (Relocate pass) because they were either
bare review-tool citations or near-duplicates of `DevDocs/adr/0007-operation-wizard-decisions.md`.
Kept here for provenance rather than left inline.

## src/test/java/com/researchspace/api/v1/controller/InventoryOperationPostValidatorTest.java

- `InventoryOperationPostValidatorTest` (section header, near `rejectsAmountTakenTooPrecise` / `rejectsMoreOriginsThanTheMaximum`):
  > storage temperature shape and magnitude (Copilot review, PR #1090)
- `InventoryOperationPostValidatorTest` (section header, near `rejectsAmountTakenWithAUnitThatDoesNotExist`):
  > amount-taken unit: must be a real amount unit (code review F4)
- `InventoryOperationPostValidatorTest` (section header, near `rejectsAnAllAmountModeOnAnOperationThatTakesNothingFromItsOrigins`):
  > documentation link target (code review, finding 6)

## src/test/java/com/researchspace/service/inventory/OperationFieldKeyPersistenceTest.java

- `OperationFieldKeyPersistenceTest` (class Javadoc):
  > Only an Inventory operation may persist an `operationFieldKey` (RSDEV-1231).
  >
  > The key is stored as the claim "an operation definition generated this field", and a later run
  > of that operation trusts it to identify the previous generation and continue a computed counter
  > from it. A caller able to set one could therefore have an unrelated field picked up as a previous
  > generation.
  >
  > The rule is enforced at binding: the DTO property is READ_ONLY, so no request body on any
  > endpoint can put a key on a field, and the write point persists whatever the server's request
  > builder set. That is deliberately not the same as asking every other endpoint's validator to
  > reject a key: that was the first design and it leaked, because the sample- and
  > instrument-template validators never call the shared extra-field validation at all (parallel
  > review, C1). A rule that must be remembered in six sibling validators is a rule the seventh will
  > miss.

## src/test/java/com/researchspace/api/v1/controller/InventoryOperationsApiControllerTest.java

- `servesTheOperationDefinitionsVerbatimAsJson`:
  > The frontend has no copy of operations_config.json (DevDocs/adr/0007): the wizard fetches
  > this endpoint and renders whatever the backend's authoritative copy declares.

## src/main/java/com/researchspace/service/inventory/InventoryOperationConfigRegistry.java

- `InventoryOperationConfigRegistry` (class Javadoc):
  > The backend's registry of Inventory operation definitions, parsed once at startup from the single
  > authoritative `operations_config.json` on the classpath (DevDocs/adr/0007; the frontend has
  > no copy and fetches GET /operations/config instead). Construction fails fast on a missing or
  > unparseable file so a bad build cannot boot with an unvalidated public endpoint. Stage 2
  > (DevDocs/adr/0007) replaces this source with user-editable definitions without changing
  > consumers.

## src/main/webapp/ui/src/Inventory/components/Operations/operationsConfig.ts

- File-header comment (above imports):
  > Validates the declarative operation definitions. Operations are data, not code: adding one is a
  > new entry in the backend's operations_config.json, with no frontend change (see DevDocs/adr/0007).
  > The backend owns the only copy and serves it via GET /operations/config; the wizard fetches it
  > (operationsApi.fetchOperationsConfig) and parses it here. The valibot schema is the frontend's
  > source of truth for the config shape; the InventoryOperation type is inferred from it, and
  > parseOperationsConfig throws on an authoring mistake so a bad config fails at fetch, not submit.

## src/main/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImpl.java

- `performOperation` (above `callerValidation.validate()`):
  > The caller's own validation (the controller's template-conformance check) runs FIRST, inside
  > this transaction, before any origin is read: a template changed after an
  > out-of-transaction check could otherwise fail the operation mid-mutation or create the sample
  > against a definition different from the one validated (Copilot review, PR #1090).
- `performOperation` (above `checkOriginLiveState(request, originsById, user)`):
  > Validate-before-mutate, inside this method's own transaction so the rules hold against the
  > same state the mutation sees (not an advisory read in a separate transaction): read each
  > origin, assert edit permission on it and check its live quantity against
  > its amountTaken. Any violation throws before anything is written. See DevDocs/adr/0007.

## src/test/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImplTest.java

- Section header, preceding `performExpectingRejection` / the live-state-rules test block:
  > live-state rules, enforced inside the operation's transaction (DevDocs/adr/0007)

## Stripped citations

Review-tool citation clauses — `(Copilot review, PR #1090)`, `(parallel review, <code>)`, `(Codex review)`, `(code review, finding N)`, `(security review, finding N)`, and similar — removed from comments that otherwise carry real, non-obvious rationale. Only the citation was cut; the rationale stays inline. Format: `anchor` — citation — what the surviving comment is about.

### src/main/java/com/researchspace/api/v1/controller/InventoryOperationPostValidator.java
- `validate` (origins ceiling check) — Copilot review, PR #1090 — ceiling check ordered before cardinality to keep per-origin work bounded
- `validateOrigins` (UNKNOWN amount mode) — parallel review — rejecting UNKNOWN via a catalog key, not a raw deserializer message
- `validateOrigins` (amountMode NOT_APPLICABLE) — parallel review — whole-origin claim on a link-only operation is malformed
- `validateOrigins` (amountTaken deliberately empty case) — parallel review — why the empty-body else-chain branch is left as is
- `validateOrigins` (unit existence check) — code review, finding 4 — unknown unit fails as 422 in the manager, not a field-scoped 400

### src/main/java/com/researchspace/api/v1/model/ApiInventoryOperationRequests.java
- `Count` field javadoc — Codex review, P2 — typed facades were laxer than the generic endpoint (ACCEPT_FLOAT_AS_INT)
- `Pool.origins` field comment — parallel review — origin-count ceiling duplicated at binding time

### src/main/java/com/researchspace/api/v1/model/stoichiometry/StockDeductionRequest.java
- `linkIds` field comment — Copilot review, PR #1090 — element-level @NotNull prevents a malformed request becoming a 500

### src/main/java/com/researchspace/service/inventory/InventoryOperationConfig.java
- class/type javadoc — parallel review, I16 — registry boot-fails on an undeclared wizard-only type

### src/main/java/com/researchspace/service/inventory/SampleApiPostFullValidator.java
- class javadoc — parallel review, L1 — why the validator lives in service.inventory, not the controller package

### src/main/webapp/ui/src/Inventory/components/Operations/WizardTemplatePicker.tsx
- `onChange` handler — Copilot review, PR #1090 — clearing must propagate to the parent to avoid stale template id submission

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/TemplateStep.test.tsx
- "selects no radio and shows no banner" test — parallel review, FE14 — selected-template label moved into the i18n catalog
- "keeps a change made while the template lookup was in flight" test — parallel review, FE6 — stale-closure bug that silently reverted a mid-lookup "remember" toggle
- "abandons a pending template lookup when the step unmounts" test — Greptile P1 — only picker/mode changes should invalidate an in-flight lookup
- "names the blocking fields as a list, not a bare join" test — code review, finding 10 — blocking-fields message assembled via i18n's Intl.ListFormat
- "inflects the sentence for a single blocking field" test — PR #963 review — pluralization decided by the catalog, not hard-coded English
- "reports a failed template lookup" test — Copilot review, PR #1090 — unhandled rejection used to leave the user with just a stopped spinner
- `fromSampleMode` comment block — parallel review, C2 — parent-template check moved to OperationWizard since only the active step mounts
- "announces the wizard's parent-template block as an alert" test setup — parallel review — reversing blockError/parentTemplateError precedence

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/operationsApi.test.ts
- `resolveLabel` test double — parallel review, FE14 — "<label>: <reason>" join comes from the i18n catalog
- "strips a dotted origin path" test — parallel review, A4 — a non-English user previously got an English-appended origin marker

### src/main/webapp/ui/src/Inventory/components/Operations/operationsApi.ts
- module-level javadoc — parallel review, A11 — typing/normalization contract covered by this module's own tests
- origin-marker wording comment — parallel review, A4 — origin marker must go through the catalog
- label/reason join comment — parallel review, FE14 — label/reason join goes through the catalog for locale-correct separators

### src/main/webapp/ui/src/hooks/api/useUiPreference.tsx
- `INVENTORY_OPERATION_PROCESS_VALUES` comment — Codex review, PR #1090 — legacy per-process bundle used to exceed the per-key size cap
- `setUiPreferences` type javadoc — code review, finding 3 — per-key write chains prevent one writer's key clobbering another's
- `useRawUiPreferences` javadoc — Codex review, PR #1090 — useUiPreference only starts reading a key on the next render
- `addAlert` context comment — Codex review, PR #1090 — save failure previously never reached the user via a no-op AlertContext
- `setPref` value-vs-setter comment — parallel review, FE12 — setter used to persist a stringified function object
- POST body comment (server merge) — code review, finding 3 — only the changed key is sent so overlapping writers can't drop each other's key
- `pendingWrites` catch comment — Codex review, PR #1090 — a silently dropped preference save used to be invisible to the user
- alert-raising try/catch comment — Codex review, PR #1090 — a throw here would poison the write chain's promise forever

### src/main/webapp/ui/src/stores/models/__tests__/TemplateModel/quantityCategory.test.ts
- `categoryOf` test helper comment — Copilot review, PR #1090 — templates used to misreport "volume" via the inherited HasQuantity getter

### src/test/java/com/researchspace/api/v1/controller/InventoryOperationsApiControllerTest.java
- converter javadoc — Codex review, PR #1090 — test uses a converter configured like the app's real one for BigDecimal binding
- precise decimal test comment — Codex review, PR #1090 — why USE_BIG_DECIMAL_FOR_FLOATS had to be turned on for the generic endpoint
- `originAfter` javadoc — parallel review, A14 — controller no longer re-reads origins; the manager returns them
- `destroyAnswers200WithANullSampleAndTheOriginAsItStands` test — parallel review, A14 — terminal operation reports a null sample but a non-null outcome
- `performSampleOperation` (template-conformance comment) — Copilot review, PR #1090 — template-conformance check runs before any origin is read
- `performMultiOriginOperation` (originsAfter comment) — parallel review, A14 — originsAfter read inside the manager's own transaction

### src/test/java/com/researchspace/api/v1/model/stoichiometry/StockDeductionRequestBeanValidationTest.java
- class javadoc — Copilot review, PR #1090 — an element-level constraint is needed to avoid a 500

### src/test/java/com/researchspace/service/inventory/OperationTemplateConformanceValidatorTest.java
- class javadoc — parallel review, L1 — check moved out of the controller callback into its own class
- qualifier-naming test javadoc — parallel review, A8 — a renamed bean silently breaks Spring's default-name qualifier match
- `rejectsNewSubSamplesOutsideTheChosenTemplatesCategory` test — code review, finding 5 — unit check must run against every child, not only the aggregate
- `acceptsNewSubSamplesInAnotherUnitOfTheTemplatesCategory` test — code review, finding 5 — template fixes measurement category, not exact unit
- over-long sampleName test comment — parallel review — a name-length gap let an over-long sample name reach Hibernate mid-transaction

### src/main/java/com/researchspace/api/v1/model/ApiInventoryRecordInfo.java
- `tags` field — security review, finding 6 — element-level @NotNull turns null list elements into a 400

### src/main/java/com/researchspace/model/inventory/field/ExtraField.java
- `operationFieldKey` javadoc — parallel review, I3 — template-based lineage falls back to matching localized field name

### src/main/java/com/researchspace/service/inventory/InventoryOperationConfigRegistry.java
- computed-function validation — parallel review, C5 — blank-checking before `Set.of(...)` lookup to avoid a bare NPE
- `validateCountBounds` javadoc — parallel review — validating countFrom bounds against config, not the builder's guard
- `keys()` javadoc — parallel review, Q9 — kept as a test seam despite no production caller

### src/main/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImpl.java
- `performMultiOriginOperation` (originsAfter comment) — parallel review, A14 — single-transaction/single-pass rationale for reading origins after the operation
- `checkOriginLiveState` (category mismatch) — security review, finding 4 — pooling across measurement categories rejected server-side too
- `checkOriginLiveState` (amountTaken category mismatch) — code review, finding 4 — rejecting an amountTaken in the wrong measurement category
- `rejectNewSubSamplesOutsideOriginCategory` javadoc — code review, finding 5 — restricting created amounts to the origin's category without a template
- `amountTakenEmptiesOrigin` (null quantity branch) — parallel review, A9 — kept as a total function despite an unreachable-in-production branch
- `amountTakenLostToRounding` javadoc — review 2026-09-14, Q1a/Q1b — QuantityUtils.subtract now stores the remainder in a finer unit
- `amountTakenLostToRounding` javadoc (NOT COVERED note) — review 2026-09-14, Q1c — re-denomination not announced to the user (product decision)

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationConfirmation.test.tsx
- "reads each per-subsample line" test — code review, finding 10 — line assembled by i18n, not string concatenation
- `describe("the confirmation preview matches the names the server stores")` — parallel review — dropping the old TS buildOperationRequest oracle

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/WizardTemplatePicker.test.tsx
- "tells the parent the selection is gone" test — Copilot review, PR #1090 — clearing the picker needs to notify the parent of the lost templateId

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/operationsConfig.test.ts
- "disables Pool above the backend's 100-origin cap" test — Copilot review, PR #1090 — mirroring the backend's 100-origin cap in the wizard
- `describe("assertComputedValuesValid")` — parallel review, Q11 — missing test coverage for assertComputedValuesValid's throws

### src/main/webapp/ui/src/Inventory/components/Operations/operationsConfig.ts
- `InputSpecSchema` (max field) — parallel review, FE9 — wizard's hand-copied constant vs the server's declared max
- `MAX_ORIGINS` — Copilot review, PR #1090 — mirroring the backend's origin cap so the wizard fails early

### src/main/webapp/ui/src/stores/models/HasQuantity.ts
- `quantityEdited` field javadoc — Codex review, PR #1090 — only sending a quantity the client was actually given
- `populateFromJson` javadoc — review 2026-09-14, C3 — live-edit-drop bug fixed by reassigning quantity on refetch

### src/main/webapp/ui/src/util/__tests__/error.test.ts
- "names which origin failed" test — parallel review, FE11 — including the origin index so a Pool rejection is actionable
- same test, second comment — parallel review, A4 — not appending English to an already-localized server reason

### src/test/java/com/researchspace/api/v1/controller/InventoryOperationsErrorCatalogTest.java
- `sourcesRaisingOperationErrors` javadoc — parallel review — matching files by name prefix instead of listing them one by one
- `everyRaisedErrorCodeHasACatalogEntry` (floor comment) — parallel review — guard uses a count floor, not just non-empty

### src/test/java/com/researchspace/model/units/TemperatureValidationTest.java
- `aTemperatureWithAUnitButNoNumberIsInvalidNotAServerError` — Copilot review, PR #1090 — a null-value shape used to 500 instead of a field-scoped 400

### src/test/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImplTest.java
- `rejectsPoolingOriginsFromDifferentMeasurementCategories` — security review, finding 4 — rejecting a pooled sample spanning measurement categories
- section header before `subSampleOf` helper — code review F4, F5 — retained as "--- measurement categories against the origin's live quantity ---"
- `readsOriginsInAscendingIdOrderAndReportsErrorsAtTheirRequestIndex` — code review, finding 1 — error path names the origin by request index
- `aWithdrawalFarBelowTheOriginsOwnResolutionIsAccepted` — Copilot review, PR #1090 and review 2026-09-14, Q1b — accepting a withdrawal that rounds back to the origin's own unit
- `aWithdrawalWhoseRemainderNeedsASmallerUnitThanEitherOperandIsAccepted` — Codex review, PR #1090 — remainder stepping down to a unit that holds it, avoiding lost stock
- `aSubUnitWithdrawalIsAccepted` — review 2026-09-14, Q1a/Q1b — storing a remainder in a finer unit instead of rejecting ordinary lab work
- `runsTheInTransactionValidationBeforeAnyOriginRead` — Copilot review, PR #1090 — template-conformance check now runs inside the operation's own transaction
- `serverBuiltCollaboratorsWired` setup helper — parallel review, L1 — builder mock accepts whatever it produced, since conformance moved elsewhere

### src/main/java/com/researchspace/api/v1/controller/InventoryRecordValidator.java
- `validateExtraFields` — Copilot review, PR #1090 — null-element dereference in extraFields turning a 400 into a 500
- comment above `isValidUnit`, re: operationFieldKey — parallel review, C1/C2 — why operationFieldKey rejection was tried and reverted twice
- `validateTags` — Copilot review, PR #1090 — null-element dereference in tags turning a 400 into a 500

### src/main/java/com/researchspace/api/v1/model/ApiListOfMaterials.java
- `@Size` field javadoc — parallel review, A6 — why the materials list is capped at 250

### src/main/java/com/researchspace/model/units/TemperatureValidator.java
- `validate` — Copilot review, PR #1090 — null numericValue would turn Bean Validation pass into a 500

### src/main/java/com/researchspace/service/inventory/InventoryOperationInputValidator.java
- class javadoc (transaction/500 discussion) — Codex review, PR #1090 — sum-vs-child quantity mismatch failing as 500 rather than field error
- zero eachAmount check — Codex review, P2 — zero eachAmount on created Aliquot amount not previously rejected

### src/main/java/com/researchspace/service/inventory/impl/SampleApiManagerImpl.java
- `mergeOperationFieldsIntoInheritedTemplateFields` javadoc — Codex review, PR #1090 — duplicate-name rejection when a template already declares the generated field's name
- `mergeOperationFieldsIntoInheritedTemplateFields` body — Copilot review, PR #1090 — type-incompatible merge falling back to ordinary duplicate-name rejection

### src/main/webapp/ui/src/Inventory/components/Operations/OperationDetailsStep.tsx
- quantityCategory derivation comment (top of file) — Copilot review, PR #1090 — unit store throwing before GET /units resolves
- integer field validation branch — parallel review, FE10 — cleared integer field failing the Next gate silently
- temperature-below/above-storable checks — Copilot review, PR #1090 — backend-rejected temperature also flagged inline
- UnitSelect endAdornment (Pool -> Per subsample amounts step) — Copilot review, PR #1090 — same unit-store-throw guard for per-subsample amount inputs

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationDetailsStep.test.tsx
- "bounds the count input" test — code review, finding 12 — request builder throwing on fractional/uncapped count
- same test, second comment — parallel review, FE9 — drift between frontend cap and server-side cap surfacing only at Perform
- "flags a per-origin amount" test — parallel review, Q15 — per-origin over-removal check untested end-to-end through the field
- `describe("OperationDetailsStep inline field errors")` — parallel review, Q15 — helper-text precedence untested at the field level

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/buildOperationRequest.test.ts
- file-level comment on why buildOperationRequest tests were repurposed — parallel review — TS model deleted since server builds the sample itself
- "never sends fields for the origin itself" test — parallel review — origin update built and discarded needlessly
- `withUniqueFieldNames` doc comment — parallel review, A10 — TS/Java suffix-format drift risk

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/operationsConfigKeys.test.ts
- file-level comment — parallel review, A5 — catalog-entry coverage gap for untranslated labelKeys

### src/main/webapp/ui/src/Inventory/components/Operations/processValues.ts
- template-choice guard doc comment — Copilot review, PR #1090 — unrecognised persisted template mode silently swapping user's template

### src/main/webapp/ui/src/stores/models/InventoryBaseRecord.tsx
- `fetchAdditionalInfo` finally block — code review, finding 2 — clearing fetchingAdditionalInfo on failure to avoid a stuck rejected promise

### src/main/webapp/ui/src/util/error.ts
- reason-parsing chain — parallel review, FE11 — removed unreachable trailing `.orElse`
- doc comment above origin-index stripping function — parallel review, FE11 — stripped path losing which origin was at fault
- same doc comment, second paragraph — parallel review, A4 — localization concerns with appending English parenthetical

### src/test/java/com/researchspace/api/v1/controller/SampleApiPostValidatorTest.java
- `aNullCollectionElementDoesNotBreakCustomValidation` — Copilot review, PR #1090 — skipping null collection elements to avoid a 500
- same test, invalid-unit comment — Copilot review, PR #1090 — only reporting the invalid-unit error, not stacking unrelated errors
- `aTemperatureWithAUnitButNoNumberIsRejectedNotDereferenced` — Copilot review, PR #1090 — missing numericValue causing a 500 instead of graceful handling

### src/test/java/com/researchspace/service/impl/UserManagerImplTest.java
- `mergeUiJsonSettingRejectsAWellFormedKeyThatNoPreferenceDeclares` — Copilot review, PR #1090 — unbounded key allowlist letting junk accumulate in the settings blob
- doc comment on UI_JSON_SETTINGS_KEYS test fixture — parallel review — reading the real TS file instead of a hand-copied list to catch drift
- `mergeUiJsonSettingRejectsAMergeThatWouldOverflowTheStoredColumn` — Copilot review, PR #1090 — keyed write path pinned against oversized-blob overflow
- `mergeUiJsonSettingRejectsASingleValueAboveThePerKeyCeiling` — parallel review, S6 — per-key ceiling check preventing single-key overflow

### src/test/java/com/researchspace/service/inventory/impl/SampleApiManagerImplTemplateFieldMergeTest.java
- class javadoc — Codex review, PR #1090 — duplicate-name rejection when template already declares the generated field's name
- `leavesAGeneratedFieldAloneWhenTheInheritedFieldRejectsItsContent` — Copilot review, PR #1090 — type-incompatible merge falling through to controlled duplicate-name error

### src/main/java/com/researchspace/api/v1/controller/SampleApiPostValidator.java
- subSample null-check comment — Copilot review, PR #1090 — dereferencing a null subSample element would turn a 400 into a 500

### src/main/java/com/researchspace/api/v1/model/ApiSampleFullPost.java
- class javadoc — parallel review, L1 — why the conformance check lives here instead of in the controller layer

### src/main/java/com/researchspace/service/JsonMessageSource.java
- method javadoc (interpolation) — parallel review, A3 — BigDecimal bounds interpolated bare as numbers

### src/main/java/com/researchspace/service/inventory/InventoryOperationManager.java
- interface javadoc (template-conformance validation) — Copilot review, PR #1090 — why conformance validation runs inside the transaction
- interface javadoc (origins-afterwards) — parallel review, A14 — N+1 transaction problem against the 100-origin cap

### src/main/java/com/researchspace/webapp/config/WebConfig.java
- Jackson converter configuration comment — RSDEV-1231, Codex review, PR #1090 — Double vs DECIMAL(19,3) precision loss

### src/main/webapp/ui/src/Inventory/components/Operations/OperationPicker.tsx
- label resolution comment — parallel review, A5 — config-driven i18n keys checked by operationsConfigKeys.test.ts

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationWizard.preferences.test.tsx
- file header comment — code review, finding 8 — read-merge-write preference save bug this file exists to catch
- "restores a bundle saved only under the new per-operation key" test — Codex review, PR #1090 — selectOperation stale processValues binding bug

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/computedValues.test.ts
- gather-helper test comment — parallel review, I11 — why a spread-based implementation would pass a weaker assertion

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/processValues.test.ts
- `describe("normalizeProcessValues: the stored template choice")` — Copilot review, PR #1090 — unrecognised template mode silently swapping the user's choice

### src/main/webapp/ui/src/Inventory/components/Operations/templateResolution.ts
- `TemplateMode` type javadoc — parallel review, Q16 — duplicate TemplateMode declarations falling out of sync
- `fieldHasDefault` javadoc — Codex review, PR #1090 — link-field default handling matching server-side validation
- `templateBlockReason` javadoc — parallel review, Q10 — duplicated field-mapping/hasDefault rule between wizard and template step disagreeing

### src/main/webapp/ui/src/stores/models/TemplateModel.tsx
- `quantityCategory` getter javadoc (override rationale) — Copilot review, PR #1090 — template quantity always null defaulting to "volume" category
- `quantityCategory` getter javadoc (unit-store ordering) — parallel review — static unit table checked before the unit store

### src/test/java/com/researchspace/api/v1/controller/InventoryOperationFacadesMVCIT.java
- "unit that holds it exactly" test comment — review 2026-09-14, Q1a/Q1b — unit descent stopping at the first unit that fits
- `aPerSubsampleAmountWhoseTotalTheColumnCannotHoldIsRejected` test — Codex review, PR #1090 — parent-total recompute overflow failing mid-transaction as a 500

### src/test/java/com/researchspace/api/v1/controller/SampleApiPutValidatorTest.java
- storage-temp unit validation test — Copilot review, PR #1090 — reporting only the invalid-unit error, not a redundant comparability error

### src/test/java/com/researchspace/service/inventory/FieldNameUniquenessParityTest.java
- class javadoc — parallel review, A10 — suffix-format drift between wizard preview and server-side field naming

### src/test/java/com/researchspace/webapp/controller/UserProfileControllerTest.java
- `updatePreferenceValueTreatsABlankSuppliedKeyAsInvalidNotAbsent` test — Copilot review, PR #1090 — blank supplied key bypassing key validation and locked merge
- 400-status-on-rejected-shape test — Copilot review, PR #1090 — client needing the 400 status to know an update was rejected

### src/main/java/com/researchspace/api/v1/controller/SampleApiValidator.java
- `validatedTemperature` (magnitude) — Copilot review, PR #1090 — magnitude-overflow rationale for temperature validation
- `validatedTemperature` (null numeric) — Copilot review, PR #1090 — returning null on missing numeric value avoids a 500
- `validateSubSampleQuantities` (null-element guard) — Copilot review, PR #1090 — null subSample element dereference would 500

### src/main/java/com/researchspace/api/v1/model/ApiSampleWithoutSubSamples.java
- `extraFields` field — security review, finding 6 — @NotNull on list elements turns 500 into 400

### src/main/java/com/researchspace/service/impl/UserManagerImpl.java
- `MAX_UI_JSON_SETTING_VALUE_CHARS` javadoc (sizing) — parallel review, S6 — sizing rationale for per-key value cap
- `MAX_UI_JSON_SETTING_VALUE_CHARS` javadoc (history) — RSDEV-1231, Codex review, PR #1090 — history of the shared-key overflow bug
- `UI_JSON_SETTINGS_KEYS` javadoc — Copilot review, PR #1090 — closed key set avoids unbounded column growth

### src/main/java/com/researchspace/service/inventory/InventoryOperationRequestBuilder.java
- `amountTakenFor` javadoc — parallel review — no shared-amount mode exists across origins
- `withUniqueFieldNames` javadoc — Codex review, PR #1090 — bug the field-name-uniqueness port fixes

### src/main/java/com/researchspace/webapp/controller/UserProfileController.java
- `updatePreference` key param javadoc — code review, finding 3 — semantics of omitted vs blank key
- `updatePreference` keyed guard (blank key) — Copilot review, PR #1090 — blank key rejected as 400 by the merge
- `updatePreference` keyed guard (status code) — Copilot review, PR #1090 — explicit status code needed on the rejected-update branch

### src/main/webapp/ui/src/Inventory/components/Operations/OperationWizard.tsx
- categoryOf/originCategory setup — Copilot review, PR #1090 — unit-store throw was breaking the context menu
- uiPreferences/bundleFor comment — RSDEV-1231, Codex review, PR #1090 — processValues staleness vs raw context read
- parentTemplateError state comment — parallel review, C2 — parent-template check location fixes fast-path gating
- parentCheckIdRef comment — parallel review, I8 — monotonic token avoids stale lookup overwrite
- parent-template effect early-return comment — Copilot review, PR #1090 — releasing status on abandoned lookup
- setTemplateSelection functional update comment — parallel review, I7 — functional update avoids clobbering concurrent selection change
- restoredTemplateSelection javadoc — parallel review, C3 — falling back to "unselected" when parent template unusable
- reconcileForOrigins comment — parallel review, I10 — undeterminable category must not throw before the empty-origin guard
- bundleFor comment — RSDEV-1231, Codex review, PR #1090 — reading a bundle for an operation not yet bound to state
- closeUnlessSubmitting comment — Copilot review, PR #1090 — closing mid-submit would allow double decrement
- submit() catch block comment — code review, finding 4 — field-scoped errors array vs generic message
- submit() post-success comment — code review, finding 2 — committed operation must not be reported failed
- Perform button disabled comment — Copilot review, PR #1090 — gating Perform on every step, not just confirm

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationWizard.test.tsx
- beforeEach mock reset comment — parallel review — mockReset needed because a test asserts getTemplate never called
- config-load-failure test comment — parallel review, Q12 — load-failed path was previously unreachable in the suite
- parent-template validation test comment — parallel review, C2 — fast-path gating bug reproduction
- retires in-flight check test comment — Copilot review, PR #1090 — stale lookup token bug reproduction
- drops restored template selection test comment — parallel review, C3 — reused bundle with no parent template falls back to unselected
- keeps rest of selection test comment — parallel review, I7 — functional update preserving concurrent selection change
- origin-index alert test comment — parallel review, A4 — catalog-worded origin marker vs concatenated English
- "treats successful Perform as done" test comment — code review, finding 2 — committed POST must not be reported failed on refresh error
- blocks-Cancel-while-Perform test comment — Copilot review, PR #1090 — reopening wizard mid-request could double-decrement origins
- re-gates Perform test comment — Copilot review, PR #1090 — un-ticking remember must re-validate every step
- "reads heading in English" test comment — code review, finding 10 — real catalog proves interpolation, not just the cimode key
- "reads rejected input in English" test comment — parallel review, FE14 — hard-coded ": " separator is English-only
- "reads origin rejection in English" test comment — parallel review, A4 — localized reason plus English-welded aside
- inflects parent-template block test comment — PR #963 review — pluralization can't be a parenthetical "(s)" across locales
- performs-a-Pool test comment — parallel review, Q14 — multi-origin path previously untested; commonQuantity bug it hid
- resets restored bundle amounts test comment — parallel review, C4 — category-mismatch bundle must clear amounts, not default them

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/operationFunctions.test.ts
- increment-guards describe block comment — Copilot review, PR #1090 — Passage number must reject non-whole/negative values silently

### src/main/webapp/ui/src/Inventory/components/Operations/buildOperationRequest.ts
- module-level doc comment — parallel review — deleted dead second implementation of request-building
- `withUniqueFieldNames` doc comment — Codex review, PR #1090 — Pool link name collisions rejected at Perform
- `buildOriginUpdates` doc comment — parallel review — origin fields dropped by the only caller

### src/main/webapp/ui/src/components/Inputs/UnitSelect.tsx
- `Select<number>` comment — Copilot review, PR #1090 — typed Select needs no cast for the unset marker

### src/main/webapp/ui/src/stores/models/__tests__/SampleModel/paramsForBackend.operationFieldKey.test.ts
- file-level doc comment — parallel review, I18 — operationFieldKey must stay inbound-only

### src/test/java/com/researchspace/api/v1/controller/InventoryOperationPostValidatorTest.java
- `rejectsMissingOperationType` — parallel review — dedicated message for missing operationType
- `reportsTheMaximumBeforeTheCardinalityForAnOversizedSingleOriginOperation` — Copilot review, PR #1090 — ceiling error reported before per-origin cardinality error
- `rejectsAnAllAmountModeOnAnOperationThatTakesNothingFromItsOrigins` — parallel review — "all" mode meaningless for a zero-take operation
- `rejectsAnUnrecognisedAmountModeWithAKeyedMessage` — parallel review — catalog-keyed rejection instead of echoing raw client input
- `theWizardsOriginsCapIsTheSameNumberTheEndpointEnforces` — parallel review, FE9 — MAX_ORIGINS pinned between wizard and validator

### src/test/java/com/researchspace/api/v1/model/ApiInventoryOperationPostBindingTest.java
- `bindsAmountModeFromItsLowercaseWireValueAndMarksAnythingElseUnknown` — parallel review — unrecognised amountMode binds to UNKNOWN rather than throwing

### src/test/java/com/researchspace/service/inventory/InventoryOperationConfigRegistryTest.java
- `reportsAMissingComputedFunctionAsAViolationRatherThanCrashing` — parallel review, C5 — missing "fn" reported as a violation instead of a bare NPE

### src/main/java/com/researchspace/api/v1/model/ApiInventoryOperationPost.java
- `inputs` field javadoc — parallel review, A15 — capping the inputs map at 50 entries

### src/main/java/com/researchspace/api/v1/model/ApiSubSample.java
- `extraFields` field — security review, finding 6 — why @NotNull is applied to list elements

### src/main/java/com/researchspace/service/inventory/InventoryFieldNameUniquenessValidator.java
- duplicate-name check loop — Copilot review, PR #1090 — why a null extra-field element is skipped rather than dereferenced

### src/main/java/com/researchspace/service/inventory/OperationTemplateConformanceValidator.java
- class javadoc — parallel review, L1 — history of why the check moved out of the controller
- validator invocation comment — parallel review — ordering of validators relative to origin decrement

### src/main/webapp/ui/src/Inventory/components/ContextMenu/__tests__/ContextDialog.test.tsx
- file header comment — PR #963 review — why userEvent rather than fireEvent is used for backdrop clicks
- "still closes on Escape when disableBackdropClick is set" — parallel review — why the guard narrows on `reason === "backdropClick"`

### src/main/webapp/ui/src/Inventory/components/Operations/TemplateStep.tsx
- `onChange` prop javadoc — parallel review, FE6 — why the updater form is accepted instead of a plain value
- `parentHasTemplate` prop javadoc — parallel review, C2 — why the "use parent template" check lives in the wizard, not the step
- template lookup catch block — Copilot review, PR #1090 — why unhandled rejection needed catching
- `onPickTemplate` — Copilot review, PR #1090 — why clearing the picker resets the selection
- `onPickTemplateRef` layout effect comment — parallel review, FE5 — why the ref is written in a layout effect, not render

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/OperationWizard.unitsLoading.test.tsx
- file header comment — Copilot review, PR #1090 — why the wizard must not throw before the operation picker renders
- "renders the per-subsample amount inputs without dereferencing the unit store" — Copilot review, PR #1090 — why the amounts step must not crash while /units is in flight

### src/main/webapp/ui/src/Inventory/components/Operations/__tests__/operationValidation.test.ts
- cryopreserve fixture definition — parallel review, FE9 — why the fixture declares explicit min/max
- "requires the child count to be a whole number" — code review, finding 12 — 1.5/101 count edge cases
- "rejects a temperature the backend would refuse outright" — Copilot review, PR #1090 — why temperature bounds are checked client-side
- `amountIsStorable` describe block — Copilot review, PR #1090 — why the wizard must block non-storable amounts
- "accepts up to three decimal places" — Copilot review, PR #1090 — floating-point rounding edge case for 1.001
- unit-clearing test — parallel review, C4 — why the unit is cleared rather than defaulted
- "leaves an amount alone when its unit's category cannot be determined" — parallel review, I9 — unknown vs. wrong unit category handling

### src/main/webapp/ui/src/Inventory/components/Operations/operationValidation.ts
- `amountIsStorable` javadoc — Copilot review, PR #1090 — why Next is blocked instead of letting Perform fail
- `amountIsStorable` body comment — Copilot review, PR #1090 — floating-point round-trip rationale
- count-bounds javadoc — parallel review, FE9 — why bounds come from the server definition, not a constant
- temperature validity javadoc — Copilot review, PR #1090 — why the check is pure and shared between gating and inline error
- `wrongCategory` comment — parallel review, I9 — unknown vs. wrong unit category handling
- unit-clearing logic comment — parallel review, C4 — why an unset unit is used instead of defaulting to the origin's

### src/main/webapp/ui/src/hooks/api/__tests__/useUiPreference.test.tsx
- `withAlerts` helper javadoc — Codex review, PR #1090 — why `getRootStore` is no longer a dependency
- "writes one key at a time and never re-reads the whole object first" — code review, finding 3 — per-key write race explanation
- "writes two updates of ONE key in the order they were made" — parallel review, Q13 — why the per-key write chain is required
- "keeps writing a key after one of its writes fails" — parallel review, Q13 — why the `.catch` unblocks the write chain
- "reports a failed write instead of swallowing it" — Codex review, PR #1090 — why a visible alert is needed on write failure
- "keeps writing a key even when raising the failure alert itself throws" — Codex review, PR #1090 — addAlert throwing outside Inventory

### src/main/webapp/ui/src/stores/models/__tests__/SubSampleModel/paramsForBackend.test.ts
- quantity describe block javadoc — Codex review, PR #1090 — why quantity is not echoed back on save
- "is not left outstanding when a mid-edit refetch resets the baseline" — review 2026-09-14, C3 — why clearing quantityEdited corresponds to a new baseline
- "is sent on a create even though the user never touched it" — review 2026-09-14, C3 — why quantity must be sent on create

### src/test/java/com/researchspace/api/v1/controller/InventoryOperationsApiControllerMVCIT.java
- quantity-on-PUT test javadoc — Codex review, PR #1090 — PUT without a quantity leaves the deduction standing
- `passageIntoATemplateThatAlreadyDeclaresTheCounterFieldMergesInsteadOfDuplicating` — Codex review, PR #1090 — merging generated field into a pre-existing template field
- `rejectsPoolingAVolumeOriginWithAMassOrigin` — security review, finding 4 — mixed-category pooling must be rejected server-side too
- `rejectsAnOriginTheCallerCannotEditThroughTheFullStack` — security review, D1 — authz-before-read pinned through the real stack
- section header above the "reproductions" test block — 2026-09-03 — field-scoped 400 reproduction tests
- `rejectsAmountTakenInAUnitThatDoesNotExist` — review repro f4-unknown — unknown unit used to surface as 422
- `rejectsAmountTakenInADifferentCategoryThanTheOrigin` — review repro f4-category — millilitre amount from gram origin used to be 422
- `rejectsANewSubSampleInADifferentCategoryThanTheOrigin` — review repro f5 — millilitre child from gram origin used to be created
- `rejectsANewSubSampleOutsideTheChosenTemplatesCategory` — review repro f5-template — gram children under a volume template used to be created
- `rejectsADocumentationLinkToAnInventoryRecord` — review repro f6 — self-referential IsDocumentedBy link used to be stored
- `rejectsANewSubSampleQuantityFinerThanTheStored3dp` — review repro f7 — 0.0004 used to persist as zero

### src/test/java/com/researchspace/api/v1/model/InventoryOperationFacadeShapesTest.java
- `ACCEPT_FLOAT_AS_INT` javadoc — Codex review, P2 — typed facades quietly laxer than the generic endpoint
- origins minItems assertion comment — parallel review — unbounded origins array must be capped at binding
- OpenAPI spec section header — parallel review, Q17 — section header for published-spec assertions
- published spec javadoc — parallel review, Q17 — a bound left behind is a customer-visible lie

### src/test/java/com/researchspace/model/inventory/ContainerTest.java
- `addsContentToACoordinateNothingHoldsWhenStoredLocationsAreNotContiguous` — live test 2026-09-13, F1 — next coordinate must come from what's in use (max), not from count()+1, since a persisted gap defeats size()-based "next free"

### src/test/java/com/researchspace/service/inventory/InventoryOperationInputValidatorTest.java
- interpolation-safety test javadoc — parallel review, A3 — error messages must not interpolate raw config identifiers
- zero-eachAmount test javadoc — Codex review, P2 — restoring a server-side rule the redesign dropped
- `negativeQuantityIsAFieldErrorOnItsKey` — Codex review, P2 — eachAmount's own "greater than zero" rule
- `anEachAmountThatFitsButWhoseTotalDoesNotIsAFieldErrorOnItsKey` — Codex review, PR #1090 — overflow must be a rejected field, not a 500

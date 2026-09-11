package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryEditConflictException;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationInputValidator;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.session.SessionTimeZoneUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.MapBindingResult;
import tech.units.indriya.quantity.Quantities;

@Service("inventoryOperationManager")
public class InventoryOperationManagerImpl implements InventoryOperationManager {

  @Autowired private SampleApiManager sampleApiMgr;
  @Autowired private SubSampleApiManager subSampleApiMgr;
  @Autowired private InventoryOperationConfigRegistry operationConfigs;
  @Autowired private MessageSource messageSource;

  /** Stateless; one instance per bean, as elsewhere in the codebase. */
  private static final QuantityUtils quantityUtils = new QuantityUtils();

  @Override
  public ApiSampleWithFullSubSamples performOperation(
      String operationKey,
      List<ApiInventoryOperationOriginUpdate> origins,
      Map<String, Object> inputs,
      Long templateId,
      String documentedByGlobalId,
      User user,
      BuiltRequestValidation callerValidation)
      throws BindException {
    InventoryOperationConfig definition =
        operationConfigs
            .get(operationKey)
            .orElseThrow(() -> new IllegalArgumentException("unknown operation " + operationKey));
    // M2 first: an input that fails its declared rule is a 400 before any origin is read. The
    // errors name the bare input key (M0: a typed facade's field IS the key).
    MapBindingResult inputErrors = new MapBindingResult(inputs, "apiInventoryOperationPost");
    InventoryOperationInputValidator.validate(definition, inputs, inputErrors);
    if (inputErrors.hasErrors()) {
      throw new BindException(inputErrors);
    }

    // M1 next: the builder needs each origin's name (link field names), global id (link targets)
    // and its parent's fields (the Passage counter). Read with the same edit assertion the core
    // repeats under lock; nothing here is what the compare-and-swap protects.
    List<InventoryOperationRequestBuilder.Origin> builderOrigins = new ArrayList<>();
    Map<String, ApiQuantityInfo> amountsByGlobalId = new HashMap<>();
    for (ApiInventoryOperationOriginUpdate origin : origins) {
      SubSample subSample = subSampleApiMgr.assertUserCanEditSubSample(origin.getId(), user);
      builderOrigins.add(
          new InventoryOperationRequestBuilder.Origin(
              origin.getId(),
              subSample.getGlobalIdentifier(),
              subSample.getName(),
              subSample.getQuantityInfo() == null
                  ? null
                  : new ApiQuantityInfo(subSample.getQuantityInfo()),
              parentFields(subSample.getSample())));
      if (origin.getAmountTaken() != null) {
        amountsByGlobalId.put(subSample.getGlobalIdentifier(), origin.getAmountTaken());
      }
    }
    InventoryOperationRequestBuilder.LabelResolver resolveLabel =
        InventoryOperationRequestBuilder.messageSourceResolver(
            messageSource, LocaleContextHolder.getLocale());
    ApiInventoryOperationPost built =
        InventoryOperationRequestBuilder.build(
            InventoryOperationRequestBuilder.Params.builder()
                .operation(definition)
                .values(inputs)
                .origins(builderOrigins)
                .resolveLabel(resolveLabel)
                .templateId(templateId)
                // The link's display name is the wizard's fixed "Documented by" label, resolved
                // here in the request's locale exactly like the generated field names.
                .documentationLink(
                    documentedByGlobalId == null
                        ? null
                        : new InventoryOperationRequestBuilder.DocumentationLink(
                            resolveLabel.resolve("operations.documentation.fieldName", Map.of()),
                            documentedByGlobalId))
                // The origin element owns amountTaken (M3 decision), so the builder is given the
                // client's per-origin amounts rather than reading one from the inputs.
                .amountMode(InventoryOperationRequestBuilder.AmountMode.PER_SUBSAMPLE)
                .perSubsampleAmounts(amountsByGlobalId)
                // The session timezone is the browser's, recorded at login (TimezoneAdjuster), so
                // this is the user's local date; an API-key session has none and gets the server's.
                .clientToday(
                    LocalDate.parse(new SessionTimeZoneUtils().formatDateForClient(new Date())))
                .build());
    // The builder decides amounts the way the wizard does (a whole-origin operation snapshots the
    // LIVE quantity); the core must instead compare-and-swap what the CLIENT saw, so every origin
    // carries the client's own amount and mode into the core. Same order in and out: the builder
    // emits one update per origin, in the order given.
    for (int i = 0; i < origins.size(); i++) {
      built.getOrigins().get(i).setAmountMode(origins.get(i).getAmountMode());
      built.getOrigins().get(i).setAmountTaken(origins.get(i).getAmountTaken());
    }
    // Every generated field's definition key is verified by construction: the server just built it
    // from the definition. The single persistence gate (ApiExtraFieldsHelper) drops an unverified
    // key silently, and the template merge in SampleApiManagerImpl skips unverified fields, so the
    // flag the client-assembled path's validator sets is set here instead. M5 removes the flag.
    Stream.concat(
            built.getOrigins().stream().flatMap(origin -> origin.getExtraFields().stream()),
            built.getNewSample() == null
                ? Stream.empty()
                : built.getNewSample().getExtraFields().stream())
        .forEach(field -> field.setOperationFieldKeyVerified(true));
    return performOperation(built, user, () -> callerValidation.validate(built));
  }

  /**
   * The origin's parent sample's fields a computed value may read: its template-defined fields
   * (which carry no definition key) and its ad-hoc extra fields, both, as the wizard's
   * gatherParentFields does.
   */
  private static List<InventoryOperationRequestBuilder.ParentField> parentFields(
      SampleEntity parent) {
    List<InventoryOperationRequestBuilder.ParentField> fields = new ArrayList<>();
    for (InventoryEntityField field : parent.getActiveFields()) {
      fields.add(
          new InventoryOperationRequestBuilder.ParentField(field.getName(), field.getData(), null));
    }
    for (ExtraField field : parent.getActiveExtraFields()) {
      fields.add(
          new InventoryOperationRequestBuilder.ParentField(
              field.getName(), field.getData(), field.getOperationFieldKey()));
    }
    return fields;
  }

  @Override
  public ApiSampleWithFullSubSamples performOperation(
      ApiInventoryOperationPost request, User user, InTransactionValidation callerValidation)
      throws BindException {
    // The caller's own validation (the controller's template-conformance check) runs FIRST, inside
    // this transaction, before any origin is read or locked: a template changed after an
    // out-of-transaction check could otherwise fail the operation mid-mutation or create the sample
    // against a definition different from the one validated (Copilot review, PR #1090).
    callerValidation.validate();
    // Origins are handled in ascending id order (not request order) so two concurrent multi-origin
    // operations over overlapping origins acquire their row locks in one consistent order and
    // cannot deadlock. The validator guarantees unique, non-null ids by this point.
    List<ApiInventoryOperationOriginUpdate> originsById =
        request.getOrigins().stream()
            .sorted(Comparator.comparing(ApiInventoryOperationOriginUpdate::getId))
            .toList();

    // Validate-before-mutate, inside this method's own transaction so the rules hold against the
    // same state the mutation sees (not an advisory read in a separate transaction): read each
    // origin with a row lock, assert edit permission on it and check its live quantity against
    // its amountTaken. Any violation throws before anything is written. See DevDocs/adr/0007.
    checkOriginLiveState(request, originsById, user);

    // Reduce each origin by the amount taken from it BEFORE creating the new sample, so the new
    // subsample is the most-recently-modified record and therefore sorts first in a
    // modification-date-descending listing (registerApiSubSampleUsage stamps each origin's
    // modification date now; the new subsample is stamped later, when created below).
    // registerApiSubSampleUsage subtracts (unit-aware) and clamps at zero, so an operation can only
    // ever decrease the origin, never increase it. Any custom fields the operation adds to the
    // origin itself (Destroy's disposed date) are applied through the ordinary subsample-edit path,
    // each marked newFieldRequest by the frontend. Coordinated inside this manager so it joins the
    // one transaction with the sample creation. See DevDocs/adr/0007.
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.registerApiSubSampleUsage(
          origin.getId(), origin.getAmountTaken().toQuantityInfo(), user);
      if (CollectionUtils.isNotEmpty(origin.getExtraFields())) {
        ApiSubSample fieldUpdate = new ApiSubSample();
        fieldUpdate.setId(origin.getId());
        fieldUpdate.setExtraFields(origin.getExtraFields());
        // Sparse update: null tags means "leave tags untouched"; the DTO's default empty list
        // would be applied as "clear all tags" and silently wipe a tagged origin's tags (same
        // idiom as InventoryIdentifierApiManagerImpl's sparse updates).
        fieldUpdate.setTags(null);
        subSampleApiMgr.updateApiSubSample(fieldUpdate, user);
      }
    }

    // A terminal operation (noOutput, e.g. Destroy) sends no new sample: it only acts on its
    // origins, so there is nothing to create and nothing to return. See DevDocs/adr/0007.
    return request.getNewSample() == null
        ? null
        : sampleApiMgr.createNewApiSample(request.getNewSample(), user);
  }

  /**
   * The live-state rules (DevDocs/adr/0007): every origin must currently hold something, the amount
   * taken may not exceed what an origin holds, and an origin-emptying operation (e.g. Destroy) must
   * take exactly what the origin holds. Permission is asserted BEFORE reading state, so an
   * under-permissioned caller gets an authorization failure, not a misleading "origin empty" 400.
   * Violations surface as the same field-scoped 400 (BindException) the structural validator
   * produces, under {@code origins[i]} in request order.
   *
   * <p>Locking, in acquisition order. First, every distinct parent sample's subsample rows are
   * locked as a set, ascending by sample id, via {@link
   * SampleApiManager#recalculateTotalFromLockedRows}: the recompute of each parent's denormalised
   * total must read the sibling rows currently, and taking them any later would deadlock, because
   * each origin's own row is one of them. Then each origin is locked through {@link
   * SubSampleApiManager#lockSubSampleForEdit}, ascending by subsample id (a re-ask for a row the
   * sibling set already holds, plus the permission check and 404), so a concurrent operation on the
   * same origin waits and then decrements from the committed quantity, not a stale read (code
   * review, finding 1). Each check below reads the origin's quantity as a locked scalar ({@link
   * SubSampleApiManager#getQuantityForUpdate}): the locked entity itself holds this transaction's
   * snapshot, and a check against that would pass on stock a concurrent committer already took.
   * Finally the parent sample rows themselves are locked, ascending, after all subsample rows,
   * matching every other writer's subsample-then-sample order (code review, finding 2).
   */
  private void checkOriginLiveState(
      ApiInventoryOperationPost request,
      List<ApiInventoryOperationOriginUpdate> originsById,
      User user)
      throws BindException {
    boolean emptiesOrigin =
        operationConfigs
            .get(request.getOperationType())
            .map(config -> config.effect().emptiesOrigin())
            .orElse(false);
    BeanPropertyBindingResult errors =
        new BeanPropertyBindingResult(request, "apiInventoryOperationPost");
    // Origins are processed in id order but reported at their REQUEST index. Keyed by identity: a
    // list scan per origin is quadratic at the 100-origin cap, and these are Lombok @Data values,
    // so two equal origins would both resolve to the first one's index.
    Map<ApiInventoryOperationOriginUpdate, Integer> requestIndex = new IdentityHashMap<>();
    for (int i = 0; i < request.getOrigins().size(); i++) {
      requestIndex.put(request.getOrigins().get(i), i);
    }
    // Edit permission is asserted on every origin BEFORE any lock is taken (unlocked read): an
    // under-permissioned caller must not be able to lock other users' sibling sets and delay their
    // writers until this transaction fails and rolls back (Copilot review, PR #1090). The locked
    // per-origin re-check below (lockSubSampleForEdit) still closes the TOCTOU window.
    // The parent ids collected here feed the FIRST locks of the transaction, before any origin is
    // locked. Each origin's own row is one of its parent's sibling rows, so asking for the sibling
    // set after the per-origin locks means two operations on two siblings each hold the row the
    // other wants, and InnoDB kills one. Taking the whole sibling set up front makes the second
    // operation WAIT here instead.
    Set<Long> parentSampleIds = new TreeSet<>();
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      parentSampleIds.add(
          subSampleApiMgr.assertUserCanEditSubSample(origin.getId(), user).getSample().getId());
    }
    parentSampleIds.forEach(sampleApiMgr::recalculateTotalFromLockedRows);

    QuantityInfo firstOriginQuantity = null;
    // Whether any origin's whole-origin claim no longer matches its live quantity. Collected rather
    // than thrown on sight so a field error found elsewhere in the request can be reported instead.
    boolean staleOrigin = false;
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.lockSubSampleForEdit(origin.getId(), user);
      errors.pushNestedPath(String.format("origins[%d]", requestIndex.get(origin)));
      try {
        // A locked scalar, not the locked entity: the entity holds this transaction's snapshot
        // (locking guarantees serialisation only), and checking against that would pass on stock a
        // concurrent committer already took.
        QuantityInfo currentQuantity = subSampleApiMgr.getQuantityForUpdate(origin.getId());
        if (originHoldsNothing(currentQuantity)) {
          errors.rejectValue(
              "id",
              "errors.inventory.operation.originEmpty",
              "An origin subsample that currently holds nothing cannot be operated on.");
        } else if (firstOriginQuantity != null
            && !quantityUtils.isComparableQuantities(firstOriginQuantity, currentQuantity)) {
          // A multi-origin operation (Pool) combines its origins into one quantity, which is
          // meaningless across measurement categories (5 ml + 5 g); the wizard blocks it, so the
          // endpoint must too (security review, finding 4).
          errors.rejectValue(
              "id",
              "errors.inventory.operation.originCategoryMismatch",
              "All origin subsamples must use the same measurement category.");
        } else if (origin.getAmountTaken() != null
            && !quantityUtils.isComparableQuantities(origin.getAmountTaken(), currentQuantity)) {
          // The wizard keeps the amount taken in the origin's own category; grams taken from a
          // millilitre origin would otherwise fail inside the unit-aware subtraction as a 422
          // (code review, finding 4).
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenCategoryMismatch",
              "The amount taken must use the origin's measurement category.");
        } else if (claimsWholeOrigin(origin)
            && !amountTakenEmptiesOrigin(origin.getAmountTaken(), currentQuantity)) {
          // Compare-and-swap, not a validation failure: the client DECLARED this amount was the
          // origin's entire quantity when it read it, so a live quantity that no longer matches
          // means the origin changed between wizard load and Perform. Emptying it anyway would
          // destroy stock the user never saw, and rejecting it as a 400 would tell them to correct
          // a field they never typed, so this is a 409 the client resolves by reloading
          // (RSDEV-1231). Checked ahead of amountTakenExceedsOrigin because in this mode a
          // too-large amount is equally a stale snapshot, not an over-removal the user chose.
          // Recorded rather than thrown here: see the throw after the loop.
          staleOrigin = true;
        } else if (emptiesOrigin
            && !amountTakenEmptiesOrigin(origin.getAmountTaken(), currentQuantity)) {
          // An emptying operation whose client did NOT declare amountMode. Absent mode is not a
          // whole-origin claim, so a mismatch here is a malformed request for this operation, not a
          // conflict: nothing has necessarily changed, the caller simply asked Destroy to take part
          // of the origin. Treating it as a 409 told such a client to reload and retry, which
          // reloads the same quantity and retries forever (parallel review, I1). Requests from this
          // wizard always declare "all", so they take the compare-and-swap branch above.
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.mustEmptyOrigin",
              "This operation must take the origin's entire remaining quantity.");
        } else if (amountTakenExceedsOrigin(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenExceedsOrigin",
              "Cannot take more from an origin than it currently holds.");
        } else if (amountTakenLostToRounding(origin.getAmountTaken(), currentQuantity)) {
          // The submitted scalar fits 3dp on its own, but the post-subtraction quantity may not
          // after unit conversion (0.001 ul from a 1 l origin leaves 999.999999 ml), and
          // registerApiSubSampleUsage would store it rounded, silently losing the decrement
          // (Copilot review, PR #1090). Checked with the same sum the decrement itself uses.
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenNotSubtractable",
              "The amount taken cannot be subtracted exactly from what the origin holds.");
        } else {
          firstOriginQuantity = firstOriginQuantity == null ? currentQuantity : firstOriginQuantity;
        }
      } finally {
        errors.popNestedPath();
      }
    }
    rejectNewSubSamplesOutsideOriginCategory(request, firstOriginQuantity, errors);
    // Field errors take precedence over the conflict. Throwing the 409 the moment a stale origin
    // was
    // seen discarded errors already collected for earlier origins, so a caller with both problems
    // got "reload and retry", reloaded, resubmitted, and only then learned about the 400 (parallel
    // review, I4). A 400 is the more actionable answer and needs no reload.
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
    if (staleOrigin) {
      throw new InventoryEditConflictException("errors.inventory.operation.amountTakenStale");
    }
    for (Long sampleId : parentSampleIds) {
      sampleApiMgr.lockSampleForEdit(sampleId, user);
    }
  }

  /**
   * Without a template the wizard offers only the origin's measurement category for the created
   * amounts, so a gram child from a millilitre origin is a request it never builds (code review,
   * finding 5). With a template the created amounts follow the template's category instead, which
   * the controller's template check enforces; the origin category is not consulted then.
   */
  private static void rejectNewSubSamplesOutsideOriginCategory(
      ApiInventoryOperationPost request,
      QuantityInfo originQuantity,
      BeanPropertyBindingResult errors) {
    ApiSampleWithFullSubSamples newSample = request.getNewSample();
    if (newSample == null
        || newSample.getTemplateId() != null
        || originQuantity == null
        || newSample.getSubSamples() == null) {
      return;
    }
    int index = 0;
    for (ApiSubSample subSample : newSample.getSubSamples()) {
      ApiQuantityInfo quantity = subSample == null ? null : subSample.getQuantity();
      if (quantity != null
          && quantity.getUnitId() != null
          && !quantityUtils.isComparableQuantities(quantity, originQuantity)) {
        errors.rejectValue(
            String.format("newSample.subSamples[%d].quantity", index),
            "errors.inventory.operation.subSampleCategoryMismatch",
            "Each new subsample must use the origin's measurement category.");
      }
      index++;
    }
  }

  /**
   * Whether this origin's submitted amount is a DECLARED claim on the origin's whole quantity
   * rather than a value the user typed, which is what makes it a compare-and-swap guard.
   *
   * <p>Only an explicit {@code amountMode: "all"} counts. An absent mode is not a claim, even on an
   * origin-emptying operation: a client predating the field that asks Destroy for part of an origin
   * is making a malformed request, and answering that with a conflict tells it to reload and retry
   * a request that can never succeed (parallel review, I1). That case is a 400 instead, which is
   * what it was before this field existed. This wizard always declares the mode.
   */
  static boolean claimsWholeOrigin(ApiInventoryOperationOriginUpdate origin) {
    return origin.getAmountMode() == ApiInventoryOperationAmountMode.ALL;
  }

  /**
   * Whether an origin currently holds nothing: a null quantity (never set), a quantity without a
   * numeric value, or a non-positive amount. No operation may act on such an origin: there is
   * nothing to take, pool, preserve or destroy.
   */
  static boolean originHoldsNothing(Quantifiable originQuantity) {
    return originQuantity == null
        || originQuantity.getNumericValue() == null
        || originQuantity.getNumericValue().signum() <= 0;
  }

  /**
   * Whether the amount taken exceeds the origin's current quantity, unit-aware within a measurement
   * category (e.g. 0.006 kg against a 5 g origin). A null amount, or a pair in different categories
   * (which the UI never produces), is not treated as over-removal. A null/absent origin quantity
   * means the origin holds nothing, so any positive amount taken from it is over-removal.
   */
  static boolean amountTakenExceedsOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    if (amountTaken == null || amountTaken.getNumericValue() == null) {
      return false;
    }
    if (originQuantity == null || originQuantity.getNumericValue() == null) {
      // Origin holds nothing: any positive amount taken is over-removal.
      return amountTaken.getNumericValue().signum() > 0;
    }
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) > 0;
  }

  /**
   * Whether the decrement would not subtract exactly what the caller asked for. {@code
   * registerApiSubSampleUsage} subtracts with {@code QuantityUtils.sum}, whose summing visitor
   * computes in the LARGER of the two units and whose result persists at 3 decimal places, so
   * resolution can be lost in the conversion rather than in the submitted scalar. Two ways that
   * happens, both rejected here:
   *
   * <ul>
   *   <li>the amount itself falls below the stored resolution: 0.001 ul from a 1 l origin is 1e-9
   *       l, rounded away entirely, so the operation would create its output without decrementing
   *       the origin at all (Copilot review, PR #1090);
   *   <li>the amount is exactly storable but the REMAINDER is not: 0.5 l is exactly 500 ml, yet
   *       taking it from a 1500.4 ml origin is computed as 1.0004 l, which rounds to 1 l and
   *       silently removes an extra 0.4 ml (Codex review, PR #1090).
   * </ul>
   *
   * <p>A scale-only test of the converted amount catches the first and misses the second, so the
   * check compares the exact remainder against the value {@code sum} would actually persist, both
   * expressed in the origin's unit. Conversions use exact power-of-ten unit factors, so nothing is
   * lost before the comparison. Over-removal is rejected before this runs, so the remainder is
   * never negative. Missing values, a zero amount and incomparable categories are handled by their
   * own rules.
   */
  static boolean amountTakenLostToRounding(
      ApiQuantityInfo amountTaken, QuantityInfo originQuantity) {
    if (amountTaken == null
        || amountTaken.getNumericValue() == null
        || amountTaken.getNumericValue().signum() == 0
        || originQuantity == null
        || originQuantity.getNumericValue() == null) {
      return false;
    }
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)
        || amountTaken.getUnitId().equals(originQuantity.getUnitId())) {
      // Same unit: both operands are already stored at 3dp, so the subtraction is exact.
      return false;
    }
    BigDecimal takenInOriginUnit =
        amountTaken
            .getNumericValue()
            .multiply(exactUnitFactor(amountTaken.getUnitId(), originQuantity.getUnitId()));
    BigDecimal exactRemainder = originQuantity.getNumericValue().subtract(takenInOriginUnit);
    QuantityInfo stored =
        quantityUtils.sum(
            List.of(
                originQuantity,
                new QuantityInfo(amountTaken.getNumericValue(), amountTaken.getUnitId()).negate()));
    BigDecimal storedInOriginUnit =
        stored
            .getNumericValue()
            .multiply(exactUnitFactor(stored.getUnitId(), originQuantity.getUnitId()));
    return exactRemainder.compareTo(storedInOriginUnit) != 0;
  }

  /**
   * The exact factor converting one unit into another within a measurement category. Factors are
   * powers of ten, so the conversion is applied with {@link BigDecimal} and loses no precision
   * before a scale or equality test. Raw-typed because the unit definitions are wildcard-typed;
   * callers assert comparability first, so the conversion cannot mix categories.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static BigDecimal exactUnitFactor(Integer fromUnitId, Integer toUnitId) {
    if (fromUnitId.equals(toUnitId)) {
      return BigDecimal.ONE;
    }
    javax.measure.Quantity oneFromUnit =
        Quantities.getQuantity(1, RSUnitDef.getUnitById(fromUnitId).getDefinition());
    return BigDecimal.valueOf(
        oneFromUnit.to(RSUnitDef.getUnitById(toUnitId).getDefinition()).getValue().doubleValue());
  }

  /**
   * Whether the amount taken equals the origin's current quantity, unit-aware within a measurement
   * category (0.005 kg empties a 5 g origin, and 0.01 l a 10 ml one). This is the equality half of
   * the whole-origin compare-and-swap: a whole-origin claim that does not match the live quantity
   * is a stale snapshot, rejected as a 409. Missing values or incomparable categories never count
   * as emptying, so an incomparable pair is left to the category check that runs before this one.
   */
  static boolean amountTakenEmptiesOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    if (amountTaken == null
        || amountTaken.getNumericValue() == null
        || originQuantity == null
        || originQuantity.getNumericValue() == null) {
      return false;
    }
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) == 0;
  }
}

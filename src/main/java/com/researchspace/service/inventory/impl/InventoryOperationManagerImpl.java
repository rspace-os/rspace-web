package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.InventoryOperationManager.OperationOutcome;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.OperationTemplateConformanceValidator;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.operations.InventoryOperation;
import com.researchspace.service.inventory.operations.LabelResolver;
import com.researchspace.service.inventory.operations.OriginState;
import com.researchspace.session.SessionTimeZoneUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

@Service("inventoryOperationManager")
public class InventoryOperationManagerImpl implements InventoryOperationManager {

  @Autowired private SampleApiManager sampleApiMgr;
  @Autowired private SubSampleApiManager subSampleApiMgr;
  @Autowired private MessageSource messageSource;
  @Autowired private OperationTemplateConformanceValidator templateConformance;
  @Autowired private LinkTargetResolver linkTargetResolver;

  private static final QuantityUtils quantityUtils = new QuantityUtils();

  @Override
  public <R extends ApiInventoryOperationRequests.Request> OperationOutcome performOperation(
      InventoryOperation<R> operation, R request, List<Long> originIds, User user)
      throws BindException {
    // Permission is asserted while the state is snapshotted, before anything is validated against
    // it, so an under-permissioned caller gets an authorization failure rather than a value error
    // about state they may not see.
    List<OriginState> origins = new ArrayList<>();
    for (Long originId : originIds) {
      SubSample subSample = subSampleApiMgr.assertUserCanEditSubSample(originId, user);
      origins.add(
          new OriginState(
              originId,
              subSample.getGlobalIdentifier(),
              subSample.getName(),
              subSample.getQuantityInfo() == null
                  ? null
                  : new ApiQuantityInfo(subSample.getQuantityInfo()),
              parentFields(subSample.getSample())));
    }

    // Values are checked before any origin is written to, so a bad request touches nothing.
    BeanPropertyBindingResult valueErrors =
        new BeanPropertyBindingResult(request, "apiInventoryOperationPost");
    operation.validate(request, valueErrors);
    rejectUnreadableDocumentationTarget(request.getDocumentedByGlobalId(), user, valueErrors);
    if (valueErrors.hasErrors()) {
      throw new BindException(valueErrors);
    }

    ApiInventoryOperationPost built =
        operation.build(
            request,
            origins,
            LabelResolver.fromMessageSource(messageSource, LocaleContextHolder.getLocale()),
            // The session's recorded browser timezone gives the user's local date; an API-key
            // session has none, so this falls back to the server's.
            LocalDate.parse(new SessionTimeZoneUtils().formatDateForClient(new Date())));

    ApiSampleWithFullSubSamples created = execute(built, user);

    // The origins as they stand afterwards, read HERE rather than by the caller: still inside this
    // transaction, so they are one consistent snapshot of what this operation produced, and one
    // transaction rather than one per origin against a 100-origin cap.
    List<ApiSubSample> originsAfter = new ArrayList<>();
    for (Long originId : originIds) {
      originsAfter.add(subSampleApiMgr.getApiSubSampleById(originId, user));
    }
    return new OperationOutcome(created, originsAfter);
  }

  private static List<OriginState.ParentField> parentFields(SampleEntity parent) {
    List<OriginState.ParentField> fields = new ArrayList<>();
    if (parent == null) {
      return fields;
    }
    for (InventoryEntityField field : parent.getActiveFields()) {
      fields.add(new OriginState.ParentField(field.getName(), field.getData(), null));
    }
    for (ExtraField field : parent.getActiveExtraFields()) {
      fields.add(
          new OriginState.ParentField(
              field.getName(), field.getData(), field.getOperationFieldKey()));
    }
    return fields;
  }

  /**
   * The transactional core, run on a request an operation built. Not on {@link
   * InventoryOperationManager}: it dereferences every origin's id and amount without guards and
   * asserts nothing about permission itself, so it is only safe after {@link #performOperation} has
   * snapshotted and validated. Reached by self-invocation, which is why {@code performOperation}
   * carries the rollback rule.
   */
  ApiSampleWithFullSubSamples execute(ApiInventoryOperationPost request, User user)
      throws BindException {
    // Inside this transaction and before any origin is read, so the template validated is the
    // template the sample is created from.
    templateConformance.validate(request, user);
    // The validator guarantees unique, non-null ids by this point.
    List<ApiInventoryOperationOriginUpdate> originsById =
        request.getOrigins().stream()
            .sorted(Comparator.comparing(ApiInventoryOperationOriginUpdate::getId))
            .toList();

    checkOriginLiveState(request, originsById, user);

    // Reduce each origin BEFORE creating the new sample, so the new subsample is the
    // most-recently-modified record and sorts first in a modification-date listing. Coordinated
    // here, in the same transaction as the sample creation, rather than as a separate step.
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.registerApiSubSampleUsage(
          origin.getId(), origin.getAmountTaken().toQuantityInfo(), user);
      if (CollectionUtils.isNotEmpty(origin.getExtraFields())) {
        ApiSubSample fieldUpdate = new ApiSubSample();
        fieldUpdate.setId(origin.getId());
        fieldUpdate.setExtraFields(origin.getExtraFields());
        // Sparse update: null tags means "leave tags untouched"; the DTO's default empty list
        // would be applied as "clear all tags" and silently wipe a tagged origin's tags.
        fieldUpdate.setTags(null);
        subSampleApiMgr.updateApiSubSample(fieldUpdate, user);
      }
    }

    return request.getNewSample() == null
        ? null
        : sampleApiMgr.createNewApiSample(request.getNewSample(), user);
  }

  /**
   * The live-state rules (DevDocs/adr/0011): every origin must currently hold something, the amount
   * taken may not exceed what an origin holds, and an origin-emptying operation (e.g. Destroy) must
   * take exactly what the origin holds. Permission is asserted BEFORE reading state, so an
   * under-permissioned caller gets an authorization failure, not a misleading "origin empty" 400.
   * Violations surface as the same field-scoped 400 (BindException) the structural validator
   * produces, under {@code origins[i]} in request order.
   */
  private void checkOriginLiveState(
      ApiInventoryOperationPost request,
      List<ApiInventoryOperationOriginUpdate> originsById,
      User user)
      throws BindException {
    boolean emptiesOrigin = request.isEmptiesOrigin();
    BeanPropertyBindingResult errors =
        new BeanPropertyBindingResult(request, "apiInventoryOperationPost");
    // Origins are processed in id order but reported at their REQUEST index. Keyed by identity: a
    // list scan per origin is quadratic at the 100-origin cap, and these are Lombok @Data values,
    // so two equal origins would both resolve to the first one's index.
    Map<ApiInventoryOperationOriginUpdate, Integer> requestIndex = new IdentityHashMap<>();
    for (int i = 0; i < request.getOrigins().size(); i++) {
      requestIndex.put(request.getOrigins().get(i), i);
    }
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.assertUserCanEditSubSample(origin.getId(), user);
    }

    QuantityInfo firstOriginQuantity = null;
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      errors.pushNestedPath(String.format("origins[%d]", requestIndex.get(origin)));
      try {
        QuantityInfo currentQuantity = subSampleApiMgr.getIfExists(origin.getId()).getQuantity();
        if (originHoldsNothing(currentQuantity)) {
          errors.rejectValue(
              "id",
              "errors.inventory.operation.originEmpty",
              "An origin subsample that currently holds nothing cannot be operated on.");
        } else if (firstOriginQuantity != null
            && !quantityUtils.isComparableQuantities(firstOriginQuantity, currentQuantity)) {
          errors.rejectValue(
              "id",
              "errors.inventory.operation.originCategoryMismatch",
              "All origin subsamples must use the same measurement category.");
        } else if (origin.getAmountTaken() != null
            && !quantityUtils.isComparableQuantities(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenCategoryMismatch",
              "The amount taken must use the origin's measurement category.");
        } else if (emptiesOrigin
            && !amountTakenEmptiesOrigin(origin.getAmountTaken(), currentQuantity)) {
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
          // The remainder fits no unit in its category, so registerApiSubSampleUsage would store
          // it rounded and silently lose the decrement. Checked with the same subtraction the
          // decrement itself uses, so the two can never disagree.
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenNotSubtractable",
              "The amount taken is too fine to record against this origin.");
        } else {
          firstOriginQuantity = firstOriginQuantity == null ? currentQuantity : firstOriginQuantity;
        }
      } finally {
        errors.popNestedPath();
      }
    }
    rejectNewSubSamplesOutsideOriginCategory(request, firstOriginQuantity, errors);
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  /**
   * A documentation target the caller cannot read is a field error on {@code documentedByGlobalId},
   * the field they sent it in.
   *
   * <p>Checked here, alongside the operation's own value rules and before any origin is written to,
   * so a request already known to be bad changes nothing. The record KIND is checked earlier, on
   * shape alone; this is the existence and read-permission half, which needs the acting user.
   */
  private void rejectUnreadableDocumentationTarget(
      String documentedByGlobalId, User user, BeanPropertyBindingResult errors) {
    if (documentedByGlobalId == null) {
      return;
    }
    GlobalIdentifier target;
    try {
      target = new GlobalIdentifier(documentedByGlobalId);
    } catch (IllegalArgumentException malformed) {
      return;
    }
    if (!linkTargetResolver.targetExistsAndIsReadable(target, user)) {
      errors.rejectValue(
          "documentedByGlobalId",
          "errors.inventory.field.linkTargetNotFound",
          new Object[] {documentedByGlobalId},
          "The documentation target does not exist or you may not read it.");
    }
  }

  /**
   * Applies only when there is no template: with one, the created amounts follow the template's
   * category instead, which the template-conformance check above enforces, and the origin category
   * is not consulted.
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
   * Whether an origin currently holds nothing. No operation may act on such an origin: there is
   * nothing to take, pool, preserve or destroy.
   */
  static boolean originHoldsNothing(Quantifiable originQuantity) {
    return originQuantity == null
        || originQuantity.getNumericValue() == null
        || originQuantity.getNumericValue().signum() <= 0;
  }

  /**
   * Whether the amount taken exceeds the origin's current quantity, unit-aware within a measurement
   * category (e.g. 0.006 kg against a 5 g origin). A null amount, or a pair in different
   * categories, is not treated as over-removal. A null/absent origin quantity means the origin
   * holds nothing, so any positive amount taken from it is over-removal.
   */
  static boolean amountTakenExceedsOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    if (amountTaken == null || amountTaken.getNumericValue() == null) {
      return false;
    }
    if (originQuantity == null || originQuantity.getNumericValue() == null) {
      // Unreachable from the only production caller: checkOriginLiveState tests
      // originHoldsNothing(currentQuantity) first and takes a different branch, and that covers
      // exactly the null / null-numericValue cases handled here. Kept so this stays a total
      // function
      // of its two arguments rather than one with an undocumented precondition, which is how its
      // direct unit test exercises it.
      return amountTaken.getNumericValue().signum() > 0;
    }
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) > 0;
  }

  /**
   * Whether the decrement would not subtract exactly what the caller asked for.
   *
   * <p>What remains rejected is a genuinely unrepresentable amount, and it reaches here through the
   * arithmetic rather than through the column: summing across a span of unit rungs wide enough to
   * exceed the working precision loses the decrement outright, for example 0.001 ng taken from a 1
   * kg origin, where the result rounds back to the untouched 1 kg. Accepting that would create the
   * operation's output while taking nothing.
   *
   * <p>The check compares the exact remainder against the value {@code subtract} would actually
   * persist, both expressed in the origin's unit, so it tracks whatever that method does rather
   * than restating its rules. Conversions use exact power-of-ten unit factors, so nothing is lost
   * before the comparison. Over-removal is rejected before this runs, so the remainder is never
   * negative. Missing values, a zero amount and incomparable categories are handled by their own
   * rules; two quantities in the SAME unit are both already stored at 3dp, so their difference is
   * exact and needs no check at all.
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
      return false;
    }
    BigDecimal takenInOriginUnit =
        amountTaken
            .getNumericValue()
            .multiply(factorBetween(amountTaken.getUnitId(), originQuantity.getUnitId()));
    BigDecimal exactRemainder = originQuantity.getNumericValue().subtract(takenInOriginUnit);
    QuantityInfo stored =
        quantityUtils.subtract(
            originQuantity,
            new QuantityInfo(amountTaken.getNumericValue(), amountTaken.getUnitId()));
    BigDecimal storedInOriginUnit =
        stored
            .getNumericValue()
            .multiply(factorBetween(stored.getUnitId(), originQuantity.getUnitId()));
    return exactRemainder.compareTo(storedInOriginUnit) != 0;
  }

  private static BigDecimal factorBetween(Integer fromUnitId, Integer toUnitId) {
    return QuantityUtils.exactUnitFactor(
        RSUnitDef.getUnitById(fromUnitId), RSUnitDef.getUnitById(toUnitId));
  }

  /**
   * Whether the amount taken equals the origin's current quantity, unit-aware within a measurement
   * category (0.005 kg empties a 5 g origin, and 0.01 l a 10 ml one). This is what the
   * mustEmptyOrigin rule checks: an emptying operation whose amount does not match the live
   * quantity is a 400. Missing values or incomparable categories never count as emptying, so an
   * incomparable pair is left to the category check that runs before this one.
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

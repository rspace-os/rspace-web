package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiExtraField;
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
import com.researchspace.service.inventory.operations.OperationFieldNames;
import com.researchspace.service.inventory.operations.OriginState;
import com.researchspace.session.SessionTimeZoneUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    LabelResolver labels =
        LabelResolver.fromMessageSource(messageSource, LocaleContextHolder.getLocale());
    BeanPropertyBindingResult valueErrors =
        new BeanPropertyBindingResult(request, "apiInventoryOperationPost");
    operation.validate(request, valueErrors);
    operation.validateOrigins(request, origins, labels, valueErrors);
    rejectUnreadableDocumentationTarget(request.getDocumentedByGlobalId(), user, valueErrors);
    if (valueErrors.hasErrors()) {
      throw new BindException(valueErrors);
    }

    ApiInventoryOperationPost built =
        operation.build(
            request,
            origins,
            labels,
            // The session's recorded browser timezone gives the user's local date; an API-key
            // session has none, so this falls back to the server's.
            LocalDate.parse(new SessionTimeZoneUtils().formatDateForClient(new Date())));

    ApiSampleWithFullSubSamples created = execute(built, user);

    // The origins as they stand afterwards, read here rather than by the caller: still inside
    // this transaction, so they are one consistent snapshot of what this operation produced.
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
    templateConformance.validate(request, user);
    // The validator guarantees unique, non-null ids by this point.
    List<ApiInventoryOperationOriginUpdate> originsById =
        request.getOrigins().stream()
            .sorted(Comparator.comparing(ApiInventoryOperationOriginUpdate::getId))
            .toList();

    checkOriginLiveState(request, originsById, user);

    // Reduce each origin BEFORE creating the new sample, so the new subsample is the
    // most-recently-modified record and sorts first in a modification-date listing.
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.registerApiSubSampleUsage(
          origin.getId(), origin.getAmountTaken().toQuantityInfo(), user);
      if (CollectionUtils.isNotEmpty(origin.getExtraFields())) {
        ApiSubSample fieldUpdate = new ApiSubSample();
        fieldUpdate.setId(origin.getId());
        fieldUpdate.setExtraFields(
            updatingExistingFields(
                origin.getExtraFields(), subSampleApiMgr.getIfExists(origin.getId())));
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
   * ADR 0011 D8: a generated origin field the origin already carries, matched by key and then by
   * name, is updated in place rather than added a second time.
   */
  private static List<ApiExtraField> updatingExistingFields(
      List<ApiExtraField> generated, SubSample live) {
    List<ExtraField> existing = live.getActiveExtraFields();
    for (ApiExtraField field : generated) {
      byKey(field, existing)
          .or(() -> byName(field, existing))
          .ifPresent(
              match -> {
                field.setId(match.getId());
                field.setNewFieldRequest(false);
              });
    }
    return generated;
  }

  private static Optional<ExtraField> byKey(ApiExtraField field, List<ExtraField> existing) {
    return existing.stream()
        .filter(
            e ->
                field.getOperationFieldKey() != null
                    && field.getOperationFieldKey().equals(e.getOperationFieldKey()))
        .findFirst();
  }

  private static Optional<ExtraField> byName(ApiExtraField field, List<ExtraField> existing) {
    return existing.stream()
        .filter(
            e ->
                OperationFieldNames.comparable(e.getName())
                    .equals(OperationFieldNames.comparable(field.getName())))
        .findFirst();
  }

  /**
   * A generated field whose name the origin already uses for a field of another type would be
   * written onto that field; the operation is refused instead.
   */
  private static void rejectOriginFieldNameClash(
      ApiInventoryOperationOriginUpdate origin, SubSample live, BeanPropertyBindingResult errors) {
    if (CollectionUtils.isEmpty(origin.getExtraFields())) {
      return;
    }
    List<ExtraField> existing = live.getActiveExtraFields();
    for (ApiExtraField field : origin.getExtraFields()) {
      if (byKey(field, existing).isPresent()) {
        continue;
      }
      byName(field, existing)
          .filter(match -> match.getType() != field.getTypeAsFieldType())
          .ifPresent(
              match ->
                  errors.rejectValue(
                      "globalId",
                      "errors.inventory.operation.originFieldNameClash",
                      new Object[] {match.getName()},
                      null));
    }
  }

  /**
   * The live-state rules (DevDocs/adr/0011). Permission is asserted BEFORE reading state, so an
   * under-permissioned caller gets an authorization failure, not a misleading "origin empty" 400.
   */
  private void checkOriginLiveState(
      ApiInventoryOperationPost request,
      List<ApiInventoryOperationOriginUpdate> originsById,
      User user)
      throws BindException {
    boolean emptiesOrigin = request.isEmptiesOrigin();
    BeanPropertyBindingResult errors =
        new BeanPropertyBindingResult(request, "apiInventoryOperationPost");
    // Origins are processed in id order but reported at their REQUEST index. Keyed by the id the
    // validator has already guaranteed unique and non-null, so no list scan per origin.
    Map<Long, Integer> requestIndex = new HashMap<>();
    for (int i = 0; i < request.getOrigins().size(); i++) {
      requestIndex.put(request.getOrigins().get(i).getId(), i);
    }
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      subSampleApiMgr.assertUserCanEditSubSample(origin.getId(), user);
    }

    QuantityInfo firstOriginQuantity = null;
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      errors.pushNestedPath(String.format("origins[%d]", requestIndex.get(origin.getId())));
      try {
        SubSample live = subSampleApiMgr.getIfExists(origin.getId());
        QuantityInfo currentQuantity = live.getQuantity();
        if (originHoldsNothing(currentQuantity)) {
          errors.rejectValue("globalId", "errors.inventory.operation.originEmpty");
        } else if (firstOriginQuantity != null
            && !quantityUtils.isComparableQuantities(firstOriginQuantity, currentQuantity)) {
          errors.rejectValue("globalId", "errors.inventory.operation.originCategoryMismatch");
        } else if (origin.getAmountTaken() != null
            && !quantityUtils.isComparableQuantities(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue(
              "amountTaken", "errors.inventory.operation.amountTakenCategoryMismatch");
        } else if (emptiesOrigin
            && !amountTakenEmptiesOrigin(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue("amountTaken", "errors.inventory.operation.mustEmptyOrigin");
        } else if (amountTakenExceedsOrigin(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue("amountTaken", "errors.inventory.operation.amountTakenExceedsOrigin");
        } else if (amountTakenLostToRounding(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue(
              "amountTaken", "errors.inventory.operation.amountTakenNotSubtractable");
        } else {
          firstOriginQuantity = firstOriginQuantity == null ? currentQuantity : firstOriginQuantity;
        }
        rejectOriginFieldNameClash(origin, live, errors);
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
   * The record KIND is checked earlier, on shape alone; this is the existence and read-permission
   * half, which needs the acting user.
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
          null);
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
            "errors.inventory.operation.subSampleCategoryMismatch");
      }
      index++;
    }
  }

  static boolean originHoldsNothing(Quantifiable originQuantity) {
    return originQuantity == null
        || originQuantity.getNumericValue() == null
        || originQuantity.getNumericValue().signum() <= 0;
  }

  /**
   * The amount taken against the origin's current quantity, unit-aware within a measurement
   * category (e.g. 0.006 kg against a 5 g origin), or null when the two cannot be compared.
   */
  private static Integer compareAmountToOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    if (amountTaken == null
        || amountTaken.getNumericValue() == null
        || originQuantity == null
        || originQuantity.getNumericValue() == null
        || !quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return null;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity);
  }

  /** Whether the amount taken exceeds the origin's current quantity. */
  static boolean amountTakenExceedsOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    Integer comparison = compareAmountToOrigin(amountTaken, originQuantity);
    return comparison != null && comparison > 0;
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
   * than restating its rules. Two quantities in the SAME unit are both already stored at 3dp, so
   * their difference is exact and needs no check at all.
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
   * Whether the amount taken equals the origin's current quantity (0.005 kg empties a 5 g origin,
   * and 0.01 l a 10 ml one). An incomparable pair is left to the category check that runs before
   * this one.
   */
  static boolean amountTakenEmptiesOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    Integer comparison = compareAmountToOrigin(amountTaken, originQuantity);
    return comparison != null && comparison == 0;
  }
}

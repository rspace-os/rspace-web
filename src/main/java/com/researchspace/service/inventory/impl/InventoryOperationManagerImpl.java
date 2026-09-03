package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

@Service("inventoryOperationManager")
public class InventoryOperationManagerImpl implements InventoryOperationManager {

  @Autowired private SampleApiManager sampleApiMgr;
  @Autowired private SubSampleApiManager subSampleApiMgr;
  @Autowired private InventoryOperationConfigRegistry operationConfigs;

  @Override
  public ApiSampleWithFullSubSamples performOperation(ApiInventoryOperationPost request, User user)
      throws BindException {
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
   * <p>Each origin is read through {@link SubSampleApiManager#lockSubSampleForEdit}, which holds a
   * row lock until this transaction ends, so a concurrent operation on the same origin waits here
   * and then sees this one's committed quantity instead of decrementing from a stale read (code
   * review, finding 1). Origins are locked in ascending id order, the same order they are
   * decremented in, so overlapping multi-origin operations cannot deadlock.
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
    QuantityUtils quantityUtils = new QuantityUtils();
    QuantityInfo firstOriginQuantity = null;
    for (ApiInventoryOperationOriginUpdate origin : originsById) {
      SubSample dbSubSample = subSampleApiMgr.lockSubSampleForEdit(origin.getId(), user);
      errors.pushNestedPath(String.format("origins[%d]", request.getOrigins().indexOf(origin)));
      try {
        QuantityInfo currentQuantity = dbSubSample.getQuantity();
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
        } else if (amountTakenExceedsOrigin(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenExceedsOrigin",
              "Cannot take more from an origin than it currently holds.");
        } else if (emptiesOrigin
            && !amountTakenEmptiesOrigin(origin.getAmountTaken(), currentQuantity)) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.mustEmptyOrigin",
              "This operation must take the origin's entire remaining quantity.");
        } else {
          firstOriginQuantity = firstOriginQuantity == null ? currentQuantity : firstOriginQuantity;
        }
      } finally {
        errors.popNestedPath();
      }
    }
    rejectNewSubSamplesOutsideOriginCategory(request, firstOriginQuantity, quantityUtils, errors);
    if (errors.hasErrors()) {
      throw new BindException(errors);
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
      QuantityUtils quantityUtils,
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
    QuantityUtils quantityUtils = new QuantityUtils();
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) > 0;
  }

  /**
   * Whether the amount taken equals the origin's current quantity, unit-aware within a measurement
   * category (0.005 kg empties a 5 g origin). An origin-emptying operation (emptiesOrigin, e.g.
   * Destroy) must take exactly what the origin holds, no less (over-removal is rejected
   * separately). Missing values or incomparable categories never count as emptying.
   */
  static boolean amountTakenEmptiesOrigin(
      ApiQuantityInfo amountTaken, Quantifiable originQuantity) {
    if (amountTaken == null
        || amountTaken.getNumericValue() == null
        || originQuantity == null
        || originQuantity.getNumericValue() == null) {
      return false;
    }
    QuantityUtils quantityUtils = new QuantityUtils();
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) == 0;
  }
}

package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
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
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationInputValidator;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.InventoryOperationManager.OperationOutcome;
import com.researchspace.service.inventory.InventoryOperationRequestBuilder;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.OperationTemplateConformanceValidator;
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
  @Autowired private OperationTemplateConformanceValidator templateConformance;
  @Autowired private LinkTargetResolver linkTargetResolver;

  private static final QuantityUtils quantityUtils = new QuantityUtils();

  @Override
  public OperationOutcome performOperation(
      String operationKey,
      List<ApiInventoryOperationOriginUpdate> origins,
      Map<String, Object> inputs,
      Long templateId,
      String documentedByGlobalId,
      User user)
      throws BindException {
    InventoryOperationConfig definition =
        operationConfigs
            .get(operationKey)
            .orElseThrow(() -> new IllegalArgumentException("unknown operation " + operationKey));
    // Inputs are validated before any origin is read, so a bad input is rejected before anything is
    // touched. Defaults fill an absent optional input first, and the filled-in value is then
    // validated like anything the client sent.
    inputs = InventoryOperationInputValidator.withDefaults(definition, inputs);
    MapBindingResult inputErrors = new MapBindingResult(inputs, "apiInventoryOperationPost");
    InventoryOperationInputValidator.validate(definition, inputs, inputErrors);
    rejectUnreadableDocumentationTarget(documentedByGlobalId, user, inputErrors);
    if (inputErrors.hasErrors()) {
      throw new BindException(inputErrors);
    }

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
                .documentationLink(
                    documentedByGlobalId == null
                        ? null
                        : new InventoryOperationRequestBuilder.DocumentationLink(
                            resolveLabel.resolve("operations.documentation.fieldName", Map.of()),
                            documentedByGlobalId))
                // Each origin's amountTaken lives on the origin element itself, not in inputs, so
                // it's passed here rather than read from the inputs map.
                .perSubsampleAmounts(amountsByGlobalId)
                // The session's recorded browser timezone gives the user's local date; an API-key
                // session has none, so this falls back to the server's.
                .clientToday(
                    LocalDate.parse(new SessionTimeZoneUtils().formatDateForClient(new Date())))
                .build());
    // An origin that carries the client's own amount takes it into the core; one without keeps the
    // builder's. amountMode and expectedQuantity are shape-checked by the post validator and read
    // nowhere else, so neither is copied. This index-based copy relies on the builder emitting one
    // update per origin, in the same order the origins were given.
    for (int i = 0; i < origins.size(); i++) {
      if (origins.get(i).getAmountTaken() != null) {
        built.getOrigins().get(i).setAmountTaken(origins.get(i).getAmountTaken());
      }
    }
    // Template conformance runs on the request just built, inside this transaction and before any
    // origin is read, so the template validated is the template the sample is created from.
    ApiSampleWithFullSubSamples created =
        performOperation(built, user, () -> templateConformance.validate(built, user));

    // The origins as they stand afterwards, read HERE rather than by the caller: still inside this
    // transaction, so they are one consistent snapshot of what this operation produced, and one
    // transaction rather than one per origin against a 100-origin cap.
    List<ApiSubSample> originsAfter = new ArrayList<>();
    for (ApiInventoryOperationOriginUpdate origin : origins) {
      originsAfter.add(subSampleApiMgr.getApiSubSampleById(origin.getId(), user));
    }
    return new OperationOutcome(created, originsAfter);
  }

  /**
   * The origin's parent sample's fields a computed value may read: its template-defined fields
   * (which carry no definition key) and its ad-hoc extra fields.
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
    callerValidation.validate();
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

    // A terminal operation (noOutput, e.g. Destroy) sends no new sample: it only acts on its
    // origins, so there is nothing to create and nothing to return.
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
   * <p>Checked here, alongside the declared inputs and before any origin is read, so a request
   * already known to be bad touches no origin. The prefix is checked earlier, by the post
   * validator; this is the existence and read-permission half, which needs the acting user.
   */
  private void rejectUnreadableDocumentationTarget(
      String documentedByGlobalId, User user, MapBindingResult errors) {
    if (documentedByGlobalId == null) {
      return;
    }
    GlobalIdentifier target;
    try {
      target = new GlobalIdentifier(documentedByGlobalId);
    } catch (IllegalArgumentException malformed) {
      // Shape is the post validator's business; it has already rejected this one.
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
   * category instead, which the controller's template check enforces, and the origin category is
   * not consulted.
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
   * <p>This now means one thing: the remainder fits NO unit in its measurement category. It used to
   * mean "the remainder does not fit the ORIGIN's unit", which rejected ordinary lab work - 2.5 mg
   * from a 5 g origin leaves 4.9975 g, four decimal places, refused; and 0.001 ul from a 1 ml
   * origin leaves 999.999 ul, which is exact and was refused anyway. The column stores a number and
   * a UNIT ID, so a remainder that will not fit the origin's own unit usually fits one rung down,
   * and {@code QuantityUtils.subtract} now stores it there.
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
      // Same unit: both operands are already stored at 3dp, so the subtraction is exact.
      return false;
    }
    BigDecimal takenInOriginUnit =
        amountTaken
            .getNumericValue()
            .multiply(exactUnitFactor(amountTaken.getUnitId(), originQuantity.getUnitId()));
    BigDecimal exactRemainder = originQuantity.getNumericValue().subtract(takenInOriginUnit);
    QuantityInfo stored =
        quantityUtils.subtract(
            originQuantity,
            new QuantityInfo(amountTaken.getNumericValue(), amountTaken.getUnitId()));
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

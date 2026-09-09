package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.OperationValidationSupport.MAX_EXTRA_FIELDS;
import static com.researchspace.api.v1.controller.OperationValidationSupport.declaredFieldType;
import static com.researchspace.api.v1.controller.OperationValidationSupport.fieldsWithKey;
import static com.researchspace.api.v1.controller.OperationValidationSupport.isValidAmountTaken;
import static com.researchspace.api.v1.controller.OperationValidationSupport.rejectDeclaredField;
import static com.researchspace.api.v1.controller.OperationValidationSupport.validateDeclaredContent;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.InventoryOperationConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

/**
 * The per-origin half of {@link InventoryOperationPostValidator}: each origin's id, the amount
 * taken from it and how the client decided that amount, plus the extra fields the operation
 * declares on the origin itself. Split out of the validator so a new origin rule lands here rather
 * than appending to one class holding every rule for every payload region (PR #963 review).
 */
class OperationOriginValidator {

  private final ApiExtraFieldsHelper extraFieldsHelper;

  OperationOriginValidator(ApiExtraFieldsHelper extraFieldsHelper) {
    this.extraFieldsHelper = extraFieldsHelper;
  }

  void validateOrigins(
      ApiInventoryOperationPost request, InventoryOperationConfig config, Errors errors) {
    // A subsample may appear at most once: each origin's amount taken is validated against that
    // origin's original quantity, but the manager applies the decrements in order, so the same id
    // listed twice would be checked twice against the full quantity yet decremented twice (two 6 mL
    // entries could drain a 10 mL origin past what the over-removal check permits). See
    // DevDocs/adr/0007.
    Set<Long> seenIds = new HashSet<>();
    int index = 0;
    for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
      errors.pushNestedPath(String.format("origins[%d]", index++));
      if (origin.getId() == null) {
        errors.rejectValue(
            "id",
            "errors.inventory.operation.originIdRequired",
            "Each origin must identify a subsample by id.");
      } else if (!seenIds.add(origin.getId())) {
        errors.rejectValue(
            "id",
            "errors.inventory.operation.duplicateOrigin",
            "An origin subsample may appear at most once in an operation.");
      }
      // An origin-emptying operation (Destroy) means to take the whole origin, so its amount is a
      // compare-and-swap claim the manager checks against the live quantity. A client that declares
      // amountMode "explicit" is saying the opposite, that this is an amount the user chose, which
      // this operation cannot honour: malformed, not conflicted, so a 400 here rather than the
      // manager's 409 (RSDEV-1231). An ABSENT mode stays acceptable, so requests predating the
      // field keep working, and it is NOT read as a whole-origin claim: only a DECLARED "all"
      // earns the compare-and-swap, so an absent mode whose amount does not empty the origin still
      // gets the mustEmptyOrigin 400 rather than a 409 it could never resolve.
      if (config.effect().emptiesOrigin()
          && origin.getAmountMode() == ApiInventoryOperationAmountMode.EXPLICIT) {
        errors.rejectValue(
            "amountMode",
            "errors.inventory.operation.amountModeMustBeAll",
            "This operation empties its origins, so the amount taken cannot be an explicit"
                + " amount.");
      } else if (!config.effect().emptiesOrigin()
          && config.effect().amountTakenFrom() == null
          && origin.getAmountMode() == ApiInventoryOperationAmountMode.ALL) {
        // The mirror of the rule above. An operation that only links to its origins (Passage)
        // requires an amount of exactly zero, so a whole-origin claim cannot be satisfied: the
        // manager compare-and-swaps the zero against the live quantity, 409s, and the client
        // reloads to find nothing changed and resubmits the only payload that validates, forever.
        // Malformed rather than conflicted, so it is a 400 here (parallel review).
        errors.rejectValue(
            "amountMode",
            "errors.inventory.operation.amountModeNotApplicable",
            "This operation does not take from its origins, so the amount taken cannot be a"
                + " whole-origin claim.");
      }
      if (!isValidAmountTaken(origin.getAmountTaken())) {
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.operation.amountTakenInvalid",
            "Each origin must specify a non-negative amount, with a unit, to take from it.");
      } else if (!RSUnitDef.exists(origin.getAmountTaken().getUnitId())) {
        // The manager subtracts unit-aware, so an unknown unit would fail there as a 422 rather
        // than a field-scoped 400 (code review, finding 4).
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.quantity.unitInvalid",
            new Object[] {origin.getAmountTaken().getUnitId()},
            "The amount taken must use a known unit.");
      } else if (!RSUnitDef.getUnitById(origin.getAmountTaken().getUnitId()).isAmount()) {
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.quantity.unitNotAmount",
            new Object[] {origin.getAmountTaken().getUnitId()},
            "The amount taken must use an amount unit (volume, mass or count).");
      } else if (!QuantityInfo.canStoreWithoutRounding(origin.getAmountTaken().getNumericValue())) {
        // Quantities persist at 3dp (QuantityInfo rounds HALF_UP), so a finer amount would pass the
        // live-state checks as given yet decrement the origin by the rounded surrogate (0.0004 ml
        // would take nothing at all). Rejected rather than rounded, like over-removal.
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.operation.amountTakenTooPrecise",
            "The amount taken supports at most 3 decimal places.");
      } else if (!config.effect().emptiesOrigin()) {
        // What the amount taken must be follows the operation's effect (DevDocs/adr/0007): an
        // operation that decrements its origins (amountTakenFrom configured) must take a positive
        // amount from each; one that only links to them (e.g. Passage) must take exactly zero. An
        // origin-emptying operation (Destroy) is compare-and-swapped live in the manager
        // instead, where the amount must still equal the origin's current quantity.
        int amountSignum = origin.getAmountTaken().getNumericValue().signum();
        if (config.effect().amountTakenFrom() != null && amountSignum <= 0) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenPositive",
              "This operation takes from each origin, so the amount taken must be greater than"
                  + " zero.");
        } else if (config.effect().amountTakenFrom() == null && amountSignum != 0) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenZero",
              "This operation does not take from its origins, so the amount taken must be zero.");
        }
      }
      validateOriginExtraFields(origin, config, errors);
      errors.popNestedPath();
    }
  }

  /**
   * An origin's extra fields must be exactly the {@code originFields} the operation declares
   * (Destroy's disposed date), matched by key; an operation declaring none accepts none, which also
   * closes the route by which an origin could be made to link to itself (DevDocs/adr/0007). Fields
   * may only ADD: a delete request or an id-bearing edit of an existing field is a mutation no
   * definition describes, so it is rejected even though the caller holds edit permission. Each
   * allowed field is then validated by the same shared field validator the subsample PUT endpoint
   * uses (name required, per-type content, link payloads).
   */
  private void validateOriginExtraFields(
      ApiInventoryOperationOriginUpdate origin, InventoryOperationConfig config, Errors errors) {
    List<ApiExtraField> fields =
        origin.getExtraFields() == null ? List.of() : origin.getExtraFields();
    List<InventoryOperationConfig.OriginField> declared = config.effect().originFields();

    // Checked before the first traversal and returns, like the newSample ceilings: each entry below
    // costs a shared-field-validator pass plus a declared-spec scan, so an unbounded list is
    // caller-controlled work on a public endpoint (Copilot review, PR #1090).
    if (fields.size() > MAX_EXTRA_FIELDS) {
      errors.rejectValue(
          "extraFields",
          "errors.inventory.operation.originExtraFieldCountMaximum",
          new Object[] {MAX_EXTRA_FIELDS},
          "This operation accepts at most 100 extra fields on each origin subsample.");
      return;
    }

    int fieldIndex = 0;
    for (ApiExtraField field : fields) {
      errors.pushNestedPath(String.format("extraFields[%d]", fieldIndex++));
      try {
        if (field == null
            || !field.isNewFieldRequest()
            || field.isDeleteFieldRequest()
            || field.getId() != null) {
          errors.rejectValue(
              "newFieldRequest",
              "errors.inventory.operation.originFieldNewOnly",
              "Origin extra fields may only add new fields.");
          continue;
        }
        if (declared.stream()
            .noneMatch(spec -> spec.nameKey().equals(field.getOperationFieldKey()))) {
          errors.rejectValue(
              "operationFieldKey",
              field.getOperationFieldKey() == null
                  ? "errors.inventory.operation.fieldKeyMissing"
                  : "errors.inventory.operation.fieldKeyUnknown",
              new Object[] {field.getOperationFieldKey()},
              "This field is not one the operation declares.");
        } else {
          // Verified against this operation's own definition, so the key may be persisted. This is
          // the only writer of that flag, which is what keeps a forged key out of every other
          // endpoint (see ApiExtraField#operationFieldKeyVerified).
          field.setOperationFieldKeyVerified(true);
        }
        ValidationUtils.invokeValidator(extraFieldsHelper, field, errors);
      } finally {
        errors.popNestedPath();
      }
    }

    for (InventoryOperationConfig.OriginField spec : declared) {
      List<ApiExtraField> keyed = fieldsWithKey(fields, spec.nameKey());
      if (keyed.size() != 1) {
        rejectDeclaredField(errors, "extraFields", spec.nameKey());
        continue;
      }
      ApiExtraField field = keyed.get(0);
      String path = String.format("extraFields[%d]", fields.indexOf(field));
      if (field.getTypeAsFieldType() != declaredFieldType(spec.type())) {
        rejectDeclaredField(errors, path, spec.nameKey());
        continue;
      }
      validateDeclaredContent(
          field.getContent(), spec.nameKey(), spec.contentFrom(), config, path, errors);
    }
  }
}

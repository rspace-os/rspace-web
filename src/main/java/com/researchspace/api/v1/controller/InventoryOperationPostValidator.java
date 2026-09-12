package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Structural validation of an {@link ApiInventoryOperationPost} against the operation definition
 * its {@code operationType} names (DevDocs/adr/0007), interpreted generically from the shared
 * {@code operations_config.json}: the origin list (non-empty, no null entries, within the cap, the
 * definition's cardinality, unique ids), each origin's amount taken and amount mode (a real amount
 * unit, storable at 3dp, positive for a decrementing operation and exactly zero for one that only
 * links, e.g. Passage; absent altogether is accepted where the definition takes nothing or the
 * whole origin, and the server then supplies it), and the kind of record a documentation target
 * names.
 *
 * <p>Nothing here looks at a sample: the server builds it from the inputs, which the manager
 * validates against the definition ({@code InventoryOperationInputValidator}). Checks needing an
 * origin's live quantity are enforced by the manager inside the operation's transaction, not here.
 */
@Component
public class InventoryOperationPostValidator implements Validator {

  /**
   * Ceiling on origins per request: each origin costs a read, a lock and an update cycle, so the
   * batch is capped like the samples endpoint caps newSampleSubSamplesCount (both 100).
   */
  static final int MAX_ORIGINS = 100;

  /** The ELN record kinds the documentation picker offers (ElnFolderBrowser.PICKABLE_TYPES). */
  static final Set<GlobalIdPrefix> DOCUMENTATION_TARGET_PREFIXES =
      Set.of(GlobalIdPrefix.SD, GlobalIdPrefix.NB, GlobalIdPrefix.GL);

  private final InventoryOperationConfigRegistry operationConfigs;

  public InventoryOperationPostValidator(InventoryOperationConfigRegistry operationConfigs) {
    this.operationConfigs = operationConfigs;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryOperationPost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryOperationPost request = (ApiInventoryOperationPost) target;

    // The operation key names the definition every other rule comes from, so an unknown key is
    // rejected alone: there is nothing meaningful to validate the rest of the request against.
    Optional<InventoryOperationConfig> configForKey =
        operationConfigs.get(request.getOperationType());
    if (configForKey.isEmpty()) {
      errors.rejectValue(
          "operationType",
          StringUtils.isBlank(request.getOperationType())
              ? "errors.inventory.operation.operationTypeRequired"
              : "errors.inventory.operation.unknownType",
          new Object[] {request.getOperationType()},
          "Unknown operation type.");
      return;
    }
    InventoryOperationConfig config = configForKey.get();

    if (CollectionUtils.isEmpty(request.getOrigins())) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originsRequired",
          "At least one origin subsample must be provided for the operation.");
      return;
    }
    // A null list entry (JSON "[null]") cannot be iterated by the checks below; reject it as a
    // clean 400 rather than letting it surface as a 500.
    if (request.getOrigins().stream().anyMatch(Objects::isNull)) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originIdRequired",
          "Each origin must identify a subsample by id.");
      return;
    }
    // The ceiling is checked before the cardinality, and returns: an oversized list is the more
    // specific reason (a single-origin operation would otherwise report only "exactly one" and hide
    // it), and stopping here keeps the per-origin work bounded by the cap rather than by whatever
    // the caller sent (Copilot review, PR #1090).
    if (request.getOrigins().size() > MAX_ORIGINS) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountMaximum",
          new Object[] {MAX_ORIGINS},
          "This operation accepts at most 100 origin subsamples.");
      return;
    }
    if (config.requiresMultiple() && request.getOrigins().size() < 2) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountMinimum",
          "This operation requires at least two origin subsamples.");
    } else if (!config.requiresMultiple() && request.getOrigins().size() != 1) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountExact",
          "This operation requires exactly one origin subsample.");
    }

    validateOrigins(request, config, errors);

    // The id arrives bare, so a malformed one is rejected here too; readability of the target is
    // checked by the shared link validation when the built sample is created.
    if (request.getDocumentedByGlobalId() != null
        && !targetsDocumentableRecord(request.getDocumentedByGlobalId())) {
      errors.rejectValue(
          "documentedByGlobalId",
          "errors.inventory.operation.documentationLinkTargetInvalid",
          "A documentation link must target an ELN document, notebook or Gallery file.");
    }
  }

  private static void validateOrigins(
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
      if (origin.getAmountMode() == ApiInventoryOperationAmountMode.UNKNOWN) {
        // A wire value the enum does not recognise. It binds to UNKNOWN rather than throwing from
        // the @JsonCreator so that the rejection is a catalog key here, not the raw English of an
        // HttpMessageNotReadableException copied into the 400 body (parallel review). Reported
        // before the two rules below because neither can mean anything for an unknown mode.
        errors.rejectValue(
            "amountMode",
            "errors.inventory.operation.amountModeUnknown",
            "Unrecognised amount mode.");
      } else if (config.effect().emptiesOrigin()
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
      if (origin.getAmountTaken() == null && config.effect().amountTakenFrom() == null) {
        // Deliberately empty. The definition, not the caller, decides what this operation takes:
        // nothing (Passage) or the whole origin (Destroy). A typed facade sends no amount for
        // either, and the manager's request builder supplies it from the definition and the
        // origin's live quantity (M0). The wizard still sends one, which is then checked like any
        // other. Inverting this to avoid the empty body would have to restructure the whole
        // else-chain below, which is not worth it (parallel review).
      } else if (!isValidAmountTaken(origin.getAmountTaken())) {
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
      errors.popNestedPath();
    }
  }

  /**
   * A valid amount-taken is a non-negative numeric value carrying a real unit. The unit is required
   * because the manager converts it to a {@link QuantityInfo} (unit-aware subtraction); a null or
   * non-positive unit would fail there with a 500 rather than a clean 400. The frontend uses a
   * non-positive unit id (UNSET_UNIT = 0) as an "unset" marker, so the unit id must be present and
   * greater than zero. A zero numeric value is still allowed (a no-op decrement, e.g. Passage); a
   * non-positive unit id is not.
   */
  private static boolean isValidAmountTaken(ApiQuantityInfo quantity) {
    return quantity != null
        && quantity.getNumericValue() != null
        && quantity.getNumericValue().compareTo(BigDecimal.ZERO) >= 0
        && quantity.getUnitId() != null
        && quantity.getUnitId() > 0;
  }

  private static boolean targetsDocumentableRecord(String globalId) {
    try {
      return DOCUMENTATION_TARGET_PREFIXES.contains(new GlobalIdentifier(globalId).getPrefix());
    } catch (IllegalArgumentException malformed) {
      return false;
    }
  }
}

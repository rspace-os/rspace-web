package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.validation.Errors;

/**
 * The rules every operation's origin list obeys, whatever the operation: each entry names a
 * subsample once, and carries an amount exactly when the operation takes one.
 *
 * <p>Errors are named as the caller's own fields ({@code origin} for the six single-origin bodies,
 * {@code origins[i]} for Pool), so nothing downstream has to rename them.
 */
public final class OperationOriginRules {

  /** The ELN record kinds the documentation picker offers (ElnFolderBrowser.PICKABLE_TYPES). */
  static final Set<GlobalIdPrefix> DOCUMENTATION_TARGET_PREFIXES =
      Set.of(GlobalIdPrefix.SD, GlobalIdPrefix.NB, GlobalIdPrefix.GL);

  private OperationOriginRules() {}

  /** The caller's field name for the origin at {@code index}. */
  public static String originField(boolean singleOrigin, int index) {
    return singleOrigin ? "origin" : "origins[" + index + "]";
  }

  /**
   * Validates the origin list and the documentation target of any operation's request. Where the
   * operation takes no caller-chosen amount, an amount sent anyway is a contradiction rather than a
   * value to honour, and is refused with the operation's own reason.
   */
  public static <R extends ApiInventoryOperationRequests.Request> void validate(
      InventoryOperation<R> operation, R request, Errors errors) {
    boolean singleOrigin = !operation.requiresMultiple();
    boolean takesAmount = operation.takesAmount(request);
    List<ApiInventoryOperationRequests.Origin> origins = request.originList();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < origins.size(); i++) {
      String field = originField(singleOrigin, i);
      ApiInventoryOperationRequests.Origin origin = origins.get(i);
      if (origin == null) {
        errors.rejectValue(
            field,
            "errors.inventory.operation.originIdRequired",
            "Each origin must identify a subsample by id.");
        continue;
      }
      if (origin.getGlobalId() != null && !seen.add(canonical(origin.getGlobalId()))) {
        // Each origin's amount is checked against that origin's original quantity, but the
        // decrements are applied in order, so the same id listed twice would be checked twice
        // against the full quantity yet decremented twice.
        errors.rejectValue(
            field + ".globalId",
            "errors.inventory.operation.duplicateOrigin",
            "An origin subsample may appear at most once in an operation.");
      }
      validateAmountTaken(origin, field, takesAmount, operation.amountNotApplicableCode(), errors);
    }
    validateDocumentationTarget(request.getDocumentedByGlobalId(), errors);
  }

  /**
   * What the origin's global id resolves to, so the duplicate check sees one subsample rather than
   * its spellings: {@code SS100}, {@code SS0100} and {@code SS100v1} all name db id 100, and all
   * reach the decrement. A malformed id is its own key; {@link #subSampleId} rejects it later.
   */
  private static String canonical(String globalId) {
    if (!GlobalIdentifier.isValid(globalId)) {
      return globalId;
    }
    GlobalIdentifier parsed = new GlobalIdentifier(globalId);
    return parsed.getPrefix() + String.valueOf(parsed.getDbId());
  }

  private static void validateAmountTaken(
      ApiInventoryOperationRequests.Origin origin,
      String field,
      boolean takesAmount,
      String notApplicableCode,
      Errors errors) {
    if (!takesAmount) {
      if (origin.getAmountTaken() != null) {
        errors.rejectValue(
            field + ".amountTaken",
            notApplicableCode,
            "This operation decides what it takes from each origin, so no amount may be sent.");
      }
      return;
    }
    OperationQuantityRules.amountTaken(origin.getAmountTaken(), field + ".amountTaken", errors);
    // No error means the amount is present and well-formed, so the value read below is safe.
    if (errors.getFieldErrorCount(field + ".amountTaken") == 0
        && origin.getAmountTaken().getNumericValue().signum() <= 0) {
      errors.rejectValue(
          field + ".amountTaken",
          "errors.inventory.operation.amountTakenPositive",
          "This operation takes from each origin, so the amount taken must be greater than zero.");
    }
  }

  /** The id arrives bare, so a target of the wrong record kind is rejected on shape alone. */
  private static void validateDocumentationTarget(String documentedByGlobalId, Errors errors) {
    if (documentedByGlobalId == null) {
      return;
    }
    boolean documentable;
    try {
      documentable =
          DOCUMENTATION_TARGET_PREFIXES.contains(
              new GlobalIdentifier(documentedByGlobalId).getPrefix());
    } catch (IllegalArgumentException malformed) {
      documentable = false;
    }
    if (!documentable) {
      errors.rejectValue(
          "documentedByGlobalId",
          "errors.inventory.operation.documentationLinkTargetInvalid",
          "A documentation link must target an ELN document, notebook or Gallery file.");
    }
  }

  /**
   * The subsample id a facade origin's global id names, or null with a field error: the prefix is
   * what makes "SS1234" unambiguous where a bare number could be a sample or a container.
   */
  public static Long subSampleId(String globalId, String field, Errors errors) {
    if (globalId != null && GlobalIdentifier.isValid(globalId)) {
      GlobalIdentifier parsed = new GlobalIdentifier(globalId);
      if (parsed.getPrefix() == GlobalIdPrefix.SS) {
        return parsed.getDbId();
      }
    }
    errors.rejectValue(
        field,
        "errors.inventory.operation.originGlobalIdInvalid",
        new Object[] {globalId},
        "Each origin must be identified by a subsample global id.");
    return null;
  }
}

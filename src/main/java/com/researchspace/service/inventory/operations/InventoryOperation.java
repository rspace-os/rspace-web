package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.Errors;

/**
 * One Inventory operation: what it takes from its origins, what it may be asked to do, and the
 * request the manager's transactional core executes.
 *
 * <p>Operations are code, not configuration (DevDocs/adr/0007, amended 2026-09-16). Each
 * implementation owns its typed request body, its own value rules beyond the annotations that body
 * carries, and the sample it builds.
 */
public interface InventoryOperation<R extends ApiInventoryOperationRequests.Request> {

  /**
   * The URL segment and registry key, e.g. {@code aliquot} for {@code POST /operations/aliquot}.
   */
  String key();

  /** Whether the operation acts on several origins at once (Pool). */
  default boolean requiresMultiple() {
    return false;
  }

  /**
   * Whether each origin carries an amount the caller chooses. False for Passage and Destroy, which
   * decide for themselves, and for a Pool request that sets {@code takeAll}.
   */
  default boolean takesAmount(R request) {
    return true;
  }

  /** Whether the operation consumes each origin's whole remaining quantity. */
  default boolean emptiesOrigin(R request) {
    return false;
  }

  /**
   * Why an amount sent on an origin was refused, when this operation takes none. Overridden where
   * the caller needs telling which of its own choices closed that door.
   */
  default String amountNotApplicableCode() {
    return "errors.inventory.operation.amountTakenNotApplicable";
  }

  /**
   * The value rules the request body's annotations cannot express: anything needing {@code
   * RSUnitDef}, a cross-field comparison, or this operation's own semantics. Field errors are named
   * as the caller's own fields.
   */
  void validate(R request, Errors errors);

  /**
   * The request the transactional core executes: one update per origin in the order given, and the
   * sample to create (null for a terminal operation).
   *
   * @param origins each origin's live state, snapshotted in the order the request listed them
   * @param today the caller's local date, for an operation that records one (Destroy)
   */
  ApiInventoryOperationPost build(
      R request, List<OriginState> origins, LabelResolver labels, LocalDate today);
}

package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.Errors;

public interface InventoryOperation<R extends ApiInventoryOperationRequests.Request> {

  /** The URL segment, e.g. {@code aliquot} for {@code POST /operations/aliquot}. */
  String key();

  default boolean requiresMultiple() {
    return false;
  }

  default boolean takesAmount(R request) {
    return true;
  }

  default boolean emptiesOrigin(R request) {
    return false;
  }

  default String amountNotApplicableCode() {
    return "errors.inventory.operation.amountTakenNotApplicable";
  }

  default void validate(R request, Errors errors) {}

  /**
   * The request the transactional core executes: one update per origin in the order given, and the
   * sample to create (null for a terminal operation).
   *
   * @param today the caller's local date, for an operation that records one (Destroy)
   */
  ApiInventoryOperationPost build(
      R request, List<OriginState> origins, LabelResolver labels, LocalDate today);
}

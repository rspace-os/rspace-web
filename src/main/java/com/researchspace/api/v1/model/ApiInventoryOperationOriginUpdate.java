package com.researchspace.api.v1.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One origin as the transactional core will act on it. {@code amountTaken} is a non-negative
 * decrement, not an absolute value: the core reduces the origin's current quantity by it.
 */
@Data
@NoArgsConstructor
public class ApiInventoryOperationOriginUpdate {

  private Long id;

  private ApiQuantityInfo amountTaken;

  /** Fields the operation adds to the origin itself, e.g. Destroy's disposed date. */
  private List<ApiExtraField> extraFields = new ArrayList<>();
}

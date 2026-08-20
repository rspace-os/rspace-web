package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One origin subsample and how an Inventory operation updates it. {@code amountTaken} is a positive
 * decrement (not an absolute value): the backend reduces the origin's current quantity by this
 * amount, clamped at zero, so an operation can never increase the origin's volume. {@code
 * extraFields} are custom fields to add to the origin itself (each with {@code newFieldRequest}
 * true), e.g. Destroy's "disposed" date; empty for an ordinary decrement-only origin. See adr/0002,
 * adr/0008.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"id", "amountTaken", "extraFields"})
public class ApiInventoryOperationOriginUpdate {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("amountTaken")
  private ApiQuantityInfo amountTaken;

  @JsonProperty("extraFields")
  private List<ApiExtraField> extraFields = new ArrayList<>();
}

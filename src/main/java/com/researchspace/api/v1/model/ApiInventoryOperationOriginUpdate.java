package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One origin subsample and the amount to remove from it when an Inventory operation completes.
 * {@code amountTaken} is a positive decrement (not an absolute value): the backend reduces the
 * origin's current quantity by this amount, clamped at zero, so an operation can never increase the
 * origin's volume. See adr/0002.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"id", "globalId", "amountTaken"})
public class ApiInventoryOperationOriginUpdate {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("globalId")
  private String globalId;

  @JsonProperty("amountTaken")
  private ApiQuantityInfo amountTaken;
}

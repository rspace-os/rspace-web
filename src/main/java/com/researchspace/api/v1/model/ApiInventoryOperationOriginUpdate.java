package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code amountTaken} is a non-negative decrement, not an absolute value: the backend reduces the
 * origin's current quantity by this amount, clamped at zero.
 *
 * <p>{@code amountMode} is checked by the post validator for shape only (see {@link
 * ApiInventoryOperationAmountMode}); it is not compared against the live quantity.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"id", "amountMode", "amountTaken"})
public class ApiInventoryOperationOriginUpdate {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("amountMode")
  private ApiInventoryOperationAmountMode amountMode;

  @JsonProperty("amountTaken")
  private ApiQuantityInfo amountTaken;

  /** Shape-checked by the post validator only; never compared against the live quantity. */
  @JsonIgnore private ApiQuantityInfo expectedQuantity;

  /** Fields the operation definition adds to the origin itself, e.g. Destroy's disposed date. */
  @JsonIgnore private List<ApiExtraField> extraFields = new ArrayList<>();
}

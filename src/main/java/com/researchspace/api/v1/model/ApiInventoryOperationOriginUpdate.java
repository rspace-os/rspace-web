package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One origin subsample and how an Inventory operation updates it. {@code amountTaken} is a
 * non-negative decrement (not an absolute value): the backend reduces the origin's current quantity
 * by this amount, clamped at zero, so an operation can never increase the origin's volume.
 *
 * <p>{@code amountMode} says how the client decided {@code amountTaken}, which turns that amount
 * into a compare-and-swap guard when the answer is "all of it". A client that means to empty the
 * origin still serializes the full quantity it saw, and the endpoint checks that against the live
 * locked quantity: a mismatch means the origin changed between wizard load and Perform, and the
 * request is rejected with 409 rather than emptying an origin the user never saw. Absent on the
 * wire it binds to null, a third state distinct from both modes: it earns no compare-and-swap, so
 * every request accepted before this field existed keeps its current meaning. See DevDocs/adr/0007.
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

  /**
   * Server-side only: the fields the operation definition adds to the origin itself (Destroy's
   * disposed date), built by the request builder with {@code newFieldRequest} set and applied by
   * the manager through the ordinary subsample edit. Not on the wire.
   */
  @JsonIgnore private List<ApiExtraField> extraFields = new ArrayList<>();
}

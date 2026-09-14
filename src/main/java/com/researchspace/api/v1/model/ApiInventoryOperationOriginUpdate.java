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
 * <p>{@code amountMode} says how the client decided {@code amountTaken}: "all of it" or an amount
 * the user typed. The post validator checks it for shape only (see {@link
 * ApiInventoryOperationAmountMode}); it is not compared against the live quantity. Absent on the
 * wire it binds to null, so every request accepted before this field existed keeps its meaning. See
 * DevDocs/adr/0007.
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
   * Server-side only, set by the typed facades (M0 D5): the quantity the caller believes the origin
   * holds. Shape-checked by the post validator and otherwise accepted without comparison
   * (DevDocs/adr/0007: no concurrency control). Not on this generic endpoint's wire.
   */
  @JsonIgnore private ApiQuantityInfo expectedQuantity;

  /**
   * Server-side only: the fields the operation definition adds to the origin itself (Destroy's
   * disposed date), built by the request builder with {@code newFieldRequest} set and applied by
   * the manager through the ordinary subsample edit. Not on the wire.
   */
  @JsonIgnore private List<ApiExtraField> extraFields = new ArrayList<>();
}

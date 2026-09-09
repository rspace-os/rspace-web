package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
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
 * true), e.g. Destroy's "disposed" date; empty for an ordinary decrement-only origin.
 *
 * <p>{@code amountMode} says how the client decided {@code amountTaken}, which turns that amount
 * into a compare-and-swap guard when the answer is "all of it". A client that means to empty the
 * origin still serializes the full quantity it saw, and the endpoint checks that against the live
 * locked quantity: a mismatch means the origin changed between wizard load and Perform, and the
 * request is rejected with 409 rather than emptying an origin the user never saw. Absent on the
 * wire it defaults to EXPLICIT, so every request accepted before this field existed keeps its
 * current meaning. See DevDocs/adr/0007.
 */
@Data
@NoArgsConstructor
// Strict binding, for the reason given on ApiInventoryOperationPost: an unrecognised property on an
// origin is a caller believing this request does something to that origin which it does not.
@JsonPropertyOrder({"id", "amountMode", "amountTaken", "extraFields"})
public class ApiInventoryOperationOriginUpdate {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("amountMode")
  private ApiInventoryOperationAmountMode amountMode;

  @JsonProperty("amountTaken")
  private ApiQuantityInfo amountTaken;

  @JsonProperty("extraFields")
  private List<ApiExtraField> extraFields = new ArrayList<>();

  /** Rejects any property an origin update does not declare. See ApiInventoryOperationPost. */
  @JsonAnySetter
  void rejectUnknownProperty(String name, Object ignoredValue) {
    throw new UnknownOperationPropertyException(name);
  }
}

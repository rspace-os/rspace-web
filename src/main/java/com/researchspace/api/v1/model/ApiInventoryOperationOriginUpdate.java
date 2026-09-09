package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
@JsonPropertyOrder({"id", "amountMode", "amountTaken", "extraFields"})
public class ApiInventoryOperationOriginUpdate implements UnknownPropertyCapturing {
  /**
   * Names of request properties this object does not declare; see {@link UnknownPropertyCapturing}.
   * Inert here: never serialized, excluded from equals and toString, and inspected only by the
   * operations endpoint's validator.
   */
  @JsonIgnore @EqualsAndHashCode.Exclude @ToString.Exclude
  private final List<String> unknownProperties = new ArrayList<>();

  /** Records an unrecognised property's NAME and discards its value. */
  @JsonAnySetter
  private void captureUnknownProperty(String name, Object ignoredValue) {
    if (unknownProperties.size() < UnknownPropertyCapturing.MAX_CAPTURED_UNKNOWN_PROPERTIES) {
      unknownProperties.add(name);
    }
  }

  @JsonProperty("id")
  private Long id;

  @JsonProperty("amountMode")
  private ApiInventoryOperationAmountMode amountMode;

  @JsonProperty("amountTaken")
  private ApiQuantityInfo amountTaken;

  @JsonProperty("extraFields")
  private List<ApiExtraField> extraFields = new ArrayList<>();
}

package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request to perform a configured Inventory operation.
 *
 * <p>The frontend assembles this from the fetched operation definitions (GET /operations/config)
 * plus the user's wizard input: a fully-built new sample (its subsamples, custom fields and
 * relation links) plus the amount taken from each origin subsample. The backend applies the whole
 * thing in a single transaction by coordinating existing managers, with no per-operation branching,
 * reducing each origin by its amount-taken (never increasing it). {@code operationType} names the
 * operation definition the request must conform to: the endpoint's validator
 * (InventoryOperationPostValidator, in the controller layer) resolves it against the config and
 * enforces the definition, still generically. See DevDocs/adr/0007.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"operationType", "origins", "inputs", "newSample"})
public class ApiInventoryOperationPost implements UnknownPropertyCapturing {
  /**
   * Names of request properties this object does not declare; see {@link UnknownPropertyCapturing}.
   * Inert here: never serialized, excluded from equals and toString, and inspected only by the
   * operations endpoint's validator.
   */
  @JsonIgnore @EqualsAndHashCode.Exclude @ToString.Exclude
  private final List<String> unknownProperties = new ArrayList<>();

  /**
   * Records an unrecognised property's NAME and discards its value.
   *
   * <p>The value parameter is {@code Void}, not {@code Object}, deliberately. An any-setter's value
   * IS deserialized before the method body runs, so {@code Object} would build a
   * LinkedHashMap/ArrayList graph for the whole unknown subtree only to drop it, turning a body of
   * junk keys into heap amplification. {@code Void} routes Jackson to NullifyingDeserializer, which
   * skips the subtree exactly as unknown-property handling did before (parallel review).
   */
  @JsonAnySetter
  private void captureUnknownProperty(String name, Void ignoredValue) {
    UnknownPropertyCapturing.capture(unknownProperties, name);
  }

  @JsonProperty("operationType")
  private String operationType;

  // @Valid on both members: the endpoint binds this DTO as @Valid, and without an explicit cascade
  // none of the Bean Validation constraints ordinary sample creation enforces (image size, note
  // length) would apply to an operation's payload.
  // Capped here as well as in InventoryOperationPostValidator (MAX_ORIGINS): the validator's check
  // runs only after Jackson has materialised every element and the @Valid cascade above has walked
  // all of them, so the ceiling belongs at binding too.
  @Valid
  @Size(max = 100, message = "{errors.inventory.operation.tooManyOrigins}")
  @JsonProperty("origins")
  private List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();

  @Valid
  @JsonProperty("newSample")
  private ApiSampleWithFullSubSamples newSample;

  /**
   * The server-built shape (plan-operations-server-builds.md, M3): the values the user typed, by
   * the definition's input key, from which the server builds the sample itself. Present selects
   * this shape and {@code newSample} must be absent; null selects the client-assembled shape above.
   * Bound as raw JSON values (a quantity arrives as a Map), which the endpoint types against the
   * definition before validation.
   */
  @JsonProperty("inputs")
  private Map<String, Object> inputs;
}

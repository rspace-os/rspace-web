package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

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
// Strict binding, deliberately narrower than the rest of api/v1. The API's converter is built by
// Jackson2ObjectMapperBuilder, which turns FAIL_ON_UNKNOWN_PROPERTIES OFF, so every other endpoint
// silently drops a property it does not recognise. That is the wrong default here because this
// endpoint's whole contract is "the request must conform to the operation definition": a mistyped
// or
// invented property is a request the caller believes does something it does not, and silently
// ignoring it means an operation performs differently from what was asked with no indication why.
// The validator already whitelists the created sample the same way (undeclaredProperty).
//
// Enforced with a rejecting @JsonAnySetter rather than @JsonIgnoreProperties(ignoreUnknown =
// false):
// false is that attribute's own default value, so Jackson cannot tell "explicitly strict" from
// "unspecified" and the mapper-level "off" still wins. A catch-all setter is independent of mapper
// configuration, which is what this needs to be.
// Scoped to the DTOs only this endpoint binds: ApiSampleWithFullSubSamples and ApiExtraField are
// shared with POST /samples and the subsample endpoints, so making those strict would change
// unrelated endpoints' behaviour. See DevDocs/adr/0007.
@JsonPropertyOrder({"operationType", "origins", "newSample"})
public class ApiInventoryOperationPost {

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
   * Rejects any property this request does not declare. Jackson routes an unrecognised property
   * here and wraps the throw as a message-conversion failure, which the API advice reports as the
   * endpoint's ordinary 400.
   */
  @JsonAnySetter
  void rejectUnknownProperty(String name, Object ignoredValue) {
    throw new UnknownOperationPropertyException(name);
  }
}

package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to perform a configured Inventory operation.
 *
 * <p>The frontend assembles this from {@code operations_config.json} plus the user's wizard input:
 * a fully-built new sample (its subsamples, custom fields and relation links) plus the amount taken
 * from each origin subsample. The backend applies the whole thing in a single transaction by
 * coordinating existing managers, with no per-operation branching, reducing each origin by its
 * amount-taken (never increasing it). {@code operationType} names the operation definition the
 * request must conform to: {@link
 * com.researchspace.api.v1.controller.InventoryOperationPostValidator} resolves it against the
 * backend's copy of the config and enforces the definition, still generically. See
 * DevDocs/adr/0006, DevDocs/adr/0007, DevDocs/adr/0015.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"operationType", "origins", "newSample"})
public class ApiInventoryOperationPost {

  @JsonProperty("operationType")
  private String operationType;

  @JsonProperty("origins")
  private List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();

  @JsonProperty("newSample")
  private ApiSampleWithFullSubSamples newSample;
}

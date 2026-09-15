package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The response of every typed operation endpoint (DevDocs/adr/0007, D2): an operation is a
 * transfer, so the caller gets both what was produced and what each origin holds afterwards,
 * without a GET per origin. {@code sample} is null for an operation that creates nothing (Destroy).
 * {@code origins} are in request order.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"sample", "origins"})
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiInventoryOperationResult {

  @JsonProperty("sample")
  private ApiSampleWithFullSubSamples sample;

  @JsonProperty("origins")
  private List<ApiSubSample> origins;
}

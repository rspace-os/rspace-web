package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Manufacturer model of the instrument (PIDINST {@code Model}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instModel {

  /** Name of the model, for example {@code "AWS-42"}. */
  @JsonProperty("modelName")
  private String modelName;

  /** Optional persistent identifier for the model. */
  @JsonProperty("modelIdentifier")
  private B2instModelIdentifier modelIdentifier;
}

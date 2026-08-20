package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Organisation that manufactured the instrument (PIDINST {@code Manufacturer}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instManufacturer {

  /** Name of the manufacturer. */
  @JsonProperty("manufacturerName")
  private String manufacturerName;

  /** Optional persistent identifier for the manufacturer. */
  @JsonProperty("manufacturerIdentifier")
  private B2instManufacturerIdentifier manufacturerIdentifier;
}

package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent identifier for a {@link B2instManufacturer}
 * (PIDINST {@code manufacturerIdentifier}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instManufacturerIdentifier {

  /** Type of manufacturer identifier, for example {@code "URL"} or {@code "ROR"}. */
  @JsonProperty("manufacturerIdentifierType")
  private String manufacturerIdentifierType;

  /** The manufacturer identifier value. */
  @JsonProperty("manufacturerIdentifierValue")
  private String manufacturerIdentifierValue;
}

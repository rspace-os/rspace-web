package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent identifier for a {@link B2instModel} (PIDINST {@code modelIdentifier}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instModelIdentifier {

  /** Type of model identifier, for example {@code "SerialNumber"}. */
  @JsonProperty("modelIdentifierType")
  private String modelIdentifierType;

  /** The model identifier value, for example {@code "SN-0042"}. */
  @JsonProperty("modelIdentifierValue")
  private String modelIdentifierValue;
}

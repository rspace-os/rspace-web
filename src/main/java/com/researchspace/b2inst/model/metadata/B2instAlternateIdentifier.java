package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local or alternate identifier of the instrument
 * (PIDINST {@code AlternateIdentifier}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instAlternateIdentifier {

  /** Type of alternate identifier, for example {@code "InventoryNumber"}. */
  @JsonProperty("alternateIdentifierType")
  private String alternateIdentifierType;

  /** The alternate identifier value, for example {@code "INV-2025-0042"}. */
  @JsonProperty("alternateIdentifierValue")
  private String alternateIdentifierValue;
}

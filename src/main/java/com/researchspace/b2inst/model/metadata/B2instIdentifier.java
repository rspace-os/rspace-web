package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent identifier of the instrument itself (PIDINST {@code Identifier}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instIdentifier {

  /** Type of identifier, for example {@code "Handle"} or {@code "DOI"}. */
  @JsonProperty("identifierType")
  private String identifierType;

  /** The identifier value, for example {@code "21.T11975/aws-42"}. */
  @JsonProperty("identifierValue")
  private String identifierValue;
}

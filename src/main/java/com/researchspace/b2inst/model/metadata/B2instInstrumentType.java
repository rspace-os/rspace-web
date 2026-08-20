package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Controlled-vocabulary instrument type (PIDINST {@code InstrumentType}).
 *
 * <p>Only {@code instrumentTypeName} is used by the current payloads; any
 * optional type-identifier fields returned by the server are tolerated through
 * {@link JsonIgnoreProperties}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instInstrumentType {

  /** Name of the instrument type, for example {@code "Weather station"}. */
  @JsonProperty("instrumentTypeName")
  private String instrumentTypeName;
}

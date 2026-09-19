package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A commissioning/decommissioning date of the instrument (PIDINST {@code Date}).
 *
 * <p>Wire format note: the EUDAT docs do not specify the entry's inner keys; the {@code
 * Date}/{@code dateType} names below were verified against production b2inst.gwdg.de records (July
 * 2026), where the inner date key is {@code Date} (PascalCase, same as the outer list property).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instDate {

  /**
   * The date value, for example {@code "2024-02-21"}. Plain ISO dates are verified accepted by
   * B2INST (test instance, July 2026); production records also carry full ISO datetimes.
   */
  @JsonProperty("Date")
  private String date;

  /** PIDINST controlled type: {@code "Commissioned"} or {@code "DeCommissioned"}. */
  @JsonProperty("dateType")
  private String dateType;
}

package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Measurement Technique or Calibration related identifier of an imported PID record that the
 * import could not turn into a link (RSDEV-1528; CONTEXT.md "Skipped entry"; ADR 0009 decision 9).
 * Returned on the created instrument by {@code POST /instruments/importPidinst} only; nothing about
 * it is stored.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiPidinstSkippedRelatedIdentifier {

  /** Why the entry was not imported. */
  public enum Reason {
    /**
     * The address is not this deployment's own item page: another RSpace's, one that is not an
     * RSpace at all, or another page of this deployment.
     */
    OTHER_SERVER,
    /**
     * The address is this deployment's, but the item cannot be linked by the importing user:
     * unreadable, missing, or of a kind links cannot target, and which of those is never said.
     */
    NOT_AVAILABLE
  }

  /** The template field the entry was for, by its canonical name, e.g. {@code Calibration}. */
  @JsonProperty("field")
  private String field;

  @JsonProperty("reason")
  private Reason reason;

  /** The entry's address as the registry holds it. */
  @JsonProperty("address")
  private String address;

  /**
   * The address's host, for {@link Reason#OTHER_SERVER} only; absent when the address has none or
   * it is this deployment's own host, which is no other server.
   */
  @JsonProperty("host")
  private String host;
}

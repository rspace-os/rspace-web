package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of {@code POST /api/inventory/v1/instruments/importPidinst} (RSDEV-1326). */
@Data
@NoArgsConstructor
public class ApiPidinstImportPost {

  /** The PID to import: a DOI or Handle, bare or as a doi.org / hdl.handle.net address. */
  @NotBlank(message = "{errors.inventory.identifier.pidinstImportPidRequired}")
  @JsonProperty("pid")
  private String pid;

  /**
   * The registry the PID belongs to: {@code PIDINST_DATACITE} or {@code PIDINST_B2INST} (ADR 0011).
   */
  @NotBlank(message = "{errors.inventory.identifier.pidinstImportProviderRequired}")
  @JsonProperty("provider")
  private String provider;

  /** Where to store the new instrument; the user's workbench when absent. */
  @JsonProperty("newTargetLocation")
  private ApiTargetLocation newTargetLocation;
}

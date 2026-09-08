package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One instrument record as a PID registry describes it, normalised across providers for the PID
 * lookup (RSDEV-1326; CONTEXT.md "PID lookup"). Multi-valued PIDINST properties stay lists here;
 * the import joins them when it fills the single-valued template fields.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiPidinstRecord {

  /** The PID itself: a Handle ({@code 21.…}) from B2INST, a DOI ({@code 10.…}) from DataCite. */
  @JsonProperty("pid")
  private String pid;

  /** {@code PIDINST_B2INST} or {@code PIDINST_DATACITE}. */
  @JsonProperty("provider")
  private String provider;

  /** The record's page on the provider; may need a provider sign-in. */
  @JsonProperty("providerRecordUrl")
  private String providerRecordUrl;

  /** The resolvable citable address: hdl.handle.net or doi.org. */
  @JsonProperty("publicUrl")
  private String publicUrl;

  /**
   * The provider's lifecycle state: {@code accepted} for a published B2INST record, else
   * DataCite's.
   */
  @JsonProperty("state")
  private String state;

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("owners")
  private List<String> owners = new ArrayList<>();

  @JsonProperty("manufacturers")
  private List<String> manufacturers = new ArrayList<>();

  @JsonProperty("model")
  private String model;

  @JsonProperty("instrumentTypes")
  private List<String> instrumentTypes = new ArrayList<>();

  @JsonProperty("measuredVariables")
  private List<String> measuredVariables = new ArrayList<>();

  /** {@code yyyy-MM-dd} or null. */
  @JsonProperty("commissioned")
  private String commissioned;

  @JsonProperty("decommissioned")
  private String decommissioned;

  @JsonProperty("landingPage")
  private String landingPage;

  @JsonProperty("alternateIdentifier")
  private String alternateIdentifier;

  @JsonProperty("created")
  private String created;

  @JsonProperty("updated")
  private String updated;

  /**
   * The globalId of the instrument in this deployment that already links this PID, or null. Set by
   * the lookup manager, never by the mapper, so the UI can disable Import before the 409.
   */
  @JsonProperty("linkedInstrumentGlobalId")
  private String linkedInstrumentGlobalId;
}

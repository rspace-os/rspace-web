package com.researchspace.model.dmps.argos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The subset of an Argos plan that RSpace saves as an imported DMP file. Deposits read the DOI back
 * from saved files, so the JSON layout ({@code id}, {@code label}, {@code grant}, {@code dois},
 * {@code doi}) must stay the same. Copied from {@code argos-java-client} 1.0.1.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArgosDMP {

  public String id;
  public String label;
  public ApiDMPGrant grant;
  public List<ApiDMPDoi> dois;

  /** The plan's grant. */
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ApiDMPGrant {
    public String id;
    public String label;
  }

  /** A DOI the plan was deposited under. */
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ApiDMPDoi {
    public String doi;
    public String repositoryId;
  }

  /**
   * @return the first DOI, or empty if the plan has none
   */
  public Optional<String> getDoi() {
    if (dois.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(dois.get(0).doi);
  }
}

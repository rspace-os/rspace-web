package com.researchspace.model.dtos;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RaidUpdateResult implements Serializable {

  private boolean succeed;
  private String repoName;
  private String raidIdentifier;
  private String raidUrl;
  private String doi;

  /** RAiD refused the DOI because it could not resolve it, e.g. the DOI is not public yet. */
  private boolean doiNotFound;

  public RaidUpdateResult(
      boolean succeed, String repoName, String raidIdentifier, String raidUrl, String doi) {
    this(succeed, repoName, raidIdentifier, raidUrl, doi, false);
  }
}

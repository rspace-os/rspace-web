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
}

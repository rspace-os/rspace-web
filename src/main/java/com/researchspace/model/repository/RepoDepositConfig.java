package com.researchspace.model.repository;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POJO for RepositorySubmission */
@Data
@NoArgsConstructor
public class RepoDepositConfig {
  /** Required if depositToRepository is true */
  @Valid private RepoDepositMeta meta;

  /** Optional - not all apps require this. */
  private Long repoCfg;

  /** Required if depositToRepository is true */
  private String appName;

  private boolean depositToRepository = false;

  /** A list of 0 or more selected DMPUser internal IDs */
  private List<Long> selectedDMPs = new ArrayList<>();

  /**
   * @return {@code}true{@code} if 1 or more DMP ids were selected
   */
  @JsonIgnore
  public boolean hasDMPs() {
    return selectedDMPs.size() > 0;
  }
}

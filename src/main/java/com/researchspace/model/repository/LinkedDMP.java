package com.researchspace.model.repository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkedDMP {

  private Long dmpUserInternalId;
  private String dmpTitle;
  private String dmpId;

  /**
   * Display name of the plan's platform. A plan with an unknown source is shown as DMPTool, or with
   * an empty name when DMPTool is off.
   */
  private String platformName;

  /**
   * {@code NOT_CONNECTED} when the plan's platform supports write-back but the user is not
   * connected to it, otherwise {@code OK}.
   */
  private String writeBackStatus;
}

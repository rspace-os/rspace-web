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

  /** Display name of the plan's platform; empty when the platform is unknown. */
  private String platformName;

  /**
   * {@code OK}, or the name of the {@code PlanReportStatus} saying why the plan will not be updated
   * after a deposit: {@code NOT_CONNECTED}, {@code PLATFORM_OFF}, {@code NOT_SUPPORTED} or {@code
   * UNKNOWN_SOURCE}.
   */
  private String writeBackStatus;
}

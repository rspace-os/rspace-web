package com.researchspace.service.dmp;

import com.researchspace.model.dmps.DMPSource;
import java.util.List;

/**
 * The write-back outcome for one ticked plan.
 *
 * @param platform the plan's effective platform ({@code UNKNOWN} is reported as {@code DMP_TOOL})
 * @param values empty unless {@code status} is {@link PlanReportStatus#REPORTED}
 */
public record PlanReportResult(
    String planTitle,
    String planId,
    DMPSource platform,
    PlanReportStatus status,
    List<ValueOutcome> values) {

  public static PlanReportResult withStatus(
      String title, String id, DMPSource platform, PlanReportStatus status) {
    return new PlanReportResult(title, id, platform, status, List.of());
  }
}

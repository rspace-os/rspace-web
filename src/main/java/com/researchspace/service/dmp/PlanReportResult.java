package com.researchspace.service.dmp;

import com.researchspace.model.dmps.DMPSource;
import java.util.List;

/**
 * The write-back outcome for one ticked plan.
 *
 * @param platform the plan's platform, {@code UNKNOWN} when it could not be worked out
 * @param values empty unless {@code status} is {@link PlanReportStatus#REPORTED}
 */
public record PlanReportResult(
    String planTitle, DMPSource platform, PlanReportStatus status, List<ValueOutcome> values) {

  public static PlanReportResult withStatus(
      String title, DMPSource platform, PlanReportStatus status) {
    return new PlanReportResult(title, platform, status, List.of());
  }
}

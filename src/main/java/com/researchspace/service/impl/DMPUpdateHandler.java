package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.service.DMPManager;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.DmpPlatformStatusResolver;
import com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus;
import com.researchspace.service.dmp.DmpReporter;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ValueOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Writes a deposit's link onto each ticked plan, routed by the plan's own platform. */
@Slf4j
public class DMPUpdateHandler {

  private final DMPManager dmpManager;
  private final DmpPlatformStatusResolver statusResolver;
  private final List<DmpReporter> reporters;

  public DMPUpdateHandler(
      DMPManager dmpManager,
      DmpPlatformStatusResolver statusResolver,
      List<DmpReporter> reporters) {
    this.dmpManager = dmpManager;
    this.statusResolver = statusResolver;
    this.reporters = reporters;
  }

  /**
   * Reports a deposit to each ticked plan that belongs to {@code subject}. A failure on one plan
   * never stops another, and nothing is thrown for a plan.
   *
   * @param deposit the deposit's DOI link or repository link
   * @param subject the user who made the deposit
   * @param dmpUserIds ids of the ticked plans; ids that are not the user's plans are ignored
   * @return one result per reported plan, in the order of the user's plans
   */
  public List<PlanReportResult> report(DepositReport deposit, User subject, List<Long> dmpUserIds) {
    if (dmpUserIds == null || dmpUserIds.isEmpty()) {
      return List.of();
    }
    List<PlanReportResult> results = new ArrayList<>();
    for (DMPUser plan : dmpManager.findDMPsForUser(subject)) {
      if (dmpUserIds.contains(plan.getId())) {
        results.add(reportPlan(plan, deposit, subject));
      }
    }
    return results;
  }

  /**
   * @param source a plan's source; {@code UNKNOWN} is treated as DMPtool
   * @return whether RSpace can write a deposit's link onto plans from that platform
   */
  public boolean supportsWriteBack(DMPSource source) {
    DMPSource platform = DmpPlatformStatusResolver.effectiveSource(source);
    return reporters.stream().anyMatch(r -> r.supports(platform));
  }

  private PlanReportResult reportPlan(DMPUser plan, DepositReport deposit, User subject) {
    DMPSource platform = DmpPlatformStatusResolver.effectiveSource(plan.getSource());
    try {
      Optional<DmpReporter> reporter =
          reporters.stream().filter(r -> r.supports(platform)).findFirst();
      if (reporter.isEmpty()) {
        return withStatus(plan, platform, PlanReportStatus.NOT_SUPPORTED);
      }
      PlatformStatus platformStatus = statusResolver.resolve(platform, subject);
      if (platformStatus != PlatformStatus.OK) {
        return withStatus(plan, platform, PlanReportStatus.valueOf(platformStatus.name()));
      }
      if (deposit.identifier() == null && deposit.accessUrl() == null) {
        return withStatus(plan, platform, PlanReportStatus.NOTHING_TO_SEND);
      }
      return reporter.get().report(plan, deposit, subject);
    } catch (RuntimeException e) {
      log.warn("Write-back to plan {} on {} failed", plan.getDmpId(), platform, e);
      String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      return new PlanReportResult(
          plan.getTitle(),
          plan.getDmpId(),
          platform,
          PlanReportStatus.REPORTED,
          List.of(ValueOutcome.failed(deposit, reason)));
    }
  }

  private static PlanReportResult withStatus(
      DMPUser plan, DMPSource platform, PlanReportStatus status) {
    return PlanReportResult.withStatus(plan.getTitle(), plan.getDmpId(), platform, status);
  }
}

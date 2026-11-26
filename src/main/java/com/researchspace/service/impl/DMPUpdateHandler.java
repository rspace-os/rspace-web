package com.researchspace.service.impl;

import static com.researchspace.service.dmp.DmpLogging.planIdForLog;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.repository.LinkedDMP;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.DmpPlatformStatusResolver;
import com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus;
import com.researchspace.service.dmp.DmpReporter;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ValueOutcome;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Writes a deposit's link onto each ticked plan, routed by the plan's own platform. */
@Slf4j
public class DMPUpdateHandler {

  private final DMPManager dmpManager;
  private final DmpPlatformStatusResolver statusResolver;
  private final List<DmpReporter> reporters;
  private final MessageSourceUtils messages;

  public DMPUpdateHandler(
      DMPManager dmpManager,
      DmpPlatformStatusResolver statusResolver,
      List<DmpReporter> reporters,
      MessageSourceUtils messages) {
    this.dmpManager = dmpManager;
    this.statusResolver = statusResolver;
    this.reporters = reporters;
    this.messages = messages;
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
    Map<DMPSource, PlatformStatus> statusByPlatform = new EnumMap<>(DMPSource.class);
    List<PlanReportResult> results = new ArrayList<>();
    for (DMPUser plan : dmpManager.findDMPsForUser(subject)) {
      if (dmpUserIds.contains(plan.getId())) {
        results.add(reportPlan(plan, deposit, subject, statusByPlatform));
      }
    }
    return results;
  }

  /**
   * Every plan the user can tick when depositing, whatever is switched on, with the reason RSpace
   * will not update it, if there is one.
   *
   * @param user the plans' owner
   * @return one entry per plan; its write-back status is {@code OK} or a {@link PlanReportStatus}
   *     name saying why the plan will not be updated
   */
  public List<LinkedDMP> linkablePlans(User user) {
    Map<DMPSource, PlatformStatus> statusByPlatform = new EnumMap<>(DMPSource.class);
    List<LinkedDMP> linkable = new ArrayList<>();
    for (DMPUser plan : dmpManager.detectMissingSources(user)) {
      String platformName =
          isUnknownSource(plan)
              ? ""
              : messages.getMessage(DmpPlatformStatusResolver.platformNameKey(plan.getSource()));
      String writeBackStatus =
          noWriteBackReason(plan, user, statusByPlatform).map(Enum::name).orElse("OK");
      linkable.add(
          new LinkedDMP(
              plan.getId(), plan.getTitle(), plan.getDmpId(), platformName, writeBackStatus));
    }
    return linkable;
  }

  /**
   * The one rule for whether a plan can be updated, used both when plans are offered and after a
   * deposit.
   *
   * @return why RSpace will not update the plan, or empty if it will try
   */
  private Optional<PlanReportStatus> noWriteBackReason(
      DMPUser plan, User user, Map<DMPSource, PlatformStatus> statusByPlatform) {
    if (isUnknownSource(plan)) {
      return Optional.of(PlanReportStatus.UNKNOWN_SOURCE);
    }
    DMPSource platform = plan.getSource();
    PlatformStatus status =
        statusByPlatform.computeIfAbsent(platform, source -> statusResolver.resolve(source, user));
    if (status == PlatformStatus.PLATFORM_OFF) {
      return Optional.of(PlanReportStatus.PLATFORM_OFF);
    }
    if (reporterFor(platform).isEmpty()) {
      return Optional.of(PlanReportStatus.NOT_SUPPORTED);
    }
    if (status == PlatformStatus.NOT_CONNECTED) {
      return Optional.of(PlanReportStatus.NOT_CONNECTED);
    }
    return Optional.empty();
  }

  private static boolean isUnknownSource(DMPUser plan) {
    return plan.getSource() == null || plan.getSource() == DMPSource.UNKNOWN;
  }

  private Optional<DmpReporter> reporterFor(DMPSource platform) {
    return reporters.stream().filter(r -> r.supports(platform)).findFirst();
  }

  private PlanReportResult reportPlan(
      DMPUser plan,
      DepositReport deposit,
      User subject,
      Map<DMPSource, PlatformStatus> statusByPlatform) {
    PlanReportResult result = attemptPlan(plan, deposit, subject, statusByPlatform);
    logOutcome(plan, subject, result);
    return result;
  }

  private static void logOutcome(DMPUser plan, User subject, PlanReportResult result) {
    boolean updated =
        result.status() == PlanReportStatus.REPORTED
            && result.values().stream().allMatch(ValueOutcome::written);
    String written =
        result.status() == PlanReportStatus.REPORTED
            ? ", written: "
                + result.values().stream()
                    .map(value -> String.valueOf(value.written()))
                    .collect(Collectors.joining(","))
            : "";
    String line = "Write-back to plan row {} of user {} on {} (plan id {}): {}{}";
    Object[] args = {
      plan.getId(),
      subject.getUsername(),
      result.platform(),
      planIdForLog(plan.getDmpId()),
      result.status(),
      written
    };
    if (updated) {
      log.info(line, args);
    } else {
      log.warn(line, args);
    }
  }

  private PlanReportResult attemptPlan(
      DMPUser plan,
      DepositReport deposit,
      User subject,
      Map<DMPSource, PlatformStatus> statusByPlatform) {
    DMPSource platform = isUnknownSource(plan) ? DMPSource.UNKNOWN : plan.getSource();
    try {
      Optional<PlanReportStatus> noWriteBack = noWriteBackReason(plan, subject, statusByPlatform);
      if (noWriteBack.isPresent()) {
        return withStatus(plan, platform, noWriteBack.get());
      }
      if (deposit.identifier() == null && deposit.accessUrl() == null) {
        return withStatus(plan, platform, PlanReportStatus.NOTHING_TO_SEND);
      }
      return reporterFor(platform).orElseThrow().report(plan, deposit, subject);
    } catch (RuntimeException e) {
      log.warn(
          "Write-back to plan {} of user {} on {} failed",
          planIdForLog(plan.getDmpId()),
          subject.getUsername(),
          platform,
          e);
      return new PlanReportResult(
          plan.getTitle(),
          platform,
          PlanReportStatus.REPORTED,
          List.of(
              ValueOutcome.failed(
                  deposit, messages.getMessage(ValueOutcome.UNEXPECTED_ERROR_KEY))));
    }
  }

  private static PlanReportResult withStatus(
      DMPUser plan, DMPSource platform, PlanReportStatus status) {
    return PlanReportResult.withStatus(plan.getTitle(), platform, status);
  }
}

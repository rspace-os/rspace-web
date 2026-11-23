package com.researchspace.webapp.integrations.dmptool;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.DmpReporter;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ReportedValueKind;
import com.researchspace.service.dmp.ValueOutcome;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Adds a deposit's link to a DMPtool plan as a related identifier. */
@Slf4j
public class DmpToolReporter implements DmpReporter {

  private final DMPToolDMPProvider provider;

  public DmpToolReporter(DMPToolDMPProvider provider) {
    this.provider = provider;
  }

  @Override
  public boolean supports(DMPSource source) {
    return source == DMPSource.DMP_TOOL;
  }

  @Override
  public PlanReportResult report(DMPUser plan, DepositReport deposit, User user) {
    boolean hasDoi = deposit.identifier() != null;
    ReportedValueKind kind = hasDoi ? ReportedValueKind.IDENTIFIER : ReportedValueKind.ACCESS_URL;
    String value = (hasDoi ? deposit.identifier() : deposit.accessUrl()).toString();
    ValueOutcome outcome;
    try {
      ServiceOperationResult<String> sent =
          provider.addRelatedIdentifierToDMP(plan.getDmpId(), hasDoi ? "doi" : "url", value, user);
      outcome =
          sent.isSucceeded()
              ? new ValueOutcome(kind, value, true, null)
              : new ValueOutcome(kind, value, false, failureMessage(sent));
    } catch (RuntimeException e) {
      log.warn("Couldn't add the deposit's {} to DMPtool plan {}", kind, plan.getDmpId(), e);
      outcome =
          new ValueOutcome(
              kind,
              value,
              false,
              StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
    }
    return new PlanReportResult(
        plan.getTitle(),
        plan.getDmpId(),
        DMPSource.DMP_TOOL,
        PlanReportStatus.REPORTED,
        List.of(outcome));
  }

  // ServiceOperationResult.fromOptionalError puts the error in the entity, not the message
  private static String failureMessage(ServiceOperationResult<String> sent) {
    return StringUtils.isNotBlank(sent.getMessage()) ? sent.getMessage() : sent.getEntity();
  }
}

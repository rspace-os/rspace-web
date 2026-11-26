package com.researchspace.webapp.integrations.dmptool;

import static com.researchspace.service.dmp.DmpLogging.planIdForLog;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.DmpReporter;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ValueOutcome;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Adds a deposit's link to a DMPtool plan as a related identifier. */
@Slf4j
public class DmpToolReporter implements DmpReporter {

  private final DMPToolDMPProvider provider;
  private final MessageSourceUtils messages;

  public DmpToolReporter(DMPToolDMPProvider provider, MessageSourceUtils messages) {
    this.provider = provider;
    this.messages = messages;
  }

  @Override
  public boolean supports(DMPSource source) {
    return source == DMPSource.DMP_TOOL;
  }

  @Override
  public PlanReportResult report(DMPUser plan, DepositReport deposit, User user) {
    boolean hasDoi = deposit.identifier() != null;
    String value = (hasDoi ? deposit.identifier() : deposit.accessUrl()).toString();
    ValueOutcome outcome;
    try {
      ServiceOperationResult<String> sent =
          provider.addRelatedIdentifierToDMP(plan.getDmpId(), hasDoi ? "doi" : "url", value, user);
      outcome =
          sent.isSucceeded()
              ? new ValueOutcome(value, true, null)
              : new ValueOutcome(value, false, failureMessage(sent));
    } catch (RuntimeException e) {
      log.warn(
          "Couldn't add the deposit's {} to DMPtool plan {} of user {}",
          hasDoi ? "DOI link" : "repository link",
          planIdForLog(plan.getDmpId()),
          user.getUsername(),
          e);
      outcome =
          new ValueOutcome(value, false, messages.getMessage(ValueOutcome.UNEXPECTED_ERROR_KEY));
    }
    return new PlanReportResult(
        plan.getTitle(), DMPSource.DMP_TOOL, PlanReportStatus.REPORTED, List.of(outcome));
  }

  // ServiceOperationResult.fromOptionalError puts the error in the entity, not the message
  private static String failureMessage(ServiceOperationResult<String> sent) {
    return StringUtils.isNotBlank(sent.getMessage()) ? sent.getMessage() : sent.getEntity();
  }
}

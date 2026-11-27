package com.researchspace.webapp.integrations.dmptool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ValueOutcome;
import com.researchspace.testutils.TestFactory;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DmpToolReporterTest {

  private static final String DOI = "https://doi.org/10.1234/abc";
  private static final String LANDING = "https://repo.example.org/dataset/1";

  private @Mock DMPToolDMPProvider provider;
  private DmpToolReporter reporter;
  private User user;
  private DMPUser plan;

  @BeforeEach
  void setUp() {
    reporter = new DmpToolReporter(provider, new MessageSourceUtils(new JsonMessageSource()));
    user = TestFactory.createAnyUser("any");
    plan = new DMPUser(user, new DmpDto("123", "My plan", DMPSource.DMP_TOOL, null, null));
  }

  @Test
  void supportsDmpToolOnly() {
    assertTrue(reporter.supports(DMPSource.DMP_TOOL));
    assertFalse(reporter.supports(DMPSource.DMP_ONLINE));
  }

  @Test
  void doiLinkIsSentAsDoi() throws Exception {
    when(provider.addRelatedIdentifierToDMP("123", "doi", DOI, user))
        .thenReturn(new ServiceOperationResult<>("DMP updated", true));

    PlanReportResult result =
        reporter.report(plan, new DepositReport("Repo", new URL(DOI), null), user);

    assertEquals(
        new PlanReportResult(
            "My plan",
            DMPSource.DMP_TOOL,
            PlanReportStatus.REPORTED,
            List.of(new ValueOutcome(DOI, true, null))),
        result);
  }

  @Test
  void landingPageIsSentAsUrl() throws Exception {
    when(provider.addRelatedIdentifierToDMP("123", "url", LANDING, user))
        .thenReturn(new ServiceOperationResult<>("DMP updated", true));

    PlanReportResult result =
        reporter.report(plan, new DepositReport("Repo", null, new URL(LANDING)), user);

    assertEquals(List.of(new ValueOutcome(LANDING, true, null)), result.values());
  }

  @Test
  void failedSendGivesFailedOutcomeWithProviderMessage() throws Exception {
    when(provider.addRelatedIdentifierToDMP("123", "doi", DOI, user))
        .thenReturn(
            ServiceOperationResult.fromOptionalError(
                Optional.of("DMPTool refused the update (error 403).")));

    PlanReportResult result =
        reporter.report(plan, new DepositReport("Repo", new URL(DOI), null), user);

    assertEquals(
        List.of(new ValueOutcome(DOI, false, "DMPTool refused the update (error 403).")),
        result.values());
  }

  @Test
  void unexpectedRuntimeErrorGivesFailedOutcome() throws Exception {
    when(provider.addRelatedIdentifierToDMP("123", "doi", DOI, user))
        .thenThrow(new IllegalStateException("boom"));

    PlanReportResult result =
        reporter.report(plan, new DepositReport("Repo", new URL(DOI), null), user);

    assertEquals(PlanReportStatus.REPORTED, result.status());
    assertEquals(
        List.of(new ValueOutcome(DOI, false, "An unexpected error occurred.")), result.values());
  }
}

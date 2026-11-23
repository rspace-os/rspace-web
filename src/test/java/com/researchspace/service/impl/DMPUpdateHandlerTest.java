package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.service.DMPManager;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.DmpPlatformStatusResolver;
import com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus;
import com.researchspace.service.dmp.DmpReporter;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ReportedValueKind;
import com.researchspace.service.dmp.ValueOutcome;
import com.researchspace.testutils.TestFactory;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DMPUpdateHandlerTest {

  private static final String DOI = "https://doi.org/10.1234/abc";
  private static final String LANDING = "https://repo.example.org/dataset/1";

  private @Mock DMPManager dmpManager;
  private @Mock DmpPlatformStatusResolver statusResolver;
  private @Mock DmpReporter dmpToolReporter;

  private DMPUpdateHandler handler;
  private User user;

  @BeforeEach
  void setUp() {
    handler = new DMPUpdateHandler(dmpManager, statusResolver, List.of(dmpToolReporter));
    user = TestFactory.createAnyUser("any");
  }

  @Test
  void dmpToolPlanGoesToDmpToolReporter() throws Exception {
    DMPUser plan = plan(1L, DMPSource.DMP_TOOL);
    DepositReport deposit = deposit(DOI, null);
    PlanReportResult reported = reported(plan);
    givenPlans(plan);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.report(plan, deposit, user)).thenReturn(reported);

    assertThat(handler.report(deposit, user, List.of(1L))).containsExactly(reported);
  }

  @Test
  void unknownPlanGoesToDmpToolReporter() throws Exception {
    DMPUser plan = plan(1L, DMPSource.UNKNOWN);
    DepositReport deposit = deposit(DOI, null);
    PlanReportResult reported = reported(plan);
    givenPlans(plan);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.report(plan, deposit, user)).thenReturn(reported);

    assertThat(handler.report(deposit, user, List.of(1L))).containsExactly(reported);
  }

  @ParameterizedTest
  @EnumSource(
      value = DMPSource.class,
      names = {"DMP_ONLINE", "ARGOS", "DMP_ASSISTANT", "DSW"})
  void otherPlatformsAreNotSupported(DMPSource source) throws Exception {
    givenPlans(plan(1L, source));
    when(dmpToolReporter.supports(source)).thenReturn(false);

    List<PlanReportResult> results = handler.report(deposit(DOI, null), user, List.of(1L));

    assertEquals(PlanReportStatus.NOT_SUPPORTED, results.get(0).status());
    assertEquals(source, results.get(0).platform());
    verify(dmpToolReporter, never()).report(any(), any(), any());
    verifyNoInteractions(statusResolver);
  }

  @Test
  void nothingToSendWithoutDoiOrLandingPage() throws Exception {
    givenPlans(plan(1L, DMPSource.DMP_TOOL));
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);

    List<PlanReportResult> results = handler.report(deposit(null, null), user, List.of(1L));

    assertEquals(PlanReportStatus.NOTHING_TO_SEND, results.get(0).status());
    verify(dmpToolReporter, never()).report(any(), any(), any());
  }

  @Test
  void landingPageWithoutDoiGoesToReporter() throws Exception {
    DMPUser plan = plan(1L, DMPSource.DMP_TOOL);
    DepositReport deposit = deposit(null, LANDING);
    PlanReportResult reported = reported(plan);
    givenPlans(plan);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.report(plan, deposit, user)).thenReturn(reported);

    assertThat(handler.report(deposit, user, List.of(1L))).containsExactly(reported);
  }

  @ParameterizedTest
  @EnumSource(
      value = PlatformStatus.class,
      names = {"PLATFORM_OFF", "NOT_CONNECTED"})
  void platformOffOrNotConnectedSkipsReporter(PlatformStatus status) throws Exception {
    givenPlans(plan(1L, DMPSource.DMP_TOOL));
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(status);

    List<PlanReportResult> results = handler.report(deposit(DOI, null), user, List.of(1L));

    assertEquals(PlanReportStatus.valueOf(status.name()), results.get(0).status());
    verify(dmpToolReporter, never()).report(any(), any(), any());
  }

  @Test
  void oneFailingPlanDoesNotStopTheNext() throws Exception {
    DMPUser first = plan(1L, DMPSource.DMP_TOOL);
    DMPUser second = plan(2L, DMPSource.DMP_TOOL);
    DepositReport deposit = deposit(DOI, null);
    PlanReportResult secondReported = reported(second);
    givenPlans(first, second);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.report(first, deposit, user)).thenThrow(new IllegalStateException("boom"));
    when(dmpToolReporter.report(second, deposit, user)).thenReturn(secondReported);

    List<PlanReportResult> results = handler.report(deposit, user, List.of(1L, 2L));

    assertThat(results).hasSize(2);
    assertEquals(PlanReportStatus.REPORTED, results.get(0).status());
    assertEquals(
        new ValueOutcome(ReportedValueKind.IDENTIFIER, DOI, false, "boom"),
        results.get(0).values().get(0));
    assertEquals(secondReported, results.get(1));
  }

  @Test
  void idThatIsNotOneOfTheUsersPlansIsIgnored() throws Exception {
    givenPlans(plan(1L, DMPSource.DMP_TOOL));

    assertThat(handler.report(deposit(DOI, null), user, List.of(-100L))).isEmpty();
    verifyNoInteractions(dmpToolReporter, statusResolver);
  }

  @Test
  void noTickedPlansDoesNoLookup() throws Exception {
    assertThat(handler.report(deposit(DOI, null), user, List.of())).isEmpty();
    verifyNoInteractions(dmpManager, dmpToolReporter, statusResolver);
  }

  @Test
  void storedPlanRowIsNotChanged() throws Exception {
    DMPUser plan = plan(1L, DMPSource.DMP_TOOL);
    plan.setDoiLink("https://doi.org/ORIGINAL");
    DepositReport deposit = deposit(DOI, null);
    givenPlans(plan);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.report(plan, deposit, user)).thenReturn(reported(plan));

    handler.report(deposit, user, List.of(1L));

    assertEquals("https://doi.org/ORIGINAL", plan.getDoiLink());
    verify(dmpManager, never()).save(any());
  }

  @Test
  void writeBackIsSupportedOnlyWhereAReporterExists() {
    givenDmpToolSupported();
    when(dmpToolReporter.supports(DMPSource.DMP_ONLINE)).thenReturn(false);

    assertTrue(handler.supportsWriteBack(DMPSource.UNKNOWN));
    assertFalse(handler.supportsWriteBack(DMPSource.DMP_ONLINE));
  }

  private void givenPlans(DMPUser... plans) {
    when(dmpManager.findDMPsForUser(user)).thenReturn(List.of(plans));
  }

  private void givenDmpToolSupported() {
    when(dmpToolReporter.supports(DMPSource.DMP_TOOL)).thenReturn(true);
  }

  private DMPUser plan(long id, DMPSource source) {
    DMPUser plan = new DMPUser(user, new DmpDto("id" + id, "title" + id, source, null, null));
    ReflectionTestUtils.setField(plan, "id", id);
    return plan;
  }

  private DepositReport deposit(String doi, String landing) throws Exception {
    return new DepositReport(
        "Repo", doi == null ? null : new URL(doi), landing == null ? null : new URL(landing));
  }

  private PlanReportResult reported(DMPUser plan) {
    return new PlanReportResult(
        plan.getTitle(),
        plan.getDmpId(),
        DMPSource.DMP_TOOL,
        PlanReportStatus.REPORTED,
        List.of(new ValueOutcome(ReportedValueKind.IDENTIFIER, DOI, true, null)));
  }
}

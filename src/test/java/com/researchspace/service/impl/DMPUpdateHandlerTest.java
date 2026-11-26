package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.model.repository.LinkedDMP;
import com.researchspace.service.DMPManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.DmpPlatformStatusResolver;
import com.researchspace.service.dmp.DmpPlatformStatusResolver.PlatformStatus;
import com.researchspace.service.dmp.DmpReporter;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ValueOutcome;
import com.researchspace.testutils.TestFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
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
  private static final String UNEXPECTED_ERROR = "An unexpected error occurred.";

  private @Mock DMPManager dmpManager;
  private @Mock DmpPlatformStatusResolver statusResolver;
  private @Mock DmpReporter dmpToolReporter;

  private final EventCapture logs = new EventCapture();
  private final Logger logger = (Logger) LogManager.getLogger(DMPUpdateHandler.class);

  private Level levelBefore;
  private DMPUpdateHandler handler;
  private User user;

  @BeforeEach
  void setUp() {
    handler =
        new DMPUpdateHandler(
            dmpManager,
            statusResolver,
            List.of(dmpToolReporter),
            new MessageSourceUtils(new JsonMessageSource()));
    user = TestFactory.createAnyUser("any");
    logs.start();
    logger.addAppender(logs);
    levelBefore = logger.getLevel();
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void removeLogCapture() {
    logger.setLevel(levelBefore);
    logger.removeAppender(logs);
    logs.stop();
  }

  @Test
  void skippedPlanIsLoggedAsWarningWithItsRowAndUser() throws Exception {
    givenPlans(plan(7L, DMPSource.DMP_TOOL));
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.PLATFORM_OFF);

    handler.report(deposit(DOI, null), user, List.of(7L));

    assertEquals(1, logs.events.size());
    assertEquals(Level.WARN, logs.events.get(0).getLevel());
    assertThat(logs.events.get(0).getMessage().getFormattedMessage())
        .contains("7", user.getUsername(), "DMP_TOOL", "id7", "PLATFORM_OFF");
  }

  @Test
  void writtenValueIsLoggedAsInfo() throws Exception {
    DMPUser plan = plan(7L, DMPSource.DMP_TOOL);
    givenPlans(plan);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    DepositReport deposit = deposit(DOI, null);
    when(dmpToolReporter.report(plan, deposit, user)).thenReturn(reported(plan));

    handler.report(deposit, user, List.of(7L));

    assertEquals(1, logs.events.size());
    assertEquals(Level.INFO, logs.events.get(0).getLevel());
    assertThat(logs.events.get(0).getMessage().getFormattedMessage())
        .contains("7", user.getUsername(), "REPORTED", "written: true");
  }

  @Test
  void lineBreakInPlanIdCannotStartAnotherLogLine() throws Exception {
    DMPUser plan =
        new DMPUser(
            user, new DmpDto("id\r\nWARN forged line", "title", DMPSource.DMP_TOOL, null, null));
    ReflectionTestUtils.setField(plan, "id", 7L);
    givenPlans(plan);
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.PLATFORM_OFF);

    handler.report(deposit(DOI, null), user, List.of(7L));

    assertEquals(1, logs.events.size());
    assertThat(logs.events.get(0).getMessage().getFormattedMessage())
        .doesNotContain("\r", "\n")
        .contains("WARN forged line");
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
  void unknownPlanIsNotReported() throws Exception {
    givenPlans(plan(1L, DMPSource.UNKNOWN));

    List<PlanReportResult> results = handler.report(deposit(DOI, null), user, List.of(1L));

    assertEquals(PlanReportStatus.UNKNOWN_SOURCE, results.get(0).status());
    verifyNoInteractions(statusResolver, dmpToolReporter);
  }

  @ParameterizedTest
  @EnumSource(
      value = DMPSource.class,
      names = {"DMP_ONLINE", "ARGOS", "DMP_ASSISTANT", "DSW"})
  void otherPlatformsAreNotSupported(DMPSource source) throws Exception {
    givenPlans(plan(1L, source));
    when(statusResolver.resolve(source, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.supports(source)).thenReturn(false);

    List<PlanReportResult> results = handler.report(deposit(DOI, null), user, List.of(1L));

    assertEquals(PlanReportStatus.NOT_SUPPORTED, results.get(0).status());
    assertEquals(source, results.get(0).platform());
    verify(dmpToolReporter, never()).report(any(), any(), any());
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

  @Test
  void platformOffSkipsReporter() throws Exception {
    givenPlans(plan(1L, DMPSource.DMP_TOOL));
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.PLATFORM_OFF);

    List<PlanReportResult> results = handler.report(deposit(DOI, null), user, List.of(1L));

    assertEquals(PlanReportStatus.PLATFORM_OFF, results.get(0).status());
    verifyNoInteractions(dmpToolReporter);
  }

  @Test
  void notConnectedSkipsReporter() throws Exception {
    givenPlans(plan(1L, DMPSource.DMP_TOOL));
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.NOT_CONNECTED);

    List<PlanReportResult> results = handler.report(deposit(DOI, null), user, List.of(1L));

    assertEquals(PlanReportStatus.NOT_CONNECTED, results.get(0).status());
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
    assertEquals(new ValueOutcome(DOI, false, UNEXPECTED_ERROR), results.get(0).values().get(0));
    assertEquals(secondReported, results.get(1));
  }

  @Test
  void twoTickedPlansAreBothReportedInOrder() throws Exception {
    DMPUser first = plan(1L, DMPSource.DMP_TOOL);
    DMPUser second = plan(2L, DMPSource.DMP_TOOL);
    DepositReport deposit = deposit(DOI, null);
    givenPlans(first, second);
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.report(first, deposit, user)).thenReturn(reported(first));
    when(dmpToolReporter.report(second, deposit, user)).thenReturn(reported(second));

    assertThat(handler.report(deposit, user, List.of(1L, 2L)))
        .containsExactly(reported(first), reported(second));
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
  void everyPlanIsLinkableWhateverIsSwitchedOn() {
    givenLinkablePlans(
        plan(1L, DMPSource.DMP_TOOL),
        plan(2L, DMPSource.DMP_ONLINE),
        plan(3L, DMPSource.DSW),
        plan(4L, DMPSource.UNKNOWN));
    when(statusResolver.resolve(any(DMPSource.class), eq(user)))
        .thenReturn(PlatformStatus.PLATFORM_OFF);

    assertThat(handler.linkablePlans(user))
        .containsExactly(
            new LinkedDMP(1L, "title1", "id1", "DMPTool", "PLATFORM_OFF"),
            new LinkedDMP(2L, "title2", "id2", "DMPonline", "PLATFORM_OFF"),
            new LinkedDMP(3L, "title3", "id3", "DSW / FAIR Wizard", "PLATFORM_OFF"),
            new LinkedDMP(4L, "title4", "id4", "", "UNKNOWN_SOURCE"));
  }

  @Test
  void linkablePlanOnConnectedDmpToolHasNoWarning() {
    givenLinkablePlans(plan(1L, DMPSource.DMP_TOOL));
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.OK);

    assertThat(handler.linkablePlans(user))
        .containsExactly(new LinkedDMP(1L, "title1", "id1", "DMPTool", "OK"));
  }

  @Test
  void linkablePlanIsMarkedNotConnectedWhenItsPlatformHasWriteBack() {
    givenLinkablePlans(plan(1L, DMPSource.DMP_TOOL));
    givenDmpToolSupported();
    when(statusResolver.resolve(DMPSource.DMP_TOOL, user)).thenReturn(PlatformStatus.NOT_CONNECTED);

    assertThat(handler.linkablePlans(user))
        .containsExactly(new LinkedDMP(1L, "title1", "id1", "DMPTool", "NOT_CONNECTED"));
  }

  @ParameterizedTest
  @EnumSource(
      value = DMPSource.class,
      names = {"DMP_ONLINE", "ARGOS", "DMP_ASSISTANT", "DSW"})
  void linkablePlanOnPlatformWithoutWriteBackIsMarkedNotSupported(DMPSource source) {
    givenLinkablePlans(plan(1L, source));
    when(statusResolver.resolve(source, user)).thenReturn(PlatformStatus.OK);
    when(dmpToolReporter.supports(source)).thenReturn(false);

    assertEquals("NOT_SUPPORTED", handler.linkablePlans(user).get(0).getWriteBackStatus());
  }

  @Test
  void unknownPlanIsLinkableWithoutPlatformAndNoStatusLookup() {
    givenLinkablePlans(plan(1L, DMPSource.UNKNOWN));

    assertThat(handler.linkablePlans(user))
        .containsExactly(new LinkedDMP(1L, "title1", "id1", "", "UNKNOWN_SOURCE"));
    verifyNoInteractions(statusResolver, dmpToolReporter);
  }

  private void givenPlans(DMPUser... plans) {
    when(dmpManager.findDMPsForUser(user)).thenReturn(List.of(plans));
  }

  private static class EventCapture extends AbstractAppender {
    final List<LogEvent> events = new ArrayList<>();

    EventCapture() {
      super("dmpUpdateHandlerCapture", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }

  private void givenLinkablePlans(DMPUser... plans) {
    when(dmpManager.detectMissingSources(user)).thenReturn(List.of(plans));
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
        DMPSource.DMP_TOOL,
        PlanReportStatus.REPORTED,
        List.of(new ValueOutcome(DOI, true, null)));
  }
}

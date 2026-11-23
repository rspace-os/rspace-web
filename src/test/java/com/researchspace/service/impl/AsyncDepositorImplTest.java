package com.researchspace.service.impl;

import static com.researchspace.model.apps.App.APP_DATAVERSE;
import static com.researchspace.model.apps.App.APP_ZENODO;
import static com.researchspace.service.dmp.ReportedValueKind.IDENTIFIER;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.dtos.RaidUpdateResult;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ValueOutcome;
import com.researchspace.service.raid.RaIDDoiNotFoundException;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.testutils.SystemPropertyTestFactory;
import com.researchspace.testutils.VelocityTestUtils;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
public class AsyncDepositorImplTest {
  private static final String DRYAD_DOI = "doi%3A10.5061%2Fdryad.abc123";

  @Mock CommunicationManager comm;
  private @Mock RaIDServiceClientAdapter mockRaidServiceClientAdapter;
  private @Mock DMPUpdateHandler dmpUpdateHandler;
  AsyncDepositorImpl depositor;

  VelocityEngine engine;
  AsyncDepositorImplTSS underTest;
  User anyUser;
  File testFile;

  class AsyncDepositorImplTSS extends AsyncDepositorImpl {

    boolean updateDMPsCalled = false;

    @Override
    void updateDMPS(
        RepositoryOperationResult result,
        App app,
        User subject,
        RepoDepositConfig repoDepositConfig) {
      this.updateDMPsCalled = true;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    underTest = new AsyncDepositorImplTSS();
    engine =
        VelocityTestUtils.setupVelocity(
            "src/main/resources/velocityTemplates/messageAndNotificationEmails");
    underTest.setVelocity(engine);
    underTest.setMessages(new MessageSourceUtils(new JsonMessageSource()));
    underTest.setCommMgr(comm);
    underTest.setRaIDServiceClientAdapter(mockRaidServiceClientAdapter);
    anyUser = createAnyUser("any");
    testFile = File.createTempFile("test", ".txt");

    depositor = new AsyncDepositorImpl();
    depositor.setVelocity(engine);
    depositor.setMessages(new MessageSourceUtils(new JsonMessageSource()));
    depositor.setCommMgr(comm);
    depositor.setDmpUpdateHandler(dmpUpdateHandler);
  }

  @Test
  public void testMessageNoLink() {
    RepositoryOperationResult result = new RepositoryOperationResult(true, "hello", null, null);
    underTest.postDeposit(
        result,
        SystemPropertyTestFactory.createAnyApp(),
        anyUser,
        testFile,
        new RepoDepositConfig());
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            Mockito.contains("hello"),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    Mockito.verifyNoInteractions(mockRaidServiceClientAdapter);
    assertTrue(underTest.updateDMPsCalled);
  }

  @Test
  public void testMessageLink() throws MalformedURLException {
    RepositoryOperationResult result =
        new RepositoryOperationResult(
            true,
            "hello",
            new URL("http://www.bbc.co.uk"),
            new URL("http://doi.org/10.12384/ACHFAT"));
    underTest.postDeposit(
        result,
        SystemPropertyTestFactory.createAnyApp(),
        anyUser,
        testFile,
        new RepoDepositConfig());
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains("http://www.bbc.co.uk")),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    Mockito.verifyNoMoreInteractions(comm);
    assertTrue(underTest.updateDMPsCalled);
    Mockito.verifyNoInteractions(mockRaidServiceClientAdapter);
  }

  @Test
  public void testMessageDoiLinkWithRaidAndDataverse()
      throws MalformedURLException,
          URISyntaxException,
          JsonProcessingException,
          ExecutionException,
          InterruptedException {
    // GIVEN
    String repoName = "Dataverse";
    String expectedResultUrl =
        "https://dataverse.org/dataset.xhtml?persistentId=doi:10.70122/FK2/FNGEGH";
    String expectedDoiLink = "https://doi.org/10.70122/FK2/FNGEGH";
    String expectedRaidIdentifier = "https://raid.org/10.12345/ERTY88";
    String expectedRaidUrl = "https://demo.app.raid.org.au/raid/10.12345/ERTY88";
    String originalRaidUrl = "https://demo.static.raid.org.au/raid/10.12345/ERTY88";
    RaIDReferenceDTO expectedRaidReference =
        new RaIDReferenceDTO("serverAlias1", "RaidTitle", expectedRaidIdentifier, originalRaidUrl);
    when(mockRaidServiceClientAdapter.addRaIDRelatedObject(
            anyUser.getUsername(), expectedRaidReference, expectedDoiLink))
        .thenReturn(true);

    RepoDepositConfig raidDepositConfig = new RepoDepositConfig();
    raidDepositConfig.setExportToRaid(true);
    raidDepositConfig.setAppName(APP_DATAVERSE);
    raidDepositConfig.setRaidAssociated(
        new RaidGroupAssociationDTO(1L, "RSpaceProjectName", expectedRaidReference));

    RaidUpdateResult expectedRaidUpdateResult =
        new RaidUpdateResult(
            true, repoName, expectedRaidIdentifier, expectedRaidUrl, expectedDoiLink);
    Future<RepositoryOperationResult> futureRepoOperationResult =
        CompletableFuture.completedFuture(
            new RepositoryOperationResult(
                true, "", new URL(expectedResultUrl), new URL(expectedDoiLink)));

    // WHEN
    Future<RaidUpdateResult> futureRaidUpdateResult =
        underTest.reportDoiToRaid(
            new App(APP_DATAVERSE, repoName, true),
            futureRepoOperationResult,
            anyUser,
            raidDepositConfig);

    // THEN
    Mockito.verify(mockRaidServiceClientAdapter)
        .addRaIDRelatedObject(anyUser.getUsername(), expectedRaidReference, expectedDoiLink);
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains(expectedDoiLink)),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    RaidUpdateResult actualRaidUpdateResult = futureRaidUpdateResult.get();
    assertEquals(expectedRaidUpdateResult, actualRaidUpdateResult);
  }

  @Test
  public void testMessageDoiLinkWithRaidAndZenodo()
      throws MalformedURLException,
          URISyntaxException,
          JsonProcessingException,
          ExecutionException,
          InterruptedException {
    // GIVEN
    String repoName = "Zenodo";
    String expectedResultUrl = "https://zenodo.org/records/18663592";
    String expectedDoiLink = "https://doi.org/10.70122/FK2/FNGEGH";
    String expectedRaidIdentifier = "https://raid.org/10.12345/ERTY88";
    String expectedRaidUrl = "https://demo.app.raid.org.au/raid/10.12345/ERTY88";
    String originalRaidUrl = "https://demo.static.raid.org.au/raid/10.12345/ERTY88";
    RaIDReferenceDTO expectedRaidReference =
        new RaIDReferenceDTO("serverAlias1", "RaidTitle", expectedRaidIdentifier, originalRaidUrl);
    when(mockRaidServiceClientAdapter.addRaIDRelatedObject(
            anyUser.getUsername(), expectedRaidReference, expectedDoiLink))
        .thenReturn(true);

    RepoDepositConfig raidDepositConfig = new RepoDepositConfig();
    raidDepositConfig.setExportToRaid(true);
    raidDepositConfig.setAppName(APP_ZENODO);
    raidDepositConfig.setRaidAssociated(
        new RaidGroupAssociationDTO(1L, "RSpaceProjectName", expectedRaidReference));

    RaidUpdateResult expectedRaidUpdateResult =
        new RaidUpdateResult(
            true, repoName, expectedRaidIdentifier, expectedRaidUrl, expectedDoiLink);
    Future<RepositoryOperationResult> futureRepoOperationResult =
        CompletableFuture.completedFuture(
            new RepositoryOperationResult(
                true, "", new URL(expectedResultUrl), new URL(expectedDoiLink)));

    // WHEN
    Future<RaidUpdateResult> futureRaidUpdateResult =
        underTest.reportDoiToRaid(
            new App(APP_ZENODO, repoName, true),
            futureRepoOperationResult,
            anyUser,
            raidDepositConfig);

    // THEN
    Mockito.verify(mockRaidServiceClientAdapter)
        .addRaIDRelatedObject(anyUser.getUsername(), expectedRaidReference, expectedDoiLink);
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains(expectedDoiLink)),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    RaidUpdateResult actualRaidUpdateResult = futureRaidUpdateResult.get();
    assertEquals(expectedRaidUpdateResult, actualRaidUpdateResult);
  }

  @Test
  public void testMessageDoiLinkWhenRaidUpdateFails()
      throws MalformedURLException,
          URISyntaxException,
          JsonProcessingException,
          ExecutionException,
          InterruptedException {
    // GIVEN
    String repoName = "Zenodo";
    String expectedResultUrl = "https://zenodo.org/records/18663592";
    String expectedDoiLink = "https://doi.org/10.5281/zenodo.18663592";
    String expectedRaidIdentifier = "https://raid.org/10.12345/ERTY88";
    String expectedRaidUrl = "https://demo.app.raid.org.au/raid/10.12345/ERTY88";
    String originalRaidUrl = "https://demo.static.raid.org.au/raid/10.12345/ERTY88";
    RaIDReferenceDTO expectedRaidReference =
        new RaIDReferenceDTO("serverAlias1", "RaidTitle", expectedRaidIdentifier, originalRaidUrl);
    when(mockRaidServiceClientAdapter.addRaIDRelatedObject(
            anyUser.getUsername(), expectedRaidReference, expectedDoiLink))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    RepoDepositConfig raidDepositConfig = new RepoDepositConfig();
    raidDepositConfig.setExportToRaid(true);
    raidDepositConfig.setAppName(APP_ZENODO);
    raidDepositConfig.setRaidAssociated(
        new RaidGroupAssociationDTO(1L, "RSpaceProjectName", expectedRaidReference));

    RaidUpdateResult expectedRaidUpdateResult =
        new RaidUpdateResult(
            false, repoName, expectedRaidIdentifier, expectedRaidUrl, expectedDoiLink);
    Future<RepositoryOperationResult> futureRepoOperationResult =
        CompletableFuture.completedFuture(
            new RepositoryOperationResult(
                true, "", new URL(expectedResultUrl), new URL(expectedDoiLink)));

    // WHEN
    Future<RaidUpdateResult> futureRaidUpdateResult =
        underTest.reportDoiToRaid(
            new App(APP_ZENODO, repoName, true),
            futureRepoOperationResult,
            anyUser,
            raidDepositConfig);

    // THEN
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains(expectedDoiLink)),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    RaidUpdateResult actualRaidUpdateResult = futureRaidUpdateResult.get();
    assertEquals(expectedRaidUpdateResult, actualRaidUpdateResult);
  }

  @Test
  public void testMessageExplainsRefusedDoiWhenRaidCannotFindIt()
      throws MalformedURLException,
          URISyntaxException,
          JsonProcessingException,
          ExecutionException,
          InterruptedException {
    // GIVEN
    String repoName = "Zenodo";
    String expectedResultUrl = "https://zenodo.org/records/18663592";
    String expectedDoiLink = "https://doi.org/10.5281/zenodo.18663592";
    String expectedRaidIdentifier = "https://raid.org/10.12345/ERTY88";
    String expectedRaidUrl = "https://demo.app.raid.org.au/raid/10.12345/ERTY88";
    String originalRaidUrl = "https://demo.static.raid.org.au/raid/10.12345/ERTY88";
    RaIDReferenceDTO expectedRaidReference =
        new RaIDReferenceDTO("serverAlias1", "RaidTitle", expectedRaidIdentifier, originalRaidUrl);
    when(mockRaidServiceClientAdapter.addRaIDRelatedObject(
            anyUser.getUsername(), expectedRaidReference, expectedDoiLink))
        .thenThrow(
            new RaIDDoiNotFoundException(
                "uri not found", new HttpClientErrorException(HttpStatus.BAD_REQUEST)));

    RepoDepositConfig raidDepositConfig = new RepoDepositConfig();
    raidDepositConfig.setExportToRaid(true);
    raidDepositConfig.setAppName(APP_ZENODO);
    raidDepositConfig.setRaidAssociated(
        new RaidGroupAssociationDTO(1L, "RSpaceProjectName", expectedRaidReference));

    RaidUpdateResult expectedRaidUpdateResult =
        new RaidUpdateResult(
            false, repoName, expectedRaidIdentifier, expectedRaidUrl, expectedDoiLink, true);
    Future<RepositoryOperationResult> futureRepoOperationResult =
        CompletableFuture.completedFuture(
            new RepositoryOperationResult(
                true, "", new URL(expectedResultUrl), new URL(expectedDoiLink)));

    // WHEN
    Future<RaidUpdateResult> futureRaidUpdateResult =
        underTest.reportDoiToRaid(
            new App(APP_ZENODO, repoName, true),
            futureRepoOperationResult,
            anyUser,
            raidDepositConfig);

    // THEN
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains("RAiD refused the DOI because it could not find it")),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    RaidUpdateResult actualRaidUpdateResult = futureRaidUpdateResult.get();
    assertEquals(expectedRaidUpdateResult, actualRaidUpdateResult);
  }

  @Test
  void depositReportUsesDoiLinkOnlyWhenThereIsOne() throws Exception {
    RepositoryOperationResult result =
        new RepositoryOperationResult(
            true, "", new URL("https://figshare.com/s/abc"), new URL("https://doi.org/10.1/x"));

    DepositReport report = depositor.buildDepositReport(result, "Figshare", App.APP_FIGSHARE);

    assertEquals(new DepositReport("Figshare", new URL("https://doi.org/10.1/x"), null), report);
  }

  @Test
  void depositReportUsesRepositoryLinkWithoutDoi() throws Exception {
    RepositoryOperationResult result =
        new RepositoryOperationResult(true, "", new URL("https://figshare.com/s/abc"), null);

    DepositReport report = depositor.buildDepositReport(result, "Figshare", App.APP_FIGSHARE);

    assertEquals(
        new DepositReport("Figshare", null, new URL("https://figshare.com/s/abc")), report);
  }

  @Test
  void depositReportIsEmptyWithoutDoiOrRepositoryLink() {
    RepositoryOperationResult result = new RepositoryOperationResult(true, "", null, null);

    assertEquals(
        new DepositReport("Figshare", null, null),
        depositor.buildDepositReport(result, "Figshare", App.APP_FIGSHARE));
  }

  @ParameterizedTest
  @CsvSource({
    "https://datadryad.org/stash/edit/"
        + DRYAD_DOI
        + "/editCode, https://datadryad.org/stash/dataset/"
        + DRYAD_DOI,
    "https://datadryad.org/stash/edit/"
        + DRYAD_DOI
        + ", https://datadryad.org/stash/dataset/"
        + DRYAD_DOI,
    "https://datadryad.org/edit/"
        + DRYAD_DOI
        + "/editCode, https://datadryad.org/dataset/"
        + DRYAD_DOI,
    "https://datadryad.org/edit/" + DRYAD_DOI + ", https://datadryad.org/dataset/" + DRYAD_DOI,
    "http://localhost:9154/edit/"
        + DRYAD_DOI
        + "/editCode, http://localhost:9154/dataset/"
        + DRYAD_DOI,
    "https://www.datadryad.org/stash/edit/"
        + DRYAD_DOI
        + "/editCode?x=1,"
        + " https://www.datadryad.org/stash/dataset/"
        + DRYAD_DOI,
  })
  void dryadEditLinkBecomesLandingPageOnItsOwnHost(String link, String expected) throws Exception {
    assertEquals(
        new URL(expected),
        depositor.buildDepositReport(dryadResult(link), "Dryad", App.APP_DRYAD).accessUrl());
  }

  @ParameterizedTest
  @CsvSource({
    "https://datadryad.org/dataset/" + DRYAD_DOI,
    "https://datadryad.org/stash/dataset/" + DRYAD_DOI,
  })
  void dryadLinkWithoutEditPathIsSentUnchanged(String link) throws Exception {
    assertEquals(
        new URL(link),
        depositor.buildDepositReport(dryadResult(link), "Dryad", App.APP_DRYAD).accessUrl());
  }

  @ParameterizedTest
  @CsvSource({
    "https://datadryad.org/dryad/edit/" + DRYAD_DOI + "/editCode",
    "https://datadryad.org/stash/other/edit/" + DRYAD_DOI + "/editCode",
    "https://datadryad.org/edit/",
    "https://datadryad.org/stash/edit//editCode",
  })
  void unrecognisedDryadEditLinkSendsNothing(String link) throws Exception {
    assertNull(depositor.buildDepositReport(dryadResult(link), "Dryad", App.APP_DRYAD).accessUrl());
  }

  @Test
  void planNotificationFollowsDepositNotificationWithOneLinePerOutcome() throws Exception {
    URL doi = new URL("https://doi.org/10.1/x");
    RepoDepositConfig cfg = configWithPlans(App.APP_DATAVERSE, 1L);
    when(dmpUpdateHandler.report(new DepositReport("Dataverse", doi, null), anyUser, List.of(1L)))
        .thenReturn(
            List.of(
                reported(
                    "Written <plan>", new ValueOutcome(IDENTIFIER, doi.toString(), true, null)),
                reported(
                    "Failed plan", new ValueOutcome(IDENTIFIER, doi.toString(), false, "denied")),
                withStatus("Empty plan", DMPSource.DMP_TOOL, PlanReportStatus.NOTHING_TO_SEND),
                withStatus("Online plan", DMPSource.DMP_ONLINE, PlanReportStatus.NOT_SUPPORTED),
                withStatus("Off plan", DMPSource.DMP_TOOL, PlanReportStatus.PLATFORM_OFF),
                withStatus(
                    "Unconnected plan", DMPSource.DMP_TOOL, PlanReportStatus.NOT_CONNECTED)));

    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", new URL("https://dv.example/ds"), doi),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        cfg);

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    InOrder inOrder = Mockito.inOrder(comm);
    inOrder
        .verify(comm, Mockito.times(2))
        .systemNotify(
            Mockito.eq(NotificationType.PROCESS_COMPLETED),
            messages.capture(),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    assertThat(messages.getAllValues().get(0)).contains("Your deposit to repository Dataverse");
    String planMessage = messages.getAllValues().get(1);
    assertThat(planMessage)
        .contains(
            "Written &lt;plan&gt; (DMPtool)",
            "<a href=\"https://doi.org/10.1/x\">https://doi.org/10.1/x</a> was added to the plan.",
            "<a href=\"https://doi.org/10.1/x\">https://doi.org/10.1/x</a> could not be added:"
                + " denied.",
            "Dataverse returned no DOI and no link, so nothing was added.",
            "Online plan (DMPonline)",
            "RSpace cannot yet update plans on DMPonline.",
            "DMPtool is switched off, so the plan was not updated.",
            "DMPtool is not connected, so the plan was not updated.")
        .doesNotContain("Written <plan>");
  }

  @Test
  void noPlanNotificationWhenNoPlanIsTicked() throws Exception {
    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", new URL("https://dv.example/ds"), null),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        new RepoDepositConfig());

    Mockito.verify(comm)
        .systemNotify(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());
    Mockito.verifyNoInteractions(dmpUpdateHandler);
  }

  @Test
  void noPlanNotificationWhenDepositFailed() throws Exception {
    depositor.postDeposit(
        new RepositoryOperationResult(false, "failed", null, null),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        configWithPlans(App.APP_DATAVERSE, 1L));

    Mockito.verify(comm)
        .systemNotify(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());
    Mockito.verifyNoInteractions(dmpUpdateHandler);
  }

  @Test
  void writeBackErrorDoesNotEscapePostDeposit() throws Exception {
    when(dmpUpdateHandler.report(Mockito.any(), Mockito.eq(anyUser), Mockito.eq(List.of(1L))))
        .thenThrow(new IllegalStateException("boom"));

    assertDoesNotThrow(
        () ->
            depositor.postDeposit(
                new RepositoryOperationResult(true, "done", null, new URL("https://doi.org/1/x")),
                new App(APP_DATAVERSE, "Dataverse", true),
                anyUser,
                null,
                configWithPlans(App.APP_DATAVERSE, 1L)));
    Mockito.verify(comm)
        .systemNotify(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());
  }

  private RepositoryOperationResult dryadResult(String link) throws MalformedURLException {
    return new RepositoryOperationResult(true, "", new URL(link), null);
  }

  private RepoDepositConfig configWithPlans(String appName, Long... planIds) {
    RepoDepositConfig cfg = new RepoDepositConfig();
    cfg.setAppName(appName);
    cfg.setSelectedDMPs(List.of(planIds));
    return cfg;
  }

  private PlanReportResult reported(String title, ValueOutcome outcome) {
    return new PlanReportResult(
        title, "id", DMPSource.DMP_TOOL, PlanReportStatus.REPORTED, List.of(outcome));
  }

  private PlanReportResult withStatus(String title, DMPSource platform, PlanReportStatus status) {
    return PlanReportResult.withStatus(title, "id", platform, status);
  }
}

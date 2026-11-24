package com.researchspace.service.impl;

import static com.researchspace.model.apps.App.APP_DATAVERSE;
import static com.researchspace.model.apps.App.APP_ZENODO;
import static com.researchspace.service.dmp.ReportedValueKind.IDENTIFIER;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.dtos.RaidUpdateResult;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepoDepositMeta;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.LicenseDefs;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.repository.spi.SubmissionMetadata;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.PlanReportStatus;
import com.researchspace.service.dmp.ReportedValueKind;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
public class AsyncDepositorImplTest {
  private static final String DRYAD_DOI = "doi%3A10.5061%2Fdryad.abc123";

  @Mock CommunicationManager comm;
  private @Mock RaIDServiceClientAdapter mockRaidServiceClientAdapter;
  private @Mock DMPUpdateHandler dmpUpdateHandler;
  private @Mock IntegrationsHandler integrationsHandler;
  private @Mock DMPManager dmpManager;
  private @Mock FileStore fileStore;
  private @Mock IRepository repository;
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
  void dryadLandingPageIsSentUnchanged(String link) throws Exception {
    assertEquals(
        new URL(link),
        depositor.buildDepositReport(dryadResult(link), "Dryad", App.APP_DRYAD).accessUrl());
  }

  @ParameterizedTest
  @CsvSource({
    "https://datadryad.org/stash/edit?doi=" + DRYAD_DOI + "&edit_code=secret",
    "https://datadryad.org/dataset/" + DRYAD_DOI + "?edit_code=secret",
    "https://datadryad.org/dataset/" + DRYAD_DOI + "#secret",
    "https://user:secret@datadryad.org/dataset/" + DRYAD_DOI,
    "https://datadryad.org/other/" + DRYAD_DOI,
  })
  void dryadLinkThatIsNotAKnownLandingPageSendsNothing(String link) throws Exception {
    assertNull(depositor.buildDepositReport(dryadResult(link), "Dryad", App.APP_DRYAD).accessUrl());
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
                    "Failed plan", new ValueOutcome(IDENTIFIER, doi.toString(), false, "Denied.")),
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
            "Written &lt;plan&gt; (DMPTool)",
            "<a href=\"https://doi.org/10.1/x\">https://doi.org/10.1/x</a> was added to the plan.",
            "<a href=\"https://doi.org/10.1/x\">https://doi.org/10.1/x</a> could not be added."
                + " Denied. You can add this link to the plan by hand.",
            "Dataverse returned no DOI and no link, so nothing was added.",
            "Online plan (DMPonline)",
            "RSpace cannot yet update plans on DMPonline.",
            "DMPTool is switched off, so the plan was not updated.",
            "DMPTool is not connected, so the plan was not updated.",
            "The DOI link for this deposit is <a href=\"https://doi.org/10.1/x\">"
                + "https://doi.org/10.1/x</a>.",
            "You can open your deposit on Dataverse at <a href=\"https://dv.example/ds\">"
                + "https://dv.example/ds</a>.")
        .doesNotContain("Written <plan>");
  }

  @Test
  void hostileTextIsEscapedInPlanNotification() throws Exception {
    String hostileLink = "https://repo.example/ds?x=\"><script>alert(1)</script>";
    when(dmpUpdateHandler.report(new DepositReport("Repo<b>", null, null), anyUser, List.of(1L)))
        .thenReturn(
            List.of(
                reported(
                    "A plan",
                    new ValueOutcome(
                        ReportedValueKind.ACCESS_URL, hostileLink, false, "<img src=x>."))));

    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", null, null),
        new App(APP_DATAVERSE, "Repo<b>", true),
        anyUser,
        null,
        configWithPlans(App.APP_DATAVERSE, 1L));

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    Mockito.verify(comm, Mockito.times(2))
        .systemNotify(Mockito.any(), messages.capture(), Mockito.any(), Mockito.anyBoolean());
    assertThat(messages.getAllValues().get(1))
        .contains(
            "Repo&lt;b&gt;",
            "&lt;img src=x&gt;.",
            "https://repo.example/ds?x=&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;")
        .doesNotContain("Repo<b>", "<img", "<script>");
  }

  @Test
  void planNotificationHasNoDoiParagraphWithoutDoi() throws Exception {
    URL landing = new URL("https://dv.example/ds");
    when(dmpUpdateHandler.report(
            new DepositReport("Dataverse", null, landing), anyUser, List.of(1L)))
        .thenReturn(
            List.of(
                reported(
                    "A plan",
                    new ValueOutcome(
                        ReportedValueKind.ACCESS_URL, landing.toString(), true, null))));

    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", landing, null),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        configWithPlans(App.APP_DATAVERSE, 1L));

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    Mockito.verify(comm, Mockito.times(2))
        .systemNotify(Mockito.any(), messages.capture(), Mockito.any(), Mockito.anyBoolean());
    assertThat(messages.getAllValues().get(1))
        .contains("was added to the plan.")
        .doesNotContain("The DOI link for this deposit is", "You can open your deposit");
  }

  @Test
  void failedOutcomeWithoutValueRendersNoEmptyLink() throws Exception {
    RepoDepositConfig cfg = configWithPlans(App.APP_DATAVERSE, 1L);
    when(dmpUpdateHandler.report(new DepositReport("Dataverse", null, null), anyUser, List.of(1L)))
        .thenReturn(
            List.of(
                reported(
                    "A plan",
                    new ValueOutcome(ReportedValueKind.ACCESS_URL, null, false, "It failed."))));

    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", null, null),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        cfg);

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    Mockito.verify(comm, Mockito.times(2))
        .systemNotify(Mockito.any(), messages.capture(), Mockito.any(), Mockito.anyBoolean());
    assertThat(messages.getAllValues().get(1))
        .contains("The plan could not be updated. It failed.")
        .doesNotContain("href=\"\"", "by hand");
  }

  @Test
  void everyPlanIsListedHoweverManyThereAre() throws Exception {
    List<PlanReportResult> results = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      results.add(
          reported(
              StringUtils.leftPad("plan " + i, 100, 'x'),
              new ValueOutcome(IDENTIFIER, "https://doi.org/10.1/x", true, null)));
    }

    String planMessage = planNotificationFor(results);

    for (int i = 0; i < 12; i++) {
      assertThat(planMessage).contains("xplan " + i + " (DMPTool)");
    }
  }

  private String planNotificationFor(List<PlanReportResult> results) throws Exception {
    URL doi = new URL("https://doi.org/10.1/x");
    when(dmpUpdateHandler.report(new DepositReport("Dataverse", doi, null), anyUser, List.of(1L)))
        .thenReturn(results);

    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", null, doi),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        configWithPlans(App.APP_DATAVERSE, 1L));

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    Mockito.verify(comm, Mockito.times(2))
        .systemNotify(Mockito.any(), messages.capture(), Mockito.any(), Mockito.anyBoolean());
    return messages.getAllValues().get(1);
  }

  @Test
  void noPlanNotificationWhenNoTickedPlanIsReported() throws Exception {
    when(dmpUpdateHandler.report(Mockito.any(), Mockito.eq(anyUser), Mockito.eq(List.of(1L))))
        .thenReturn(List.of());

    depositor.postDeposit(
        new RepositoryOperationResult(true, "done", null, new URL("https://doi.org/1/x")),
        new App(APP_DATAVERSE, "Dataverse", true),
        anyUser,
        null,
        configWithPlans(App.APP_DATAVERSE, 1L));

    Mockito.verify(comm)
        .systemNotify(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());
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
  void writeBackErrorIsReportedToTheUserAndDoesNotEscapePostDeposit() throws Exception {
    when(dmpUpdateHandler.report(Mockito.any(), Mockito.eq(anyUser), Mockito.eq(List.of(1L))))
        .thenThrow(new IllegalStateException("boom"));

    assertDoesNotThrow(
        () ->
            depositor.postDeposit(
                new RepositoryOperationResult(true, "done", null, new URL("https://doi.org/1/x")),
                new App(APP_DATAVERSE, "Data<verse>", true),
                anyUser,
                null,
                configWithPlans(App.APP_DATAVERSE, 1L)));

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    Mockito.verify(comm, Mockito.times(2))
        .systemNotify(Mockito.any(), messages.capture(), Mockito.any(), Mockito.anyBoolean());
    assertEquals(
        "RSpace could not update the plans you selected after your deposit on"
            + " \"Data&lt;verse&gt;\". You can add the deposit to them by hand.",
        messages.getAllValues().get(1).trim());
  }

  @Test
  void failureToSendTheWriteBackErrorDoesNotEscapePostDeposit() throws Exception {
    when(dmpUpdateHandler.report(Mockito.any(), Mockito.eq(anyUser), Mockito.eq(List.of(1L))))
        .thenThrow(new IllegalStateException("boom"));
    Mockito.doReturn(null)
        .doThrow(new IllegalStateException("no notification"))
        .when(comm)
        .systemNotify(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyBoolean());

    assertDoesNotThrow(
        () ->
            depositor.postDeposit(
                new RepositoryOperationResult(true, "done", null, new URL("https://doi.org/1/x")),
                new App(APP_DATAVERSE, "Dataverse", true),
                anyUser,
                null,
                configWithPlans(App.APP_DATAVERSE, 1L)));
  }

  @Test
  void twoTickedPlansOnDataverseAreDepositedWithoutPlanDoiAndBothAreReported() throws Exception {
    givenDepositorWithPlans(plan(1L), plan(2L));
    givenDepositSucceeds();
    RepoDepositConfig cfg = depositConfigWithPlans(App.APP_DATAVERSE, 1L, 2L);

    RepositoryOperationResult result =
        depositor
            .depositArchive(
                new App(APP_DATAVERSE, "Dataverse", true),
                anyUser,
                repository,
                cfg,
                null,
                CompletableFuture.completedFuture(new ArchiveResult()))
            .get();

    assertTrue(result.isSucceeded());
    ArgumentCaptor<SubmissionMetadata> metadata = ArgumentCaptor.forClass(SubmissionMetadata.class);
    Mockito.verify(repository)
        .submitDeposit(Mockito.any(), Mockito.any(), metadata.capture(), Mockito.any());
    assertTrue(metadata.getValue().getDmpDoi().isEmpty());
    Mockito.verify(dmpUpdateHandler)
        .report(Mockito.any(), Mockito.eq(anyUser), Mockito.eq(List.of(1L, 2L)));
  }

  @Test
  void twoTickedPlansOnZenodoStillFailTheDeposit() throws Exception {
    givenDepositorWithPlans(plan(1L), plan(2L));

    RepositoryOperationResult result =
        depositor
            .depositArchive(
                new App(APP_ZENODO, "Zenodo", true),
                anyUser,
                repository,
                depositConfigWithPlans(APP_ZENODO, 1L, 2L),
                null,
                CompletableFuture.completedFuture(new ArchiveResult()))
            .get();

    assertFalse(result.isSucceeded());
    Mockito.verifyNoInteractions(repository);
  }

  @Test
  void oneTickedPlanSetsPlanDoiOnDepositMetadata() throws Exception {
    DMPUser plan = plan(1L);
    givenDepositorWithPlans(plan);
    File dmpJson = File.createTempFile("dmp", ".json");
    Files.writeString(
        dmpJson.toPath(),
        "{\"items\":[{\"dmp\":{\"dmp_id\":{\"identifier\":\"https://doi.org/10.48321/D1X\"}}}]}");
    when(fileStore.findFile(Mockito.any())).thenReturn(dmpJson);
    givenDepositSucceeds();

    depositor
        .depositArchive(
            new App(APP_DATAVERSE, "Dataverse", true),
            anyUser,
            repository,
            depositConfigWithPlans(App.APP_DATAVERSE, 1L),
            null,
            CompletableFuture.completedFuture(new ArchiveResult()))
        .get();

    ArgumentCaptor<SubmissionMetadata> metadata = ArgumentCaptor.forClass(SubmissionMetadata.class);
    Mockito.verify(repository)
        .submitDeposit(Mockito.any(), Mockito.any(), metadata.capture(), Mockito.any());
    assertEquals(Optional.of("https://doi.org/10.48321/D1X"), metadata.getValue().getDmpDoi());
  }

  private void givenDepositorWithPlans(DMPUser... plans) {
    IntegrationInfo on = new IntegrationInfo();
    on.setAvailable(true);
    on.setEnabled(true);
    IntegrationInfo off = new IntegrationInfo();
    when(integrationsHandler.getIntegration(anyUser, IntegrationsHandler.ARGOS_APP_NAME))
        .thenReturn(off);
    when(integrationsHandler.getIntegration(anyUser, IntegrationsHandler.DMPONLINE_APP_NAME))
        .thenReturn(off);
    when(integrationsHandler.getIntegration(anyUser, IntegrationsHandler.DMPTOOL_APP_NAME))
        .thenReturn(on);
    when(dmpManager.findDMPsForUser(anyUser)).thenReturn(List.of(plans));
    ReflectionTestUtils.setField(depositor, "integrationsHandler", integrationsHandler);
    ReflectionTestUtils.setField(depositor, "dmpManager", dmpManager);
    ReflectionTestUtils.setField(depositor, "fileStore", fileStore);
  }

  private void givenDepositSucceeds() throws Exception {
    when(repository.submitDeposit(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(new RepositoryOperationResult(true, "done", null, null));
  }

  private DMPUser plan(long id) {
    DMPUser plan =
        new DMPUser(anyUser, new DmpDto("id" + id, "title" + id, DMPSource.DMP_TOOL, null, null));
    ReflectionTestUtils.setField(plan, "id", id);
    plan.setDmpDownloadFile(new EcatDocumentFile());
    return plan;
  }

  private RepoDepositConfig depositConfigWithPlans(String appName, Long... planIds) {
    RepoDepositConfig cfg = configWithPlans(appName, planIds);
    RepoDepositMeta meta = new RepoDepositMeta();
    meta.setAuthors(List.of());
    meta.setContacts(List.of());
    meta.setTags(List.of());
    meta.setLicenseUrl(LicenseDefs.NO_LICENSE_URL);
    cfg.setMeta(meta);
    return cfg;
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

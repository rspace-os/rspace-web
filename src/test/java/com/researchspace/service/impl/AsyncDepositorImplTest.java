package com.researchspace.service.impl;

import static com.researchspace.model.apps.App.APP_DATAVERSE;
import static com.researchspace.model.apps.App.APP_ZENODO;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.RaidGroupAssociationDTO;
import com.researchspace.model.dtos.RaidUpdateResult;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.testutils.SystemPropertyTestFactory;
import com.researchspace.testutils.VelocityTestUtils;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.scheduling.annotation.AsyncResult;

public class AsyncDepositorImplTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  @Mock CommunicationManager comm;
  private @Mock RaIDServiceClientAdapter mockRaidServiceClientAdapter;

  VelocityEngine engine;
  AsyncDepositorImplTSS underTest;
  User anyUser;
  File testFile;

  class AsyncDepositorImplTSS extends AsyncDepositorImpl {

    boolean updateDMPsCalled = false;

    void updateDMPS(
        RepositoryOperationResult result, User subject, RepoDepositConfig repoDepositConfig) {
      this.updateDMPsCalled = true;
    }
  }

  @Before
  public void setUp() throws Exception {
    underTest = new AsyncDepositorImplTSS();
    engine =
        VelocityTestUtils.setupVelocity(
            "src/main/resources/velocityTemplates/messageAndNotificationEmails");
    underTest.setVelocity(engine);
    underTest.setCommMgr(comm);
    underTest.setRaIDServiceClientAdapter(mockRaidServiceClientAdapter);
    anyUser = createAnyUser("any");
    testFile = File.createTempFile("test", ".txt");
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
    when(mockRaidServiceClientAdapter.updateRaIDRelatedObject(
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
        new AsyncResult<>(
            new RepositoryOperationResult(true, "", new URL(expectedResultUrl), null));

    // WHEN
    Future<RaidUpdateResult> futureRaidUpdateResult =
        underTest.reportDoiToRaid(
            new App(APP_DATAVERSE, repoName, true),
            futureRepoOperationResult,
            anyUser,
            raidDepositConfig);

    // THEN
    Mockito.verify(mockRaidServiceClientAdapter)
        .updateRaIDRelatedObject(anyUser.getUsername(), expectedRaidReference, expectedDoiLink);
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
    when(mockRaidServiceClientAdapter.updateRaIDRelatedObject(
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
        new AsyncResult<>(
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
        .updateRaIDRelatedObject(anyUser.getUsername(), expectedRaidReference, expectedDoiLink);
    Mockito.verify(comm)
        .systemNotify(
            Mockito.any(NotificationType.class),
            (Mockito.contains(expectedDoiLink)),
            Mockito.eq(anyUser.getUsername()),
            Mockito.eq(true));
    RaidUpdateResult actualRaidUpdateResult = futureRaidUpdateResult.get();
    assertEquals(expectedRaidUpdateResult, actualRaidUpdateResult);
  }
}

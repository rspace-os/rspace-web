package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.repository.RepoUIConfigInfo;
import com.researchspace.model.system.SystemPropertyTestFactory;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.repository.spi.RepositoryConfigurer;
import com.researchspace.service.impl.RepositoryDepositHandlerImpl;
import com.researchspace.webapp.controller.repositories.RSpaceRepoConnectionConfig;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RepositoryDepositHandlerTest {
  private static final String DATAVERSE_REPOSITORY = "dataverseRepository";
  private static final String FIGSHARE_REPOSITORY = "figshareRepository";
  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  private @Mock UserManager userManager;
  private @Mock RepositoryFactory repoFactory;

  private @Mock UserConnectionManager connectionRepo;
  private @Mock IntegrationsHandler integrationsHandler;
  private @Mock IRepository aRepo;
  private @Mock RepositoryConfigurer repoConfigurer;
  private @Mock IRepositoryConfigFactory repositoryConfigFactory;

  static class RepositoryDepositHandlerImplTSS extends RepositoryDepositHandlerImpl {
    boolean connectionStateChecked;

    @Override
    protected void checkConnectionState(App app, User subject) {
      this.connectionStateChecked = true;
    }
  }

  static class RepositoryDepositHandlerImplUnconnectedHandlerTSS
      extends RepositoryDepositHandlerImpl {
    boolean connectionStateChecked;

    @Override
    protected void checkConnectionState(App app, User subject) {
      this.connectionStateChecked = true;
      throw new AppNotAuthorisedException("Fail");
    }
  }

  @InjectMocks RepositoryDepositHandlerImplTSS handler;

  @InjectMocks RepositoryDepositHandlerImplUnconnectedHandlerTSS unconnectedHandlerTSS;
  private User anyUser;

  @Before
  public void setUp() throws Exception {

    handler.setRepoFactory(repoFactory);
    unconnectedHandlerTSS.setRepoFactory(repoFactory);
    anyUser = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void getConfigInfoHappyCase() throws MalformedURLException {
    UserAppConfig appCfg =
        SystemPropertyTestFactory.createAnyAppWithConfigElements(anyUser, App.APP_FIGSHARE);
    mockGetSessionUser();
    setUpRepoExpectations(FIGSHARE_REPOSITORY);
    //		Mockito.when(appCfg.getAppConfigElementSets());
    RepoUIConfigInfo returnedInfo =
        handler.getDataverseRepoUIConfigInfo(
            appCfg.getAppConfigElementSets().iterator().next(), anyUser);
    assertEquals(App.APP_FIGSHARE, returnedInfo.getRepoName());
    verifyConfigurerCalledOK(FIGSHARE_REPOSITORY);
  }

  private void mockGetSessionUser() {
    when(userManager.getAuthenticatedUserInSession()).thenReturn(anyUser);
  }

  private void verifyConfigurerCalledOK(String repoName) {
    verify(repoFactory.getRepository(repoName)).getConfigurer();
  }

  private void setUpRepoExpectations(String repoName) throws MalformedURLException {
    when(repoFactory.getRepository(repoName)).thenReturn(aRepo);
    when(aRepo.getConfigurer()).thenReturn(repoConfigurer);
    when(repositoryConfigFactory.createRepositoryConfigFromAppCfg(
            Mockito.any(RSpaceRepoConnectionConfig.class), Mockito.any(User.class)))
        .thenReturn(new RepositoryConfig(new URL("http://somewhere.com"), "id", "pwd", ""));
  }

  @Test
  public void getSubjectsThrowsAppNotAuthExceptionIfNoAuthConnections() throws Exception {
    UserAppConfig appCfg =
        SystemPropertyTestFactory.createAnyAppWithConfigElements(anyUser, App.APP_FIGSHARE);
    App app = appCfg.getApp();
    mockGetSessionUser();
    CoreTestUtils.assertExceptionThrown(
        () ->
            unconnectedHandlerTSS.getDataverseRepoUIConfigInfo(
                appCfg.getAppConfigElementSets().iterator().next(), anyUser),
        AppNotAuthorisedException.class);
  }

  @Test
  public void getSubjectsDoesntThrowExceptionIfAppNotRequiresAuth() throws MalformedURLException {
    UserAppConfig appCfg =
        SystemPropertyTestFactory.createAnyAppWithConfigElements(anyUser, App.APP_DATAVERSE);
    App app = appCfg.getApp();
    mockGetSessionUser();
    setUpRepoExpectations(DATAVERSE_REPOSITORY);
    handler.getDataverseRepoUIConfigInfo(
        appCfg.getAppConfigElementSets().iterator().next(), anyUser);
    verifyConfigurerCalledOK(DATAVERSE_REPOSITORY);
  }

  private IntegrationInfo getConnectionList() {
    IntegrationInfo rc = new IntegrationInfo();
    rc.setName("TESTAPP");
    return rc;
  }
}

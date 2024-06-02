package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.repository.RepoUIConfigInfo;
import com.researchspace.repository.spi.properties.RepoProperty;
import com.researchspace.repository.spi.properties.StringRepoProperty;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.RepositoryDepositHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserManager;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepositoryConfigurationControllerTest {

  @Mock private RepositoryDepositHandler repositoryDepositHandler;
  @Mock private UserAppConfigManager appCfgMgr;
  @Mock private IntegrationsHandler integrationsHandler;
  @Mock private UserManager userManager;
  @Mock private DMPManager dmpManager;

  @InjectMocks private RepositoryConfigurationController repositoryConfigurationController;

  User exporter = null;

  @BeforeEach
  public void setUp() throws Exception {
    exporter = TestFactory.createAnyUser("anyUser");
    when(userManager.getAuthenticatedUserInSession()).thenReturn(exporter);
  }

  @Test
  void getOtherProperties() throws Exception {

    RepoProperty repoProperty = new StringRepoProperty("test", false, "testValue");
    RepoUIConfigInfo info = new RepoUIConfigInfo("A repo", null, null, toList(repoProperty));

    mockRepoConfigInfo(info);
    UserAppConfig appCfg = createValidDataverseApp();
    when(appCfgMgr.getByAppName("app.dataverse", exporter)).thenReturn(appCfg);
    IntegrationInfo datverseIntegrationInfo = createEnabledAvailableInfo(DATAVERSE_APP_NAME);
    addDataverseConfig(datverseIntegrationInfo);
    when(integrationsHandler.getIntegration(eq(exporter), eq(DATAVERSE_APP_NAME)))
        .thenReturn(datverseIntegrationInfo);

    List<RepoUIConfigInfo> activeRepos =
        repositoryConfigurationController.getAllActiveRepositories();
    assertEquals(1, activeRepos.size());
    assertEquals("test", activeRepos.get(0).getOtherProperties().get(0).getName());
  }

  // this test mocks out calls to underlying repositories and integrations
  @Test
  void getAllActiveReposDetectsAppConfigs() throws Exception {

    RepoProperty repoProperty = new StringRepoProperty("test", false, "testValue");
    RepoUIConfigInfo uiCfgInfo = new RepoUIConfigInfo("A repo", null, null, toList(repoProperty));

    Map<String, Object> options = new HashMap<>();
    options.put("op", "value");
    uiCfgInfo.setOptions(options);
    mockRepoConfigInfo(uiCfgInfo);
    setupApp();

    // this is inactive for dataverse - no items configured
    IntegrationInfo datverseIntegrationInfo = createEnabledAvailableInfo(DATAVERSE_APP_NAME);
    // initially inactive for figshare - not oauthconnec

    when(integrationsHandler.getIntegration(exporter, DATAVERSE_APP_NAME))
        .thenReturn(datverseIntegrationInfo);
    List<RepoUIConfigInfo> activeRepos =
        repositoryConfigurationController.getAllActiveRepositories();
    assertEquals(0, activeRepos.size());

    addDataverseConfig(datverseIntegrationInfo);
    activeRepos = repositoryConfigurationController.getAllActiveRepositories();
    assertEquals(1, activeRepos.size());
  }

  private void setupApp() {
    UserAppConfig appCfg = createValidDataverseApp();
    when(appCfgMgr.getByAppName("app.dataverse", exporter)).thenReturn(appCfg);
  }

  private UserAppConfig createValidDataverseApp() {
    App app = new App("app.dataverse", "dataverse", true);
    UserAppConfig appCfg = new UserAppConfig(exporter, app, true);
    AppConfigElementSet s = new AppConfigElementSet();
    s.addConfigElement(new AppConfigElement(new AppConfigElementDescriptor()));
    appCfg.addConfigSet(s);
    return appCfg;
  }

  @Test
  void getAllActiveReposDetectsOAuthConnected() throws Exception {
    App app = new App("app.figshare", "figshare", true);
    UserAppConfig appCfg = new UserAppConfig(exporter, app, true);
    // initially inactive for figshare - not oauthconnec
    IntegrationInfo figshareIntegrationInfo = createEnabledAvailableInfo(FIGSHARE_APP_NAME);
    lenient()
        .when(integrationsHandler.getIntegration(exporter, FIGSHARE_APP_NAME))
        .thenReturn(figshareIntegrationInfo);
    mockUICfgInfo();
    assertEquals(1, repositoryConfigurationController.getAllActiveRepositories().size());
    figshareIntegrationInfo.setOauthConnected(false);
    assertEquals(0, repositoryConfigurationController.getAllActiveRepositories().size());
  }

  private void mockUICfgInfo() throws MalformedURLException {
    RepoUIConfigInfo uiCfgInfo =
        new RepoUIConfigInfo("A repo", null, null, Collections.emptyList());
    mockRepoConfigInfo(uiCfgInfo);
  }

  private void mockRepoConfigInfo(RepoUIConfigInfo mockResult) throws MalformedURLException {
    lenient()
        .when(
            repositoryDepositHandler.getDataverseRepoUIConfigInfo(
                any(AppConfigElementSet.class), any(User.class)))
        .thenReturn(mockResult);
    lenient()
        .when(repositoryDepositHandler.getFigshareRepoUIConfigInfo(any(User.class)))
        .thenReturn(mockResult);
  }

  private void addDataverseConfig(IntegrationInfo datverseIntegrationInfo) {
    // now set to be active (has >1 config set)
    Map<String, Object> config = new HashMap<>();
    config.put("1", "someValue");
    datverseIntegrationInfo.setOptions(config);
  }

  @Test
  void getDMPs() throws MalformedURLException {
    IntegrationInfo dfinf = createEnabledAvailableInfo(DATAVERSE_APP_NAME);
    // Dataverse has to have options otherwise it's filtered out as unconfigured
    Map<String, Object> options = new HashMap<>();
    options.put("op", "value");
    dfinf.setOptions(options);
    IntegrationInfo dmpInf = createEnabledAvailableInfo(DMPTOOL_APP_NAME);
    when(integrationsHandler.getIntegration(eq(exporter), anyString()))
        .thenReturn(dfinf, new IntegrationInfo(), new IntegrationInfo(), dmpInf);
    when(dmpManager.findDMPsForUser(exporter))
        .thenReturn(List.of(new DMPUser(exporter, new DMP("id", "title"))));
    setupApp();
    mockUICfgInfo();
    List<RepoUIConfigInfo> activeRepos =
        repositoryConfigurationController.getAllActiveRepositories();
    assertEquals(1, activeRepos.size());
    assertEquals(1, activeRepos.get(0).getLinkedDMPs().size());
    assertEquals("title", activeRepos.get(0).getLinkedDMPs().get(0).getDmpTitle());
  }

  private IntegrationInfo createEnabledAvailableInfo(String name) {
    IntegrationInfo integrationInfo = new IntegrationInfo();
    integrationInfo.setAvailable(true);
    integrationInfo.setEnabled(true);
    integrationInfo.setName(name);
    integrationInfo.setOauthConnected(true);
    integrationInfo.setDisplayName("Display name");
    return integrationInfo;
  }
}

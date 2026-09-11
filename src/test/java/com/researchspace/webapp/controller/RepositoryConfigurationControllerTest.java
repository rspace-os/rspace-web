package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.repository.RepoUIConfigInfo;
import com.researchspace.repository.spi.properties.RepoProperty;
import com.researchspace.repository.spi.properties.StringRepoProperty;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.RepositoryDepositHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
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
    assertThat(activeRepos).hasSize(1);
    assertEquals("test", activeRepos.get(0).getOtherProperties().get(0).getName());
  }

  @Test
  void getDataverseOptions() throws Exception {

    RepoUIConfigInfo info = new RepoUIConfigInfo("A repo", null, null, null);
    mockRepoConfigInfo(info);
    UserAppConfig appCfg = createValidDataverseApp();
    when(appCfgMgr.getByAppName("app.dataverse", exporter)).thenReturn(appCfg);
    IntegrationInfo datverseIntegrationInfo = createEnabledAvailableInfo(DATAVERSE_APP_NAME);
    addDataverseConfig(datverseIntegrationInfo);
    when(integrationsHandler.getIntegration(eq(exporter), eq(DATAVERSE_APP_NAME)))
        .thenReturn(datverseIntegrationInfo);

    List<RepoUIConfigInfo> activeRepos =
        repositoryConfigurationController.getAllActiveRepositories();
    assertThat(activeRepos).hasSize(1);
    assertThat(activeRepos.get(0).getOptions()).hasSize(1);
    Map<String, Object> dataverseUIConfigOptions = activeRepos.get(0).getOptions();
    Map<String, Object> configOptionsMap = (Map<String, Object>) dataverseUIConfigOptions.get("1");
    assertThat(configOptionsMap).hasSize(1);
    assertNull(configOptionsMap.get("metadataLanguages"));

    repositoryConfigurationController.setMetadataLanguagesMap(
        "[{\"title\": \"English\", \"locale\": \"en\"}, {\"title\": \"Hungarian\", \"locale\":"
            + " \"hu\"}]");
    activeRepos = repositoryConfigurationController.getAllActiveRepositories();
    assertThat(activeRepos).hasSize(1);
    assertThat(activeRepos.get(0).getOptions()).hasSize(1);
    dataverseUIConfigOptions = activeRepos.get(0).getOptions();
    configOptionsMap = (Map<String, Object>) dataverseUIConfigOptions.get("1");
    assertThat(configOptionsMap).hasSize(2);
    assertThat(configOptionsMap)
        .containsEntry(
            "metadataLanguages",
            List.of(
                Map.of("title", "English", "locale", "en"),
                Map.of("title", "Hungarian", "locale", "hu")));
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
    assertThat(activeRepos).isEmpty();

    addDataverseConfig(datverseIntegrationInfo);
    activeRepos = repositoryConfigurationController.getAllActiveRepositories();
    assertThat(activeRepos).hasSize(1);
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
    // initially inactive for figshare - not oauthconnec
    IntegrationInfo figshareIntegrationInfo = createEnabledAvailableInfo(FIGSHARE_APP_NAME);
    when(integrationsHandler.getIntegration(eq(exporter), anyString()))
        .thenAnswer(
            invocation ->
                FIGSHARE_APP_NAME.equals(invocation.getArgument(1))
                    ? figshareIntegrationInfo
                    : null);
    RepoUIConfigInfo uiCfgInfo =
        new RepoUIConfigInfo("A repo", null, null, Collections.emptyList());
    when(repositoryDepositHandler.getFigshareRepoUIConfigInfo(any(User.class)))
        .thenReturn(uiCfgInfo);
    assertThat(repositoryConfigurationController.getAllActiveRepositories()).hasSize(1);
    figshareIntegrationInfo.setOauthConnected(false);
    assertThat(repositoryConfigurationController.getAllActiveRepositories()).isEmpty();
  }

  private void mockUICfgInfo() throws MalformedURLException {
    RepoUIConfigInfo uiCfgInfo =
        new RepoUIConfigInfo("A repo", null, null, Collections.emptyList());
    mockRepoConfigInfo(uiCfgInfo);
  }

  private void mockRepoConfigInfo(RepoUIConfigInfo mockResult) throws MalformedURLException {
    when(repositoryDepositHandler.getDataverseRepoUIConfigInfo(
            any(AppConfigElementSet.class), any(User.class)))
        .thenReturn(mockResult);
  }

  private void addDataverseConfig(IntegrationInfo datverseIntegrationInfo) {
    // now set to be active (has >1 config set)
    Map<String, Object> config = new HashMap<>();
    config.put("1", new HashMap<>(Map.of("someKey", "someValue")));
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
        .thenReturn(List.of(new DMPUser(exporter, new DmpDto("id", "title"))));
    setupApp();
    mockUICfgInfo();
    List<RepoUIConfigInfo> activeRepos =
        repositoryConfigurationController.getAllActiveRepositories();
    assertThat(activeRepos).hasSize(1);
    assertThat(activeRepos.get(0).getLinkedDMPs()).hasSize(1);
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

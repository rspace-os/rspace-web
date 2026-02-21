package com.researchspace.webapp.integrations.dsw;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementDescriptor;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.preference.SettingsType;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.integrations.dsw.model.DSWProject;
import com.researchspace.webapp.integrations.dsw.model.DSWUser;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

@RunWith(ConditionalTestRunner.class)
public class DSWControllerTest extends MVCTestBase {

  private static String DSW_SERVER_ALIAS = "This string is bypassed by unit tests";
  private static String TEST_PROJECT_NAME = "RSpace Nightly Test Project";
  private static String TEST_PROJECT_DESCRIPTION = "Project for confirming endpoint functionality";

  @Autowired protected SystemPropertyManager sysPropMgr;

  private DSWClient dswClient;

  private DSWController dswController;

  UserManager userManager;
  UserAppConfigManager userAppConfigMgr;
  UserConnectionManager source;
  MediaManager mediaManager;
  DMPManager dmpManager;

  private ObjectMapper mapper = new ObjectMapper();

  private User u;

  @Value("${dsw.realConnectionTest.url}")
  private String url;

  @Value("${dsw.realConnectionTest.apikey}")
  private String apiKey;

  @Before
  public void setup() throws Exception {
    userManager = Mockito.mock(UserManager.class);
    userAppConfigMgr = Mockito.mock(UserAppConfigManager.class);
    source = Mockito.mock(UserConnectionManager.class);
    mediaManager = Mockito.mock(MediaManager.class);
    dmpManager = Mockito.mock(DMPManager.class);

    dswClient = new DSWClient(source, userManager, mediaManager, dmpManager);
    dswController = new DSWController(dswClient, userManager, userAppConfigMgr);

    u = new User();
    u.setId(1701l);
    u.setUsername("Test user");

    UserAppConfig uacfg = new UserAppConfig(u, null, true);

    PropertyDescriptor descAlias = new PropertyDescriptor("DSW_ALIAS", SettingsType.STRING, "");
    PropertyDescriptor descUrl = new PropertyDescriptor("DSW_URL", SettingsType.STRING, "");

    AppConfigElementDescriptor acedAlias = new AppConfigElementDescriptor(descAlias);
    AppConfigElementDescriptor acedUrl = new AppConfigElementDescriptor(descUrl);

    AppConfigElement aceAlias = new AppConfigElement(acedAlias, "Actual DSW URL");
    AppConfigElement aceUrl = new AppConfigElement(acedUrl, url);

    AppConfigElementSet aces = new AppConfigElementSet();
    aces.addConfigElement(aceAlias);
    aces.addConfigElement(aceUrl);
    aces.setUserAppConfig(uacfg);

    uacfg.getAppConfigElementSets().add(aces);

    UserConnection connection = new UserConnection();
    connection.setAccessToken(apiKey);

    when(userManager.get(anyLong())).thenReturn(u);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(u);
    when(userAppConfigMgr.getByAppName("app.dsw", u)).thenReturn(uacfg);
    when(userAppConfigMgr.findByAppConfigElementSetId(anyLong())).thenReturn(Optional.of(aces));
    when(userAppConfigMgr.findByAppConfigElementSetId(null)).thenReturn(Optional.of(aces));
    when(source.findByUserNameProviderName(anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(connection));
    when(mediaManager.saveNewDMP(anyString(), any(), any(), any()))
        .thenReturn(new EcatDocumentFile());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testCurrentUserDetails() {
    try {
      ResponseEntity usersResponse = dswController.currentUsers(DSW_SERVER_ALIAS);
      assertNotNull(usersResponse);
      DSWUser dswUser = mapper.readValue(usersResponse.getBody().toString(), DSWUser.class);
      assertNotNull(dswUser.getLastName());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testPlans() {
    try {
      AjaxReturnObject plansResponse = dswController.listDSWPlans(DSW_SERVER_ALIAS);
      assertNotNull(plansResponse);
      DSWProject[] projects =
          mapper.readValue(((JsonNode) plansResponse.getData()).toString(), DSWProject[].class);
      assertTrue(projects.length > 1);
      List<String> projectNames =
          Arrays.stream(projects).map(DSWProject::getName).collect(Collectors.toList());
      assertTrue(projectNames.contains(TEST_PROJECT_NAME));
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testImportPlan() {
    try {
      AjaxReturnObject plansResponse = dswController.listDSWPlans(DSW_SERVER_ALIAS);
      assertNotNull(plansResponse);
      DSWProject[] projects =
          mapper.readValue(((JsonNode) plansResponse.getData()).toString(), DSWProject[].class);
      assertTrue(projects.length > 0);

      DSWProject projectForRetrieval =
          Arrays.stream(projects)
              .filter(p -> p.getName().equals(TEST_PROJECT_NAME))
              .collect(Collectors.toList())
              .get(0);

      assertNotNull(projectForRetrieval);
      assertNotNull(projectForRetrieval.getUuid());
      assertEquals(TEST_PROJECT_DESCRIPTION, projectForRetrieval.getDescription());

      AjaxReturnObject<JsonNode> project =
          dswController.importPlan(DSW_SERVER_ALIAS, projectForRetrieval.getUuid());

      assertNotNull(project);
      assertNotNull(project.getData());
      assertEquals(TEST_PROJECT_NAME, project.getData().get("name").asText());
      assertEquals(projectForRetrieval.getUuid(), project.getData().get("uuid").asText());

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}

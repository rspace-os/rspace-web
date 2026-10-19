package com.researchspace.webapp.controller;

import static com.researchspace.service.SystemPropertyName.IGSN_DATACITE_PASSWORD;
import static com.researchspace.service.SystemPropertyName.IGSN_DATACITE_USERNAME;
import static com.researchspace.service.SystemPropertyName.PIDINST_B2INST_TOKEN;
import static com.researchspace.service.SystemPropertyName.PIDINST_DATACITE_PASSWORD;
import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyName;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

public class SystemAndDeploymentPropsControllerMVCIT extends MVCTestBase {

  // shared with CommunityAdminControllerMVCIT
  static final List<String> FREE_TEXT_SETTINGS =
      List.of(
          IGSN_DATACITE_USERNAME.getPropertyName(), // free text, so default-deny too
          IGSN_DATACITE_PASSWORD.getPropertyName(),
          PIDINST_DATACITE_PASSWORD.getPropertyName(),
          PIDINST_B2INST_TOKEN.getPropertyName());

  @Value("${egnyte.client.id}")
  private String egnyteClientId;

  @Autowired private DeploymentPropertiesController deploymentPropertiesController;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    logout();
    super.tearDown();
  }

  @Test
  public void testGetPropertyValues() throws Exception {
    logoutAndLoginAsCommunityAdmin(); // can be anyone,
    MvcResult res = mockMvc.perform(get("/deploymentproperties/ajax/properties")).andReturn();
    Map<String, ?> data = getFromJsonResponseBody(res, Map.class);
    final int MIN_PROPERTY_COUNT = 7; // from rspac861
    assertThat(data.keySet().size()).isGreaterThanOrEqualTo(MIN_PROPERTY_COUNT);
    // assert properties are merged from DB...
    assertNotNull(data.get(SystemPropertyName.DROPBOX_AVAILABLE.getPropertyName()));
    // .. and property files
    assertNotNull(data.get("baseURL"));
    assertThat(data).containsKeys("sysadmin.delete.user", "deployment.cloud");
  }

  @Test
  public void testGetPropertyValue() throws Exception {
    logoutAndLoginAsCommunityAdmin(); // can be anyone,
    MvcResult res =
        mockMvc
            .perform(
                get("/deploymentproperties/ajax/property")
                    .param("name", SystemPropertyName.DROPBOX_AVAILABLE.getPropertyName()))
            .andReturn();
    String result = res.getResponse().getContentAsString();
    HierarchicalPermission.valueOf(result);
    assertFalse(isEmpty(result));

    res =
        mockMvc
            .perform(get("/deploymentproperties/ajax/property").param("name", "egnyte.client.id"))
            .andReturn();
    result = res.getResponse().getContentAsString();
    assertEquals(egnyteClientId, result);

    mockMvc
        .perform(get("/deploymentproperties/ajax/property").param("name", "box.client.id"))
        .andExpect(status().isOk());
  }

  @Test
  public void updatePropertyRequiresSysadmin() throws Exception {
    SystemPropertyName name = SystemPropertyName.CHEMISTRY_AVAILABLE;
    String current = sysPropMgr.findByName(name).getValue();
    String other =
        HierarchicalPermission.ALLOWED.name().equals(current)
            ? HierarchicalPermission.DENIED.name()
            : HierarchicalPermission.ALLOWED.name();
    try {
      logoutAndLoginAsCommunityAdmin(); // ROLE_ADMIN, not ROLE_SYSADMIN
      MvcResult denied =
          mockMvc
              .perform(
                  post("/deploymentproperties/ajax/updateProperty")
                      .param("propertyName", name.getPropertyName())
                      .param("newValue", other))
              .andReturn();
      assertInstanceOf(AuthorizationException.class, denied.getResolvedException());
      assertEquals(current, sysPropMgr.findByName(name).getValue());

      logoutAndLoginAsSysAdmin();
      MvcResult allowed =
          mockMvc
              .perform(
                  post("/deploymentproperties/ajax/updateProperty")
                      .param("propertyName", name.getPropertyName())
                      .param("newValue", current))
              .andReturn();
      assertNull(allowed.getResolvedException());
      assertEquals(current, getFromJsonAjaxReturnObject(allowed, String.class));
    } finally {
      sysPropMgr.save(name, current, getSysAdminUser());
    }
  }

  @Test
  public void readEndpointsOmitCredentials() throws Exception {
    logoutAndLoginAsCommunityAdmin();
    MvcResult res =
        mockMvc.perform(get("/deploymentproperties/ajax/editableProperties")).andReturn();
    Map<String, Object> data = parseJSONObjectFromResponseStream(res);
    assertThat(data.keySet())
        .contains("self_service_labgroups", "allow_project_groups") // read by admin.js
        .doesNotContainAnyElementsOf(FREE_TEXT_SETTINGS);

    for (String name : FREE_TEXT_SETTINGS) {
      MvcResult single =
          mockMvc
              .perform(get("/deploymentproperties/ajax/property").param("name", name))
              .andReturn();
      assertInstanceOf(IllegalArgumentException.class, single.getResolvedException());
    }
  }

  @Test
  public void multiServerConfigReadsDropServerSecrets() throws Exception {
    Object target = AopTestUtils.getUltimateTargetObject(deploymentPropertiesController);
    Object pyrat = ReflectionTestUtils.getField(target, "pyratServerConfig");
    Object raid = ReflectionTestUtils.getField(target, "raidServerConfig");
    try {
      ReflectionTestUtils.setField(
          target,
          "pyratServerConfig",
          "{\"main\":{\"url\":\"https://pyrat.example\",\"token\":\"pyrat-secret\"}}");
      ReflectionTestUtils.setField(
          target,
          "raidServerConfig",
          "{\"DEMO\":{\"url\":\"https://raid.example\",\"clientId\":\"raid-client\","
              + "\"clientSecret\":\"raid-secret\"}}");
      logoutAndLoginAsCommunityAdmin(); // any user can read these

      assertThat(propertyResponse("pyrat.server.config"))
          .contains("https://pyrat.example")
          .doesNotContain("pyrat-secret");
      assertThat(propertyResponse("raid.server.config"))
          .contains("https://raid.example", "raid-client")
          .doesNotContain("raid-secret");
    } finally {
      ReflectionTestUtils.setField(target, "pyratServerConfig", pyrat);
      ReflectionTestUtils.setField(target, "raidServerConfig", raid);
    }
  }

  private String propertyResponse(String name) throws Exception {
    return mockMvc
        .perform(get("/deploymentproperties/ajax/property").param("name", name))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}

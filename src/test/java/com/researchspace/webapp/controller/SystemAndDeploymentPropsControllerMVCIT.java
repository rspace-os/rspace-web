package com.researchspace.webapp.controller;

import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyName;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;

public class SystemAndDeploymentPropsControllerMVCIT extends MVCTestBase {

  @Value("${egnyte.client.id}")
  private String egnyteClientId;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    logout();
    super.tearDown();
  }

  @Test
  public void testGetPropertyValues() throws Exception {
    logoutAndLoginAsCommunityAdmin(); // can be anyone,
    MvcResult res = mockMvc.perform(get("/deploymentproperties/ajax/properties")).andReturn();
    @SuppressWarnings("unchecked")
    Map<String, String> data = getFromJsonResponseBody(res, Map.class);
    final int MIN_PROPERTY_COUNT = 7; // from rspac861
    assertTrue(data.keySet().size() >= MIN_PROPERTY_COUNT);
    // assert properties are merged from DB...
    assertNotNull(data.get(SystemPropertyName.DROPBOX_AVAILABLE.getPropertyName()));
    // .. and property files
    assertNotNull(data.get("baseURL"));
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
  }
}

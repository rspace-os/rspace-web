package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
@TestPropertySource(properties = "ror.api.url=https://api.ror.org/v2/organizations")
// tests marked as NIGHLTY are contacting the REAL ROR API
@RunWith(ConditionalTestRunner.class)
public class RoRSysAdminControllerMVCIT extends MVCTestBase {

  private static final String rorSysadminUrl = "/system/ror/";
  private static final String rorPublicUrl = "/global/ror/";

  /* ROR IDS can be the full Url - Spring controllers however, don't allow forward
  slashes in request params, so front end escapes them */
  private static final String validRorOne =
      "https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__038xqyz77";
  private static final String validRorTwo = "ror.org__rspacror_forsl__038xqyz77";
  private static final String validRorThree = "038xqyz77";
  public static final String ROR_FULL_ID = "https://ror.org/038xqyz77";
  public static final String ROR_NAME = "Research Space (United Kingdom)";
  @Autowired private SystemPropertyManager systemPropertyManager;

  @Before
  public void setup() throws Exception {
    super.setUp();
    systemPropertyManager.save(SystemPropertyName.RSPACE_ROR, "", getSysAdminUser());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testGetExistingRor() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "existingGlobalRoRID"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals("", result.getResponse().getContentAsString());
  }

  @Test
  public void testGetExistingRorPublicEndpoint() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(rorPublicUrl + "existingGlobalRoRID"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals("", result.getResponse().getContentAsString());
  }

  @Test
  public void testGetExistingRorNamePublicEndpoint() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(rorPublicUrl + "existingGlobalRoRName"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals("", result.getResponse().getContentAsString());
  }

  @Test
  public void testGetExistingRorName() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "existingGlobalRoRName"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals("", result.getResponse().getContentAsString());
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testSearchForValidRor() throws Exception {
    // There are 3 versions of a valid ROR ID
    mockMvc
        .perform(get(rorSysadminUrl + "rorForID/" + validRorOne))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.id", is(ROR_FULL_ID)))
        .andReturn();
    mockMvc
        .perform(get(rorSysadminUrl + "rorForID/" + validRorTwo))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.id", is(ROR_FULL_ID)))
        .andReturn();
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "rorForID/" + validRorThree))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.id", is(ROR_FULL_ID)))
            .andReturn();

    JsonNode rorDetails = getFromJsonResponseBody(result, JsonNode.class);
    String rorID = rorDetails.get("id").asText();
    assertEquals(ROR_FULL_ID, rorID);
    checkV2City(rorDetails);
    checkV2Country(rorDetails);
    checkV2Links(rorDetails);
    checkV2Names(rorDetails);
  }

  @Test
  public void testSearchForInValidRor() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "rorForID/" + validRorOne + 1))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(
        "https://ror.org/038xqyz771 is not a valid ROR",
        result.getModelAndView().getModel().get("exceptionMessage"));
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testUpdateRorAsSysadmin() throws Exception {
    logoutAndLoginAsSysAdmin();
    mockMvc
        .perform(post(rorSysadminUrl + "rorForID/" + validRorOne))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.data", is(true)));
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "existingGlobalRoRID"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(ROR_FULL_ID, result.getResponse().getContentAsString());
    result =
        mockMvc
            .perform(get(rorSysadminUrl + "existingGlobalRoRName"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(ROR_NAME, result.getResponse().getContentAsString());
  }

  @Test
  public void testUpdateRorAsNonSysadminNotAllowed() throws Exception {
    User notSysAdmin = createAndSaveUser(getRandomAlphabeticString("user"));
    logoutAndLoginAs(notSysAdmin);
    MvcResult result =
        mockMvc
            .perform(post(rorSysadminUrl + "rorForID/" + validRorOne))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Unauthorized role manipulation attempt"));
  }

  @Test
  public void tesDeleteRorAsSysadmin() throws Exception {
    logoutAndLoginAsSysAdmin();
    mockMvc
        .perform(delete(rorSysadminUrl + "rorForID/"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.data", is(true)));
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "existingGlobalRoRID"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals("", result.getResponse().getContentAsString());
  }

  @Test
  public void testDeleteRorAsNonSysadminNotAllowed() throws Exception {
    User notSysAdmin = createAndSaveUser(getRandomAlphabeticString("user"));
    logoutAndLoginAs(notSysAdmin);
    MvcResult result =
        mockMvc
            .perform(delete(rorSysadminUrl + "rorForID/"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Unauthorized role manipulation attempt"));
  }

  private void checkV2Country(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("locations").findValues("country_name", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"United Kingdom\""));
  }

  private void checkV2City(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("locations").findValues("name", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"Edinburgh\""));
  }

  private void checkV2Names(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("names").findValues("value", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"Research Space\""));
  }

  private void checkV2Links(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("links").findValues("value", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"https://www.researchspace.com\""));
  }
}

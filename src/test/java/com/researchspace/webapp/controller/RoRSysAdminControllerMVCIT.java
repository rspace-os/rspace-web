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
@TestPropertySource(properties = "ror.api.url=https://api.ror.org/organizations")
// The NIGHLTY test contacts the REAL ROR API

// Uncomment and use the DEV url to test v2 of RoR API EXPLICITLY.
// RoR will switch to v2 api as default sometime soon and therefore the tests use PROD api and check
// the response for which version is being returned and then assert the values which we use in the
// front end
// are present as expected.

// @TestPropertySource(properties = "ror.api.url=https://api.dev.ror.org/v2/organizations")
@RunWith(ConditionalTestRunner.class)
public class RoRSysAdminControllerMVCIT extends MVCTestBase {

  private static final String rorSysadminUrl = "/system/ror/";
  private static final String rorPublicUrl = "/global/ror/";
  // ROR IDS can be the full Url - Spring controllers however, dont allow forward slashes in request
  // params, so front end escapes them
  private static final String validRorOne =
      "https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp94";
  private static final String validRorTwo = "ror.org__rspacror_forsl__02mhbdp94";
  private static final String validRorThree = "02mhbdp94";
  public static final String ROR_FULL_ID = "https://ror.org/02mhbdp94";
  public static final String ROR_NAME = "Universidad de Los Andes";
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
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "rorForID/" + validRorOne))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.id", is(ROR_FULL_ID)))
            .andReturn();
    result =
        mockMvc
            .perform(get(rorSysadminUrl + "rorForID/" + validRorTwo))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.id", is(ROR_FULL_ID)))
            .andReturn();
    result =
        mockMvc
            .perform(get(rorSysadminUrl + "rorForID/" + validRorThree))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.id", is(ROR_FULL_ID)))
            .andReturn();
    JsonNode rorDetails = getFromJsonResponseBody(result, JsonNode.class);
    String rorID = rorDetails.get("id").asText();
    assertEquals(ROR_FULL_ID, rorID);
    // test v1 API
    if (rorDetails.get("addresses") != null) {
      String country = rorDetails.get("country").get("country_name").asText();
      assertEquals("Colombia", country);
      checkV1Links(rorDetails);
      checkV1City(rorDetails);
      String name = rorDetails.get("name").asText();
      assertEquals("Universidad de Los Andes", name);
    } else {
      // test v2 api - switch should happen transparently when RoR decide
      // to do so in the next few months from Oct 2023 - we can delete v1 tests at that point
      checkV2City(rorDetails);
      checkV2Country(rorDetails);
      checkV2Links(rorDetails);
      checkV2Names(rorDetails);
    }
  }

  @Test
  public void testSearchForInValidRor() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(rorSysadminUrl + "rorForID/" + validRorOne + 1))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(
        "https://ror.org/02mhbdp941 is not a valid ROR",
        result.getModelAndView().getModel().get("exceptionMessage"));
  }

  @Test
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
    User sysadmin = logoutAndLoginAsSysAdmin();
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
            .contains("\"Colombia\""));
  }

  private void checkV2City(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("locations").findValues("name", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"Bogotá\""));
  }

  private void checkV2Names(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("names").findValues("value", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"Universidad de Los Andes\""));
  }

  private void checkV2Links(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("links").findValues("value", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"http://www.uniandes.edu.co/\""));
  }

  private void checkV1Links(JsonNode rorDetails) {
    assertTrue(rorDetails.get("links").toString().contains("\"http://www.uniandes.edu.co/\""));
  }

  private void checkV1City(JsonNode rorDetails) {
    assertTrue(
        rorDetails.get("addresses").findValues("city", new ArrayList<>()).stream()
            .map(n -> n.toString())
            .collect(Collectors.toList())
            .contains("\"Bogotá\""));
  }
}

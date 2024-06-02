package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.AuditTrailSearchResultCsvGenerator.ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_CSV;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.audit.search.IAuditFileSearch;
import java.io.File;
import java.security.Principal;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class AuditTrailControllerMVCIT extends MVCTestBase {

  @Autowired IAuditFileSearch srch;

  @Before
  public void setUp() throws Exception {
    srch.setLogFilePrefix("RSLog");
    srch.setLogFolder(new File("src/test/resources/TestResources"));
    super.setUp();
  }

  @Test
  public void testGetDomains() throws Exception {
    MvcResult result =
        this.mockMvc.perform(get("/audit/domains")).andExpect(status().isOk()).andReturn();
  }

  @Test
  public void testGetActions() throws Exception {
    MvcResult result =
        this.mockMvc.perform(get("/audit/actions")).andExpect(status().isOk()).andReturn();
  }

  @Test
  public void testGetQueryableUsers() throws Exception {
    User newUser = createAndSaveUser(getRandomAlphabeticString("user"));
    Principal subject = newUser::getUsername;
    logoutAndLoginAs(newUser);
    MvcResult result =
        this.mockMvc
            .perform(get("/audit/queryableUsers").principal(subject).param("term", ""))
            .andExpect(status().isOk())
            .andReturn();
    Map data = parseJSONObjectFromResponseStream(result);
    assertTrue(data.keySet().contains("data"));
  }

  @Test
  public void testDownloadAsCsv() throws Exception {

    logoutAndLoginAsSysAdmin();
    final int TOTAL_HITS = 26;
    MvcResult result =
        this.mockMvc
            .perform(
                get("/audit/download")
                    .param("dateFrom", "2014-05-16")
                    .param("dateTo", "2014-05-19"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .stringValues(
                        "Content-Disposition", ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_CSV))
            .andExpect(content().contentType(MediaType.parseMediaType("text/csv")))
            .andReturn();
    String csv = result.getResponse().getContentAsString();
    String[] rows = csv.split("\\n");
    // 1 row per event, plus header plus comment line
    assertEquals(TOTAL_HITS + 2, rows.length);
  }

  @Test
  public void testQuery() throws Exception {
    User newUser = createAndSaveUser(getRandomAlphabeticString("user"));
    logoutAndLoginAsSysAdmin();
    final int TOTAL_HITS = 26;
    MvcResult result =
        this.mockMvc
            .perform(
                get("/audit/query").param("dateFrom", "2014-05-16").param("dateTo", "2014-05-19"))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.totalHits").value(TOTAL_HITS))
            .andReturn();
    // check validation of date syntax
    result =
        this.mockMvc
            .perform(
                get("/audit/query").param("dateFrom", "2014/05/16").param("dateTo", "2014-05-19"))
            .andExpect(status().isOk())
            .andExpect(errorMsg())
            .andReturn();

    // check  error handling of invalid oid
    this.mockMvc
        .perform(get("/audit/query").param("oid", "INVALID"))
        .andExpect(status().isOk())
        .andExpect(errorMsg())
        .andReturn();

    // check  error handling of invalid group syntx
    MvcResult result3 =
        this.mockMvc
            .perform(get("/audit/query").param("groups", "INVALID"))
            .andExpect(status().isOk())
            .andExpect(errorMsg())
            .andReturn();
  }

  private ResultMatcher errorMsg() {
    return MockMvcResultMatchers.jsonPath("$.errorMsg").exists();
  }

  @Test
  public void testQueryByCommunity() throws Exception {
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    // set up a community.
    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("c1"));

    User pi1 = createAndSaveUser(getRandomAlphabeticString("pi1"), Constants.PI_ROLE);
    User pi2 = createAndSaveUser(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    initUsers(pi1, pi2);
    logoutAndLoginAs(admin);
    // add 2 groups
    Group g1 = createGroupForUsers(admin, pi1.getUsername(), "", pi1);
    Group g2 = createGroupForUsers(admin, pi2.getUsername(), "", pi2);
    logoutAndLoginAs(admin);
    communityMgr.addGroupToCommunity(g1.getId(), comm.getId(), admin);
    communityMgr.addGroupToCommunity(g2.getId(), comm.getId(), admin);
    // check  error handling of invalid group syntx
    MvcResult result3 =
        this.mockMvc
            .perform(
                get("/audit/query")
                    .param("community", comm.getDisplayName() + "<" + comm.getId() + ">"))
            .andExpect(status().isOk())
            .andReturn();
    Map data = parseJSONObjectFromResponseStream(result3);
    assertNotNull(data.get("data"));
  }
}

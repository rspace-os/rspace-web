package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.offline.service.OfflineManager;
import java.security.Principal;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class MobileControllerMVCIT extends MVCTestBase {

  @Autowired MockServletContext servletContext;

  private Principal principal;
  private User user;
  private BaseRecord doc1;

  @Autowired private OfflineManager offlineManager;

  @Before
  public void setup() throws Exception {
    user = createAndSaveUser(getRandomAlphabeticString("user"));
    setUpUserWithInitialisedContent(user);

    logoutAndLoginAs(user);
    principal = new MockPrincipal(user.getUsername());

    doc1 = createBasicDocumentInRootFolderWithText(user, "testDoc1");
    offlineManager.addRecordForOfflineWork(doc1, user, activeUsers);
  }

  @Test
  public void testStatusUrlReturnsOK() throws Exception {

    MvcResult result = mockMvc.perform(get("/mobile/status")).andReturn();
    assertNull(result.getResolvedException());

    String response = result.getResponse().getContentAsString();
    assertEquals("mobile status response not ok", "OK", response);
  }

  @Test
  public void testAuthenticationUrlReturnsOK() throws Exception {

    MvcResult result =
        mockMvc.perform(get("/mobile/authenticate").principal(principal)).andReturn();
    assertNull(result.getResolvedException());

    String response = result.getResponse().getContentAsString();
    assertEquals("mobile authentication response not ok", "OK", response);
  }

  @Test
  public void testGetOfflineWorkList() throws Exception {

    MvcResult result =
        mockMvc
            .perform(get("/mobile/offlineRecordList").principal(principal))
            .andExpect(MockMvcResultMatchers.jsonPath("$.[*].id", not(empty())))
            .andReturn();
    assertNull(result.getResolvedException());

    String response = result.getResponse().getContentAsString();
    assertNotNull(response);
    assertTrue("response list not empty", response.length() > 2);
  }

  @Test
  public void testDownloadOfflineRecord() throws Exception {

    MvcResult result =
        mockMvc
            .perform(get("/mobile/record/" + doc1.getId()).principal(principal))
            .andExpect(jsonPath("$.name", containsString("BasicDocument")))
            .andReturn();
    assertNull(result.getResolvedException());

    String response = result.getResponse().getContentAsString();
    assertNotNull(response);
    assertTrue("response object not empty", response.length() > 2);
  }

  private String dummyNewOfflineRecordJson =
      "{\"id\":null,\"name\":\"test record\",\"path\":\"(local records)\","
          + "\"lockType\":\"EDIT\",\"lastSynchronisedModificationTime\":213,\"images\":[]}";

  @Test
  public void testUploadNewOfflineRecord() throws Exception {

    MvcResult result =
        mockMvc
            .perform(
                post("/mobile/uploadRecord")
                    .content(dummyNewOfflineRecordJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(principal))
            .andReturn();
    assertNull(result.getResolvedException());

    String response = result.getResponse().getContentAsString();
    assertTrue("response object empty", response != null && response.length() > 0);
    assertTrue(
        "response can't be parsed to positive id: " + response, Long.parseLong(response) > 0);
  }
}

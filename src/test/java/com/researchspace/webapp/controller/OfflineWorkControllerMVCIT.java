package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.model.User;
import com.researchspace.model.record.Record;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.security.Principal;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
public class OfflineWorkControllerMVCIT extends RealTransactionSpringTestBase {

  @Autowired MockServletContext servletContext;

  @Autowired private WebApplicationContext wac;

  private MockMvc mockMvc;

  private Principal principal;
  private User user;

  @Before
  public void setup() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();

    user = createAndSaveUser(getRandomAlphabeticString("user"));
    setUpUserWithInitialisedContent(user);

    logoutAndLoginAs(user);
    principal = new MockPrincipal(user.getUsername());
  }

  @Test
  public void testAddRemoveRecordFromOfflineWork() throws Exception {

    Record doc1 = createBasicDocumentInRootFolderWithText(user, "testDoc1");

    MvcResult result1 =
        mockMvc
            .perform(
                post("/offlineWork/selectForOffline")
                    .principal(principal)
                    .param("recordIds[]", doc1.getId() + ""))
            .andReturn();
    String response1 = result1.getResponse().getContentAsString();
    assertEquals("Done.\nRecord " + doc1.getId() + " marked for offline work.", response1);

    MvcResult result2 =
        mockMvc
            .perform(
                post("/offlineWork/removeFromOffline")
                    .principal(principal)
                    .param("recordIds[]", doc1.getId() + ""))
            .andReturn();
    String response2 = result2.getResponse().getContentAsString();
    assertEquals("Done.\nFinished offline work for record " + doc1.getId() + ".", response2);
  }
}

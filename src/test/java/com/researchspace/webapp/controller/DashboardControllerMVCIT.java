package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class DashboardControllerMVCIT extends MVCTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void loginAndListDashboard() throws Exception {
    setUpLoginAsPIUserAndCreateADocument();

    // now lists dashboard
    this.mockMvc
        .perform(get("/dashboard").principal(mockPrincipal))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("paginationList", "notificationList", "timeOfListing"));
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void listNotifications() throws Exception {

    User sender = createInitAndLoginAnyUser();
    logoutAndLoginAs(sender);

    // now lists notifications
    this.mockMvc
        .perform(get("/dashboard/ajax/listNotifications").principal(sender::getUsername))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("paginationList", "notificationList", "timeOfListing"));
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void listMessagesTest() throws Exception {

    User sender = createInitAndLoginAnyUser();
    User target = createInitAndLoginAnyUser();

    logoutAndLoginAs(sender);
    sendSimpleMessage(sender, "Hi there !", target);
    logoutAndLoginAs(target);
    MvcResult result =
        this.mockMvc
            .perform(get("/dashboard/ajax/allMessages").principal(target::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andExpect(
                model().attributeExists("paginationList", "messages", "timeOfListing", "user"))
            .andReturn();
    assertNResults(result, 1);
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void listMessagesTest2() throws Exception {

    User sender = createInitAndLoginAnyUser();
    User target = createInitAndLoginAnyUser();

    for (int i = 0; i < 10; i++) {
      logoutAndLoginAs(sender);
      sendSimpleMessage(sender, "Hi there ! Message (" + i + ")", target);
      logoutAndLoginAs(target);
      MvcResult result =
          this.mockMvc
              .perform(get("/dashboard/ajax/allMessages").principal(target::getUsername))
              .andExpect(status().is2xxSuccessful())
              .andExpect(
                  model().attributeExists("paginationList", "messages", "timeOfListing", "user"))
              .andReturn();
      assertNResults(result, i + 1);
    }
  }

  /**
   * Reference: RSPAC-438
   *
   * @throws Exception
   */
  @Test
  public void listMessagesTest3() throws Exception {

    User sender = createInitAndLoginAnyUser();
    User target = createInitAndLoginAnyUser();
    logoutAndLoginAs(sender);

    for (int i = 0; i <= 10; i++) {
      sendSimpleMessage(sender, "Hi there ! Message (" + i + ")", target);
    }

    logoutAndLoginAs(target);
    MvcResult result =
        this.mockMvc
            .perform(get("/dashboard/ajax/allMessages").principal(target::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andExpect(
                model().attributeExists("paginationList", "messages", "timeOfListing", "user"))
            .andReturn();
    assertNResults(result, 10);
  }

  @Test
  public void listSentRequestTest1() throws Exception {
    User sender = createInitAndLoginAnyUser();
    User target = createInitAndLoginAnyUser();
    logoutAndLoginAs(sender);

    Folder rootFolder = folderMgr.getRootRecordForUser(sender, sender);
    RSForm form = createAnyForm(sender);
    StructuredDocument doc = createDocumentInFolder(rootFolder, form, sender);
    sendSharedRecordRequest(sender, doc, "read", target);

    MvcResult result =
        this.mockMvc
            .perform(get("/dashboard/ajax/listMyRequests").principal(sender::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andExpect(
                model().attributeExists("paginationList", "messages", "timeOfListing", "user"))
            .andReturn();
    assertNResults(result, 1);
  }

  @Test
  public void listSentRequestTest2() throws Exception {
    User sender = createInitAndLoginAnyUser();
    User target = createInitAndLoginAnyUser();
    logoutAndLoginAs(sender);

    Folder rootFolder = folderMgr.getRootRecordForUser(sender, sender);

    for (int i = 0; i <= 10; i++) {
      RSForm form = createAnyForm(sender);
      StructuredDocument doc = createDocumentInFolder(rootFolder, form, sender);
      sendSharedRecordRequest(sender, doc, "read", target);
    }

    MvcResult result =
        this.mockMvc
            .perform(get("/dashboard/ajax/listMyRequests").principal(sender::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andExpect(
                model().attributeExists("paginationList", "messages", "timeOfListing", "user"))
            .andReturn();
    assertNResults(result, 10);
  }

  @Test
  @Ignore
  public void listMessagesByType() throws Exception {
    User sender = createInitAndLoginAnyUser();
    User target = createInitAndLoginAnyUser();
    logoutAndLoginAs(sender);
    sendSimpleMessage(sender, "hello", target);

    // now login as target and get ALL message listing:
    logoutAndLoginAs(target);
    MvcResult result =
        this.mockMvc
            .perform(get("/dashboard/ajax/allMessages").principal(target::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNResults(result, 1);

    // now we'll use an optional filter to include msg:
    result =
        this.mockMvc
            .perform(get("/dashboard/ajax/specialMessages").principal(target::getUsername))
            .andReturn();
    assertNResults(result, 0);
  }

  @SuppressWarnings("unchecked")
  private void assertNResults(MvcResult result, int expectedResults) {
    List<MessageOrRequest> messagesList =
        (List<MessageOrRequest>) result.getModelAndView().getModelMap().get("messages");
    assertEquals(expectedResults, messagesList.size());
  }
}

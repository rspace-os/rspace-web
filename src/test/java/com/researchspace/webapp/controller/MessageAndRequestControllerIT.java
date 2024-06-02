package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import java.time.Year;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

public class MessageAndRequestControllerIT extends MVCTestBase {

  @Autowired private MessageAndRequestController ctrller;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testListRecipientsForGeneralMessage() throws Exception {
    // check works OK with no record set
    MsgOrReqstCreationCfg reCmnd = new MsgOrReqstCreationCfg();
    reCmnd.setRecordId(null);
    reCmnd.setMessageType(MessageType.SIMPLE_MESSAGE);
    reCmnd.setTargetFinderPolicy("ALL");

    // happy case
    this.mockMvc
        .perform(
            get("/messaging//ajax/recipients")
                .principal(mockPrincipal)
                .param("messageType", reCmnd.getMessageType().toString())
                .param("targetFinderPolicy", reCmnd.getTargetFinderPolicy()))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void testAvailableMessageTypes() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(CoreTestUtils.getRandomName(7));
    initUser(other);
    Group grp = createGroupForUsersWithDefaultPi(piUser, other);
    logoutAndLoginAs(piUser); // as grp pi
    MvcResult res =
        this.mockMvc
            .perform(get("/messaging/ajax/create").principal(mockPrincipal))
            .andExpect(model().hasNoErrors())
            .andReturn();
    MsgOrReqstCreationCfg rq = getReqCommandFromModel(res);
    assertTrue(ArrayUtils.contains(rq.getAllMessageTypes(), MessageType.REQUEST_EXTERNAL_SHARE));

    logoutAndLoginAs(other); // as non pi - can't request outside group.
    MvcResult res2 =
        this.mockMvc
            .perform(
                get("/messaging/ajax/create").principal(new MockPrincipal(other.getUsername())))
            .andExpect(model().hasNoErrors())
            .andReturn();
    MsgOrReqstCreationCfg rq2 = getReqCommandFromModel(res2);
    assertFalse(ArrayUtils.contains(rq2.getAllMessageTypes(), MessageType.REQUEST_EXTERNAL_SHARE));

    // now lets run with specification of requestParam to control the msg type.
    logoutAndLoginAs(piUser); // as grp pi
    MvcResult res3 =
        this.mockMvc
            .perform(
                get("/messaging/ajax/create")
                    .principal(mockPrincipal)
                    .param(
                        "messageTypes[]",
                        "SIMPLE_MESSAGE,REQUEST_RECORD_REVIEW")) // 2 options available here
            .andExpect(model().hasNoErrors())
            .andReturn();
    MsgOrReqstCreationCfg rq3 = getReqCommandFromModel(res3);
    assertFalse(ArrayUtils.contains(rq2.getAllMessageTypes(), MessageType.REQUEST_EXTERNAL_SHARE));
    assertEquals(2, rq3.getAllMessageTypes().length);
  }

  private MsgOrReqstCreationCfg getReqCommandFromModel(MvcResult res3) {
    return (MsgOrReqstCreationCfg) res3.getModelAndView().getModelMap().get("request");
  }

  @Test
  public void testListRecipients() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser("other");
    initUser(other);
    Group grp = createGroupForUsersWithDefaultPi(piUser, other);

    ShareConfigElement gsce = new ShareConfigElement(grp.getId(), "write");

    ShareConfigElement[] gsceArray = new ShareConfigElement[] {gsce};
    AjaxReturnObject<List<UserBasicInfo>> rc =
        ctrller.getRecipients(
            mockPrincipal, sd.getId(), MessageType.REQUEST_RECORD_WITNESS, "STRICT", null);
    assertNull(rc.getData()); // no candidates yet as not shared
    assertNotNull(rc.getErrorMsg()); // no candidates yet as not shared
    doInTransaction(
        () -> {
          sharingMgr.shareRecord(piUser, sd.getId(), gsceArray);
          AjaxReturnObject<List<UserBasicInfo>> rc2 =
              ctrller.getRecipients(
                  mockPrincipal, sd.getId(), MessageType.REQUEST_RECORD_WITNESS, "STRICT", null);
          assertEquals(1, rc2.getData().size()); // now is shared with other.
        });
  }

  @Test
  public void testCreateRequestValidation() throws Exception {

    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    // happy case
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.SIMPLE_MESSAGE.toString())
                .param("targetFinderPolicy", "ALL")
                .param("recipientnames", piUser.getUsername())
                .param("recordId", sd.getId() + ""))
        .andExpect(model().hasNoErrors());

    User other = createAndSaveUser(CoreTestUtils.getRandomName(8));
    // request review record to someone unauthorised to view the record
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                .param("recipientnames", other.getUsername())
                .param("recordId", sd.getId() + ""))
        .andExpect(model().attributeHasFieldErrors("request", "recipientnames"));

    // no user specified
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                .param("recipientnames", "") // no recipients
                .param("recordId", sd.getId() + ""))
        .andExpect(model().attributeHasFieldErrors("request", "recipientnames"));

    // invalid record id
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_WITNESS.toString())
                .param("recipientnames", piUser.getUsername())
                .param("recordId", 10000L + "")) // unknown recordID
        .andExpect(model().errorCount(1));

    // invalid date format
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                .param("recipientnames", piUser.getUsername())
                .param("recordId", sd.getId() + "")
                .param("requestedCompletionDate", "1.2.3"))
        .andExpect(model().errorCount(1))
        .andExpect(model().attributeHasFieldErrors("request", "requestedCompletionDate"));

    // date in the past not allowed
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                .param("recipientnames", piUser.getUsername())
                .param("recordId", sd.getId() + "")
                .param("requestedCompletionDate", "2013-01-31 12:00"))
        .andExpect(model().errorCount(1))
        .andExpect(model().attributeHasFieldErrors("request", "requestedCompletionDate"));

    // date in the future, this is OK
    String nextYearAsString = (Year.now().getValue() + 1) + "";
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                .param("recipientnames", piUser.getUsername())
                .param(
                    "targetFinderPolicy", "ALL") // saves having to create a group for test to pass
                .param("recordId", sd.getId() + "")
                .param("requestedCompletionDate", nextYearAsString + "-01-31 12:00"))
        .andExpect(model().hasNoErrors());

    // message too long, should be silently truncated
    String maxLengthMessage = new String(new char[Communication.MESSAGE_COLUMN_LENGTH]);
    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(mockPrincipal)
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                .param("recipientnames", piUser.getUsername())
                .param("targetFinderPolicy", "ALL")
                .param("recordId", sd.getId() + "")
                .param("optionalMessage", maxLengthMessage + "test"))
        /* controller exceptions are not directly propagated to ResultActions, so
         * checking if method returned correct view (it would return 'error' on exception) */
        .andExpect(view().name("empty"));
  }

  @Test
  public void testJoinCollabGroup() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User pi1 = createAndSaveUser("pi1" + CoreTestUtils.getRandomName(7), Constants.PI_ROLE);
    logoutAndLoginAs(piUser);
    initUser(pi1);
    User pi2 = createAndSaveUser("pi2" + CoreTestUtils.getRandomName(7), Constants.PI_ROLE);
    User pi3 = createAndSaveUser("pi3" + CoreTestUtils.getRandomName(7), Constants.PI_ROLE);
    initUser(pi2);
    initUser(pi3);

    Group labGrp1 = createGroupForUsers(piUser, pi1.getUsername(), "", pi1);
    Group labGrp2 = createGroupForUsers(piUser, pi2.getUsername(), "", pi2);
    // this is 3rd lab group who we'll invite to join existing collab group
    Group labGrp3 = createGroupForUsers(piUser, pi3.getUsername(), "", pi3);

    logoutAndLoginAs(pi1); // initiate collabgroup
    Group collabGroup = createCollabGroupBetweenGroups(labGrp1, labGrp2);
    logoutAndLoginAs(pi1);
    MvcResult res =
        this.mockMvc
            .perform(
                get("/messaging/ajax/create")
                    .principal(new MockPrincipal(pi1.getUsername()))
                    .param("groupId", "1"))
            .andExpect(model().attributeExists("request"))
            .andReturn();
    MsgOrReqstCreationCfg cfg = getReqCommandFromModel(res);
    assertNotNull(cfg.getGroupId());

    this.mockMvc
        .perform(
            post("/messaging/ajax/create")
                .principal(new MockPrincipal(pi1.getUsername()))
                .sessionAttr("request", new MsgOrReqstCreationCfg())
                .param("messageType", MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP.toString())
                .param("recipientnames", pi3.getUsername())
                .param(
                    "targetFinderPolicy",
                    "ALL_PIS") // saves having to create a group for test to pass
                .param("groupId", collabGroup.getId() + ""))
        .andExpect(model().hasNoErrors());
    // now we'll login as pi3 and respond
    Long requestId = getGroupMOROriginatedByUser(pi1);
    logoutAndLoginAs(pi3); // initiate collabgroup
    this.mockMvc.perform(
        post("/dashboard/ajax/messageStatus")
            .principal(new MockPrincipal(pi3.getUsername()))
            .param("status", "COMPLETED")
            .param("messageOrRequestId", requestId + ""));

    Group updatedCollabGrp = grpMgr.getGroup(collabGroup.getId());
    assertEquals(3, updatedCollabGrp.getMembers().size());
    assertTrue(updatedCollabGrp.getMembers().contains(pi3));
    assertEquals(RoleInGroup.PI, updatedCollabGrp.getRoleForUser(pi3));
  }

  @Test
  public void checkICalendarFileForRecordReviewRequest() throws Exception {

    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User target = createAndSaveUser(getRandomAlphabeticString("target"));
    initUser(target);
    String nextYearAsString = (Year.now().getValue() + 1) + "";
    MvcResult createResponse =
        this.mockMvc
            .perform(
                post("/messaging/ajax/create")
                    .principal(mockPrincipal)
                    .sessionAttr("request", new MsgOrReqstCreationCfg())
                    .param("messageType", MessageType.REQUEST_RECORD_REVIEW.toString())
                    .param("targetFinderPolicy", "ALL")
                    .param("recipientnames", target.getUsername())
                    .param("requestedCompletionDate", nextYearAsString + "-01-31 12:00")
                    .param("recordId", sd.getId() + ""))
            .andExpect(model().hasNoErrors())
            .andReturn();

    Long msgId = (Long) createResponse.getModelAndView().getModel().get("createdMessageId");
    logoutAndLoginAs(target);
    MvcResult icalResponse = getIcalForRequest(target, msgId);
    logoutAndLoginAs(piUser);
    icalResponse = getIcalForRequest(piUser, msgId);

    MockHttpServletResponse response = icalResponse.getResponse();
    assertEquals("text/calendar", response.getContentType());
    assertEquals("attachment; filename=rspace.ics", response.getHeader("Content-Disposition"));
    // other user can't access rspac1253
    User otherUser = createInitAndLoginAnyUser();
    icalResponse =
        this.mockMvc
            .perform(
                get("/messaging/ical?id=" + msgId)
                    .principal(new MockPrincipal(otherUser.getUsername())))
            .andReturn();
    assertAuthorizationException(icalResponse);
    RSpaceTestUtils.logout();
  }

  private MvcResult getIcalForRequest(User target, Long msgId) throws Exception {
    MvcResult icalResponse =
        this.mockMvc
            .perform(
                get("/messaging/ical?id=" + msgId)
                    .principal(new MockPrincipal(target.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    return icalResponse;
  }

  private Long getGroupMOROriginatedByUser(User u) {
    openTransaction();
    Session session = sessionFactory.getCurrentSession();
    List results =
        session
            .createQuery("from GroupMessageOrRequest where originator.id=:id")
            .setParameter("id", u.getId())
            .list();
    commitTransaction();
    return ((MessageOrRequest) results.get(0)).getId();
  }
}

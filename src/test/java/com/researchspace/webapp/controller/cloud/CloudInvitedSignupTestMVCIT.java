package com.researchspace.webapp.controller.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.cloud.impl.CommunityPostSignupVerification;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.controller.SignupController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;

/** Full acceptance test for signup of invited user. */
@WebAppConfiguration
@CommunityTestContext
public class CloudInvitedSignupTestMVCIT extends MVCTestBase {

  @Autowired private RSCommunityController cloudController;

  private HttpServletRequest mockRequest;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    mockRequest = new MockHttpServletRequest();
    assertTrue(propertyHolder.isCloud());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void inviteNewUserToJoinGroupHappyCase() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);
    final String username = getRandomAlphabeticString("newuser");
    final String randomEmail = username + "@x.com";

    // step 1 invite new user & logout
    inviteNewUserToJoinGroup(creator, randomEmail);
    RSpaceTestUtils.logout();
    User newUser = followSignupWorkflow(username, randomEmail);
    // finally lets login and check there is a correct message waiting for them
    logoutAndLoginAs(newUser);
    assertNewUserHasIncomingRequestOfType(newUser, MessageType.REQUEST_JOIN_LAB_GROUP);
  }

  @Test
  public void inviteNewUserBePIOfGroupHappyCase() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("creator"), Constants.USER_ROLE);
    // this is just someone to be in the group that will be created when the PI accepts
    // this is needed for the form to b eaccepted.
    User existingUserToBeInGroup =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);
    final String newUsername = getRandomAlphabeticString("newPI");
    final String randomEmail = newUsername + "@x.com";

    // step 1 invite new user & logout
    inviteNewUserBePIOfNewLabGroupGroup(creator, randomEmail, existingUserToBeInGroup);
    RSpaceTestUtils.logout();
    User newUser = followSignupWorkflow(newUsername, randomEmail);
    // finally lets login and check there is a correct message waiting for them
    logoutAndLoginAs(newUser);
    assertNewUserHasIncomingRequestOfType(newUser, MessageType.REQUEST_CREATE_LAB_GROUP);
  }

  @Test
  public void inviteNewUserShareRecord() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("creator"), Constants.USER_ROLE);

    Folder rootFolder = initUser(creator);
    logoutAndLoginAs(creator);
    RSForm t = createAnyForm(creator);
    StructuredDocument doc = createDocumentInFolder(rootFolder, t, creator);

    final String username = getRandomAlphabeticString("user");
    final String randomEmail = username + "@x.com";

    // step 1 invite new user & logout
    inviteNewUserShareRecord(creator, randomEmail, "read", doc);
    RSpaceTestUtils.logout();
    User newUser = followSignupWorkflow(username, randomEmail);
    // finally lets login and check there is a correct message waiting for them
    logoutAndLoginAs(newUser);
    assertNewUserHasIncomingRequestOfType(newUser, MessageType.REQUEST_SHARE_RECORD);
  }

  @Test
  public void userInvitedToProjectGroupReceivesProjectGroupRequest() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);
    final String username = getRandomAlphabeticString("newuser");
    final String randomEmail = username + "@x.com";
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("A Project Group");
    toPost.setPiEmail(creator.getEmail());
    toPost.setEmails(new String[] {randomEmail});

    this.mockMvc
        .perform(
            post("/projectGroup/createProjectGroup")
                .content(JacksonUtil.toJson(toPost))
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .principal(creator::getUsername))
        .andExpect(status().isOk());
    RSpaceTestUtils.logout();
    User newUser = followSignupWorkflow(username, randomEmail);
    logoutAndLoginAs(newUser);

    assertNewUserHasIncomingRequestOfType(newUser, MessageType.REQUEST_JOIN_PROJECT_GROUP);
  }

  private void inviteNewUserShareRecord(
      User creator, String randomEmail, String permission, StructuredDocument doc) {

    ShareConfigCommand content = new ShareConfigCommand();
    Long[] idToShare = new Long[1];
    idToShare[0] = doc.getId();
    ShareConfigElement[] values = new ShareConfigElement[1];
    values[0] = new ShareConfigElement();
    values[0].setEmail(randomEmail);
    values[0].setOperation(permission);

    content.setIdsToShare(idToShare);
    content.setValues(values);

    AjaxReturnObject<SharingResult> result =
        cloudController.shareRecord(content, creator::getUsername, mockRequest);

    assertNotNull(result.getData());
  }

  /** */
  public static final MediaType APPLICATION_JSON_UTF8 =
      new MediaType(
          MediaType.APPLICATION_JSON.getType(),
          MediaType.APPLICATION_JSON.getSubtype(),
          StandardCharsets.UTF_8);

  public static byte[] convertObjectToJsonBytes(Object object) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsBytes(object);
  }

  private void inviteNewUserBePIOfNewLabGroupGroup(
      User creator, String randomEmail, User existingUserToBeInGroup) throws Exception {
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(randomEmail);
    toPost.setEmails(new String[] {existingUserToBeInGroup.getEmail()});
    this.mockMvc
        .perform(
            post("/cloud/createCloudGroup2")
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(JacksonUtil.toJson(toPost))
                .principal(creator::getUsername))
        .andExpect(status().isOk());
  }

  private User followSignupWorkflow(final String username, final String randomEmail)
      throws Exception {
    // as a side-effect, a token is created.
    final TokenBasedVerification token = getTokenForEmail(randomEmail);
    assertTokenGeneratedOK(token);

    // now we'll simulate the recipient clicking on the acceptance link
    clickonSignupLink(token);

    // now they signup using their original email, and token:
    doValidSignup(username, randomEmail, token);
    // now lets assert the side-effects:
    User newUser = assertUserActivated(randomEmail, token);
    // token link has now expired and redirects to a different view
    assertTokenExpired(token);
    return newUser;
  }

  private void clickonSignupLink(final TokenBasedVerification token) throws Exception {
    this.mockMvc
        .perform(get(SignupController.SIGNUP_URL).param("token", token.getToken()))
        .andExpect(view().name("signup"));
  }

  private void assertTokenExpired(final TokenBasedVerification token) throws Exception {
    this.mockMvc
        .perform(get(SignupController.SIGNUP_URL).param("token", token.getToken()))
        .andExpect(view().name(SignupController.CLOUD_SIGNUP_ACCOUNT_ACTIVATION_FAIL_URL));
  }

  private void doValidSignup(
      final String username, final String randomEmail, final TokenBasedVerification token)
      throws Exception {
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("username", username)
                .param("email", randomEmail)
                .param("password", TESTPASSWD)
                .param("confirmPassword", TESTPASSWD)
                .param("firstName", "first")
                .param("lastName", "surname")
                .param("affiliation", "any")
                .param("token", token.getToken()))
        .andExpect(model().hasNoErrors())
        .andExpect(
            view().name(CommunityPostSignupVerification.CLOUD_SIGNUP_ACCOUNT_ACTIVATION_COMPLETE));
  }

  private User assertUserActivated(final String randomEmail, final TokenBasedVerification token) {
    // 1. Only 1 user is in DB with email
    List<User> created = userMgr.getUserByEmail(randomEmail);
    assertEquals(1, created.size());
    User newUser = created.get(0);
    assertUserStateOK(newUser);
    // check that the token is inactivated
    TokenBasedVerification updatedtoken = userMgr.getUserVerificationToken(token.getToken());
    assertTokenIsInactivated(updatedtoken);
    return newUser;
  }

  private void assertNewUserHasIncomingRequestOfType(User newUser, MessageType type) {
    ISearchResults<MessageOrRequest> messages =
        communicationMgr.getActiveMessagesAndRequestsForUserTarget(
            newUser.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    MessageOrRequest msg = messages.getFirstResult();
    assertNotNull(msg);
    assertEquals(type, msg.getMessageType());
  }

  private void assertTokenIsInactivated(TokenBasedVerification updatedtoken) {
    assertTrue(updatedtoken.isResetCompleted());
    assertFalse(
        updatedtoken.isValidLink(
            updatedtoken.getToken(), TokenBasedVerificationType.VERIFIED_SIGNUP));
  }

  private void assertUserStateOK(User newUser) {
    assertTrue(newUser.isEnabled());
    assertFalse(newUser.isTempAccount());
    assertFalse(newUser.isAccountLocked());
  }

  private void inviteNewUserToJoinGroup(User creator, String randomEmail) throws Exception {
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(creator.getEmail());
    toPost.setEmails(new String[] {randomEmail});
    this.mockMvc
        .perform(
            post("/cloud/createCloudGroup2")
                .content(JacksonUtil.toJson(toPost))
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .principal(creator::getUsername))
        .andExpect(status().isOk());
    User temp = userMgr.searchUsers(randomEmail).get(0);
    assertTrue(temp.isTempAccount());
  }

  private void assertTokenGeneratedOK(TokenBasedVerification token) {
    assertNotNull(token);
    assertEquals(TokenBasedVerificationType.VERIFIED_SIGNUP, token.getVerificationType());
    assertTrue(token.isValidLink(token.getToken(), TokenBasedVerificationType.VERIFIED_SIGNUP));
  }

  private TokenBasedVerification getTokenForEmail(String randomEmail) {
    openTransaction();
    TokenBasedVerification rc =
        (TokenBasedVerification)
            sessionFactory
                .getCurrentSession()
                .createCriteria(TokenBasedVerification.class)
                .add(Restrictions.eq("email", randomEmail))
                .uniqueResult();
    commitTransaction();
    return rc;
  }
}

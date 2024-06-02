package com.researchspace.webapp.controller.cloud;

import static com.researchspace.core.util.JacksonUtil.toJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.cloud.impl.CommunityPostSignupVerification;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestGroup;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.MVCTestBase;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
@CommunityTestContext
public class CommunityControllerMVCIT extends MVCTestBase {

  private @Autowired RSCommunityController rsCommunityController;
  private HttpServletRequest mockRequest;

  @Mock CommunityPostSignupVerification postSignupVerification;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    mockRequest = new MockHttpServletRequest();
    assertTrue(propertyHolder.isCloud());

    MockitoAnnotations.openMocks(this);
    rsCommunityController.setPostUserSignup(postSignupVerification);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void createCloudGroupWithExistingUsersCase1() throws Exception {

    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    User existingUser1 =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    User existingUser2 =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    initUsers(existingUser1, existingUser2, creator);
    logoutAndLoginAs(creator);
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(creator.getEmail());
    toPost.setEmails(new String[] {existingUser1.getEmail(), existingUser2.getEmail()});

    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/createCloudGroup2")
                    .content(toJson(toPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(creator::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));

    assertEquals(1, getActiveRequestCountForUser(existingUser1));
    assertEquals(1, getActiveRequestCountForUser(existingUser2));

    logoutAndLoginAs(existingUser1);
    acceptFirstRequest(existingUser1);
    assertEquals(0, getActiveRequestCountForUser(existingUser1));
  }

  @Test
  public void searchForUsersGeneratesErrorIfTermTooShort() throws Exception {
    createAndSaveUser(getRandomAlphabeticString(""));
    MvcResult res =
        this.mockMvc
            .perform(get("/cloud/ajax/searchPublicUserInfoList").param("term", "t1"))
            .andReturn();
    Map data = parseJSONObjectFromResponseStream(res);
    assertTrue(data.containsKey("errorMsg"));
  }

  @Test
  public void createCloudGroupWithNonExistingUsersCase1() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(creator.getEmail());
    toPost.setEmails(new String[] {"NewTempUser@mail.com"});
    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/createCloudGroup2")
                    .content(toJson(toPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(creator::getUsername))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));
    User temp = userMgr.searchUsers("NewTempUser@mail.com").get(0);
    assertTrue(temp.isTempAccount());
  }

  @Test
  public void createCloudGroupWithExistingPIUserCase2() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    User principalUser =
        createAndSaveUser(getRandomAlphabeticString("principalUser"), Constants.USER_ROLE);
    User existingUser =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    initUsers(principalUser, existingUser, creator);

    logoutAndLoginAs(creator);
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(principalUser.getEmail());
    toPost.setEmails(new String[] {existingUser.getEmail()});
    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/createCloudGroup2")
                    .content(toJson(toPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(creator::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));

    assertEquals(1, getActiveRequestCountForUser(principalUser));
    assertEquals(0, getActiveRequestCountForUser(existingUser));
  }

  @Test
  public void inviteUserToExistingGroup() throws Exception {
    User admin = logoutAndLoginAsSysAdmin();
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi);
    final String[] emails = {pi.getUsername() + "@x.com"};

    Group grp = createGroupForUsers(admin, pi.getUsername(), "", pi);
    logoutAndLoginAs(pi);
    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/inviteCloudUser")
                    .param("groupId", grp.getId() + "")
                    .param("emails[]", emails)
                    .principal(new MockPrincipal(pi.getUsername())))
            .andReturn();
    Map results = parseJSONObjectFromResponseStream(result);
    assertNotNull(results.get("data"));
  }

  @Test
  public void createCloudGroupWithNonExistingPIUserCase2() throws Exception {

    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);

    User existingUser =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);

    initUsers(creator);
    logoutAndLoginAs(creator);
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail("NewTempPIUser@mail.com");
    toPost.setEmails(new String[] {existingUser.getEmail()});
    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/createCloudGroup2")
                    .content(toJson(toPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(creator::getUsername))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));
    User temp = userMgr.searchUsers("NewTempPIUser@mail.com").get(0);
    assertTrue(temp.isTempAccount());
  }

  @Test
  public void shareRecordWithUsers() throws Exception {

    User creator = createAndSaveUser(getRandomAlphabeticString("creator"), Constants.USER_ROLE);
    User existingUser1 =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    User existingUser2 =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    initUsers(existingUser1, existingUser2);

    Folder rootFolder = initUser(creator);
    logoutAndLoginAs(creator);
    RSForm t = createAnyForm(creator);
    StructuredDocument doc = createDocumentInFolder(rootFolder, t, creator);

    inviteUserShareRecord(creator, existingUser1.getEmail(), "read", doc);
    inviteUserShareRecord(creator, existingUser2.getEmail(), "write", doc);

    RSpaceTestUtils.logout();

    assertEquals(1, getActiveRequestCountForUser(existingUser1));
    assertEquals(1, getActiveRequestCountForUser(existingUser2));

    // existingUser1 will accept the request.
    logoutAndLoginAs(existingUser1);
    acceptFirstRequest(existingUser1);
    assertEquals(0, getActiveRequestCountForUser(existingUser1));
    assertDocumentSharedConsequences(
        creator, doc, TransformerUtils.toSet(existingUser1), PermissionType.READ, true);

    // existingUser2 will reject the request.
    logoutAndLoginAs(existingUser2);
    rejectFirstRequest(existingUser2);
    assertEquals(0, getActiveRequestCountForUser(existingUser2));
    assertDocumentSharedConsequences(
        creator, doc, TransformerUtils.toSet(existingUser2), PermissionType.READ, false);
  }

  private void rejectFirstRequest(User existingUser2) {
    ISearchResults<MessageOrRequest> mors = searchDBForRequests(existingUser2);
    MessageOrRequest mor = mors.getFirstResult();
    reqUpdateMgr.updateStatus(
        existingUser2.getUsername(), CommunicationStatus.REJECTED, mor.getId(), "Rejected");
  }

  private void acceptFirstRequest(User existingUser1) {
    ISearchResults<MessageOrRequest> mors = searchDBForRequests(existingUser1);
    MessageOrRequest mor = mors.getFirstResult();
    reqUpdateMgr.updateStatus(
        existingUser1.getUsername(), CommunicationStatus.ACCEPTED, mor.getId(), "Accepted");
    reqUpdateMgr.updateStatus(
        existingUser1.getUsername(), CommunicationStatus.COMPLETED, mor.getId(), "Completed");
  }

  private void inviteUserShareRecord(
      User creator, String email, String permission, StructuredDocument doc) {

    ShareConfigCommand content = new ShareConfigCommand();
    Long[] idToShare = new Long[1];
    idToShare[0] = doc.getId();
    ShareConfigElement[] values = new ShareConfigElement[1];
    values[0] = new ShareConfigElement();
    values[0].setEmail(email);
    values[0].setOperation(permission);

    content.setIdsToShare(idToShare);
    content.setValues(values);

    AjaxReturnObject<SharingResult> result =
        rsCommunityController.shareRecord(
            content, new MockPrincipal(creator.getUsername()), mockRequest);

    assertNotNull(result);
  }

  @Test
  public void shareRecordWithExternalGroup() throws Exception {
    User creator = createAndSaveUser(getRandomAlphabeticString("creator"), Constants.USER_ROLE);

    User piUser = createAndSaveUser(getRandomAlphabeticString("piUser"), Constants.PI_ROLE);
    User existingUser1 =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    User existingUser2 =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    initUsers(piUser, existingUser1, existingUser2);
    Group externalGroup =
        createGroupForUsers(piUser, piUser.getUsername(), "", piUser, existingUser1, existingUser2);

    Folder rootFolder = initUser(creator);
    logoutAndLoginAs(creator);
    RSForm form = createAnyForm(creator);
    StructuredDocument doc = createDocumentInFolder(rootFolder, form, creator);

    inviteExternalGroupShareRecord(creator, externalGroup, "read", doc);

    RSpaceTestUtils.logout();

    assertEquals(1, getActiveRequestCountForUser(piUser));
    assertEquals(1, getActiveRequestCountForUser(existingUser1));
    assertEquals(1, getActiveRequestCountForUser(existingUser2));

    // piUser will accept the request.
    logoutAndLoginAs(piUser);
    acceptFirstRequest(piUser);
    assertEquals(0, getActiveRequestCountForUser(piUser));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, piUser));

    // existingUser1 will accept the request.
    logoutAndLoginAs(existingUser1);
    acceptFirstRequest(existingUser1);
    assertEquals(0, getActiveRequestCountForUser(existingUser1));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, existingUser1));
    assertDocumentSharedConsequences(
        creator, doc, TransformerUtils.toSet(existingUser1, piUser), PermissionType.READ, true);

    // existingUser2 will reject the request.
    logoutAndLoginAs(existingUser2);
    rejectFirstRequest(existingUser2);
    assertEquals(0, getActiveRequestCountForUser(existingUser2));
    assertDocumentSharedConsequences(
        creator, doc, TransformerUtils.toSet(existingUser2), PermissionType.READ, false);
  }

  private void inviteExternalGroupShareRecord(
      User creator, Group externalGroup, String permission, StructuredDocument doc) {

    ShareConfigCommand content = new ShareConfigCommand();
    Long[] idToShare = new Long[1];
    idToShare[0] = doc.getId();

    ShareConfigElement[] values = new ShareConfigElement[1];
    values[0] = new ShareConfigElement();
    values[0].setExternalGroupId(externalGroup.getId());
    values[0].setOperation(permission);

    content.setIdsToShare(idToShare);
    content.setValues(values);

    AjaxReturnObject<SharingResult> result =
        rsCommunityController.shareRecord(content, creator::getUsername, mockRequest);

    assertNotNull(result);
  }

  @Test
  public void checkPromoteToPiFunctionalityAfterCreatingLabGroup() throws Exception {
    TestGroup group1 = createTestGroup(2);
    TestGroup group2 = createTestGroup(2);

    User piUser1 = group1.getPi();
    User existingUser1 = group1.getUserByPrefix("u1");
    logoutAndLoginAs(piUser1);
    Group collaborationGroup = createCollabGroupBetweenGroups(group1.getGroup(), group2.getGroup());
    collaborationGroup.addMember(existingUser1, RoleInGroup.DEFAULT);
    grpMgr.saveGroup(collaborationGroup, piUser1);

    // Check existingUser1 has a default role within the collaboration group

    assertUserHasRoleInGroup(existingUser1, collaborationGroup, RoleInGroup.DEFAULT);

    logoutAndLoginAs(existingUser1);
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(existingUser1.getEmail());
    toPost.setEmails(new String[] {group1.getUserByPrefix("u2").getEmail()});
    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/createCloudGroup2")
                    .content(toJson(toPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(existingUser1::getUsername))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));

    // Now after creating a group as PI, existingUser1 becomes PI within the
    // collaboration group.
    collaborationGroup = grpMgr.getGroup(collaborationGroup.getId());
    assertUserHasRoleInGroup(existingUser1, collaborationGroup, RoleInGroup.PI);
  }

  private void assertUserHasRoleInGroup(
      User existingUser1, Group collaborationGroup, RoleInGroup roleInGroup) {
    Set<UserGroup> ugs = collaborationGroup.getUserGroups();
    for (UserGroup ug : ugs) {
      if (ug.getUser().equals(existingUser1)) {
        assertTrue(ug.getRoleInGroup().equals(roleInGroup));
      }
    }
  }

  @Test
  public void creatingAnEmptyLabGroup() throws Exception {

    User existingUser1 =
        createAndSaveUser(getRandomAlphabeticString("existingUser1"), Constants.USER_ROLE);
    logoutAndLoginAs(existingUser1);
    CreateCloudGroup toPost = new CreateCloudGroup();
    toPost.setGroupName("NewGroup");
    toPost.setPiEmail(existingUser1.getEmail());
    toPost.setEmails(new String[] {});
    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/createCloudGroup2")
                    .content(toJson(toPost))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(existingUser1::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));
  }

  @Test
  public void resendingVerificationEmailWithWrongEmail() throws Exception {
    // Create a non initialized user that is locked
    createUserAwaitingEmailConfirmation("notActivatedUser");

    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/resendConfirmationEmail/resend")
                    .param("email", "wrong_email@wrong_email.abc"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    verify(postSignupVerification, never()).postUserCreate(any(), any(), any());

    assertEquals(
        result.getResponse().getRedirectedUrl(), "/cloud/resendConfirmationEmail/resendFailure");
  }

  @Test
  public void resendingVerificationEmailHappyCase() throws Exception {
    // Create a non initialized user that is locked
    User notActivatedUser = createUserAwaitingEmailConfirmation("notActivatedUser1");

    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/resendConfirmationEmail/resend")
                    .param("email", notActivatedUser.getEmail()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    verify(postSignupVerification, times(1)).postUserCreate(eq(notActivatedUser), any(), any());

    assertEquals(
        result.getResponse().getRedirectedUrl(), "/cloud/resendConfirmationEmail/resendSuccess");
  }

  @Test
  public void resendingVerificationEmailUserAlreadyVerified() throws Exception {
    // Create a verified and initialized user
    User activatedUser =
        createAndSaveUser(getRandomAlphabeticString("existingUser1"), Constants.USER_ROLE);

    MvcResult result =
        this.mockMvc
            .perform(
                post("/cloud/resendConfirmationEmail/resend")
                    .param("email", activatedUser.getEmail()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    verify(postSignupVerification, never()).postUserCreate(any(), any(), any());

    assertEquals(
        result.getResponse().getRedirectedUrl(), "/cloud/resendConfirmationEmail/resendFailure");
  }

  @Test
  public void openingAwaitingEmailVerificationPage() throws Exception {
    // Create a verified and initialized user
    User activatedUser =
        createAndSaveUser(getRandomAlphabeticString("existingUser1"), Constants.USER_ROLE);

    this.mockMvc
        .perform(
            get("/cloud/resendConfirmationEmail/awaitingEmailConfirmation")
                .param("email", activatedUser.getEmail()))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void selfRemoval_RSPAC1662() throws Exception {
    TestGroup tg = createTestGroup(2);
    logoutAndLoginAs(tg.u1());
    MvcResult result =
        this.mockMvc
            .perform(
                post("/groups/admin/removeSelf/{groupId}", tg.getGroup().getId() + "")
                    .principal(() -> tg.u1().getUsername()))
            .andReturn();
    assertTrue(getFromJsonAjaxReturnObject(result, Boolean.class));
    Group grp = grpMgr.getGroup(tg.getGroup().getId());
    assertFalse(grp.getMembers().contains(tg.u1()));

    // now set group profile to be private:
    logoutAndLoginAs(tg.getPi());
    grp.setPrivateProfile(true);
    grp = grpMgr.saveGroup(grp, tg.getPi());

    // u2 removed returns false
    logoutAndLoginAs(tg.u2());
    MvcResult result2 =
        this.mockMvc
            .perform(
                post("/groups/admin/removeSelf/{groupId}", tg.getGroup().getId() + "")
                    .principal(() -> tg.u2().getUsername()))
            .andReturn();
    assertFalse(getFromJsonAjaxReturnObject(result2, Boolean.class));
  }

  private User createUserAwaitingEmailConfirmation(String username) {
    // Create a non initialized user that is locked
    User notActivatedUser =
        createAndSaveUser(getRandomAlphabeticString(username), Constants.USER_ROLE);
    notActivatedUser.setAccountLocked(false);
    notActivatedUser.setEnabled(false);
    userMgr.saveUser(notActivatedUser);
    assertTrue(notActivatedUser.isAccountAwaitingEmailConfirmation());
    return notActivatedUser;
  }
}

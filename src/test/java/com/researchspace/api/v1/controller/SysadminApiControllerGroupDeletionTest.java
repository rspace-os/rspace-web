package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.DateUtil.localDateToDateUTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.GroupManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.testutils.MockAndStubUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.time.LocalDate;
import javax.servlet.ServletRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Spring transactional integration tests for the sysadmin "delete group" API endpoint ({@link
 * SysadminApiController#deleteGroupIfNoLoginInPastYear(ServletRequest, Long, User)}).
 *
 * <p>Each test sets up a Group of one specific {@link GroupType} (with at least two members and a
 * PI where the type requires one), invokes the endpoint as a sysadmin, and asserts that the
 * underlying {@link Group} row has been removed via {@link GroupManager#getGroup(Long)}.
 */
public class SysadminApiControllerGroupDeletionTest extends SpringTransactionalTest {

  @Autowired private SysadminApiController sysadminApiController;
  @Autowired private SystemPropertyManager systemPropertyManager;
  @Autowired private SharingHandler sharingHandler;
  @Autowired private WhiteListIPChecker originalIpChecker;

  private User sysadmin;
  private MockHttpServletRequest request;
  private WhiteListIPChecker mockIpChecker;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    request = new MockHttpServletRequest();
    sysadmin = logoutAndLoginAsSysAdmin();
    mockIpChecker = mock(WhiteListIPChecker.class);
    when(mockIpChecker.isRequestWhitelisted(any(), any(User.class), any(Logger.class)))
        .thenReturn(true);
    ReflectionTestUtils.setField(sysadminApiController, "ipWhiteListChecker", mockIpChecker);
  }

  @After
  public void tearDown() throws Exception {
    ReflectionTestUtils.setField(sysadminApiController, "ipWhiteListChecker", originalIpChecker);
    super.tearDown();
  }

  @Test
  public void sysadminCanDeleteLabGroup() {
    TestGroup tg = createTestGroup(2);
    Group labGroup = tg.getGroup();
    assertEquals(GroupType.LAB_GROUP, labGroup.getGroupType());
    assertHasAtLeastTwoMembersAndPi(labGroup);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, labGroup.getId(), sysadmin);

    assertGroupDeleted(labGroup.getId());
  }

  @Test
  public void sysadminCanDeleteSelfServiceLabGroup() {
    TestGroup tg = createTestGroup(2);
    Group selfServiceLabGroup = tg.getGroup();
    selfServiceLabGroup.setSelfService(true);
    grpdao.save(selfServiceLabGroup);
    assertTrue(selfServiceLabGroup.isSelfService());
    assertEquals(GroupType.LAB_GROUP, selfServiceLabGroup.getGroupType());
    assertHasAtLeastTwoMembersAndPi(selfServiceLabGroup);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(
        request, selfServiceLabGroup.getId(), sysadmin);

    assertGroupDeleted(selfServiceLabGroup.getId());
  }

  @Test
  public void sysadminCanDeleteProjectGroup() throws Exception {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("powner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("pmember"));
    initialiseContentWithEmptyContent(owner, member);
    Group projectGroup = createProjectGroupForUsers(owner, "", "", owner, member);
    assertEquals(GroupType.PROJECT_GROUP, projectGroup.getGroupType());
    assertTrue("project group should have >= 2 members", projectGroup.getMembers().size() >= 2);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, projectGroup.getId(), sysadmin);

    assertGroupDeleted(projectGroup.getId());
  }

  @Test
  public void sysadminCanDeleteCollaborationGroup() {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    assertEquals(GroupType.COLLABORATION_GROUP, collabGroup.getGroupType());
    assertTrue("collab group should have at least 2 members", collabGroup.getMembers().size() >= 2);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, collabGroup.getId(), sysadmin);

    assertGroupDeleted(collabGroup.getId());
    // the constituent lab groups should still exist
    assertNotNull(grpMgr.getGroup(tg1.getGroup().getId()));
    assertNotNull(grpMgr.getGroup(tg2.getGroup().getId()));
  }

  @Test
  public void sysadminCanDeleteLabGroupWhenMembersHaveSharedData() {
    TestGroup tg = createTestGroup(2);
    Group labGroup = tg.getGroup();
    User sharer = tg.u1();
    logoutAndLoginAs(sharer);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(sharer, "shared content");
    shareRecordWithGroup(sharer, labGroup, doc);
    assertFalse(
        "precondition: doc should be shared with group",
        sharingMgr.getSharedRecordsForUserAndGroup(sharer, labGroup).isEmpty());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, labGroup.getId(), sysadmin);

    assertGroupDeleted(labGroup.getId());
  }

  @Test
  public void sysadminCanDeleteSelfServiceLabGroupWhenMembersHaveSharedData() {
    TestGroup tg = createTestGroup(2);
    Group labGroup = tg.getGroup();
    labGroup.setSelfService(true);
    grpdao.save(labGroup);
    User sharer = tg.u1();
    logoutAndLoginAs(sharer);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(sharer, "selfservice shared");
    shareRecordWithGroup(sharer, labGroup, doc);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, labGroup.getId(), sysadmin);

    assertGroupDeleted(labGroup.getId());
  }

  @Test
  public void sysadminCanDeleteProjectGroupWhenMembersHaveSharedData() throws Exception {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("powner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("pmember"));
    initialiseContentWithEmptyContent(owner, member);
    Group projectGroup = createProjectGroupForUsers(owner, "", "", owner, member);
    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "project shared");
    Folder communalFolder = folderMgr.getFolder(projectGroup.getCommunalGroupFolderId(), owner);
    sharingHandler.shareIntoSharedFolderOrNotebook(owner, communalFolder, doc.getId(), null);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, projectGroup.getId(), sysadmin);

    assertGroupDeleted(projectGroup.getId());
  }

  @Test
  public void sysadminCanDeleteCollaborationGroupWhenMembersHaveSharedData() {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());

    User sharer = tg1.getPi();
    logoutAndLoginAs(sharer);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(sharer, "collab shared");
    shareRecordWithGroup(sharer, collabGroup, doc);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, collabGroup.getId(), sysadmin);

    assertGroupDeleted(collabGroup.getId());
  }

  @Test
  public void sysadminCanDeleteLabGroupWhenPiCanSeeAllMembersWork() {
    systemPropertyManager.save(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP,
        HierarchicalPermission.ALLOWED.name(),
        sysadmin);
    TestGroup tg = createTestGroup(2);
    Group labGroup = tg.getGroup();
    User pi = tg.getPi();
    logoutAndLoginAs(pi);
    grpMgr.authorizePIToEditAll(labGroup.getId(), pi, true);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, labGroup.getId(), sysadmin);

    assertGroupDeleted(labGroup.getId());
  }

  @Test
  public void sysadminCanDeleteCollaborationGroupWhenPiCanSeeAllMembersWork() {
    systemPropertyManager.save(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP,
        HierarchicalPermission.ALLOWED.name(),
        sysadmin);
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg1.getPi());
    grpMgr.authorizePIToEditAll(tg1.getGroup().getId(), tg1.getPi(), true);
    logoutAndLoginAs(tg2.getPi());
    grpMgr.authorizePIToEditAll(tg2.getGroup().getId(), tg2.getPi(), true);

    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, collabGroup.getId(), sysadmin);

    assertGroupDeleted(collabGroup.getId());
    // and the underlying lab groups still exist
    assertNotNull(grpMgr.getGroup(tg1.getGroup().getId()));
    assertNotNull(grpMgr.getGroup(tg2.getGroup().getId()));
  }

  // ---- lastLogin guard ----------------------------------------------------

  @Test
  public void
      sysadminCannotDeleteGroupIfNoLoginInPastYearWhenAMemberHasLoggedInWithinTheLastYear() {
    TestGroup tg = createTestGroup(2);
    Group labGroup = tg.getGroup();
    User recentlyActive = tg.u1();
    setLastLogin(recentlyActive, LocalDate.now().minusDays(7));

    logoutAndLoginAsSysAdmin();
    try {
      sysadminApiController.deleteGroupIfNoLoginInPastYear(request, labGroup.getId(), sysadmin);
      fail(
          "expected IllegalArgumentException because member "
              + recentlyActive.getUsername()
              + " has lastLogin within the last year, but no exception was thrown");
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage(), containsString("logged in"));
    }
    assertTrue(
        "Group " + labGroup.getId() + " should NOT have been deleted",
        grpdao.exists(labGroup.getId()));
  }

  @Test
  public void sysadminCanDeleteGroupIfNoLoginInPastYearWhenAllMembersLastLoginIsOlderThanOneYear() {
    TestGroup tg = createTestGroup(2);
    Group labGroup = tg.getGroup();
    for (User u : tg.getUnameToUser().values()) {
      setLastLogin(u, LocalDate.now().minusYears(2));
    }

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteGroupIfNoLoginInPastYear(request, labGroup.getId(), sysadmin);

    assertGroupDeleted(labGroup.getId());
  }

  private void setLastLogin(User user, LocalDate date) {
    User reloaded = userMgr.get(user.getId());
    MockAndStubUtils.modifyDateField(
        reloaded, localDateToDateUTC(date), User.class, "setLastLogin");
    userMgr.save(reloaded);
  }

  private void assertGroupDeleted(Long groupId) {
    assertFalse(
        "Group " + groupId + " should have been deleted but was still present",
        grpdao.exists(groupId));
  }

  private void assertHasAtLeastTwoMembersAndPi(Group group) {
    assertTrue("group should have at least 2 members", group.getMembers().size() >= 2);
    assertFalse("lab/collab group should have a PI", group.getPiusers().isEmpty());
  }
}

package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.DateUtil.localDateToDateUTC;
import static com.researchspace.testutils.MockAndStubUtils.modifyUserCreationDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.testutils.MockAndStubUtils;
import com.researchspace.testutils.TestGroup;
import com.researchspace.webapp.controller.UserExportHandler;
import java.time.LocalDate;
import java.util.Date;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Spring transactional integration tests for the sysadmin user-deletion endpoints, covering only
 * the scenarios NOT already exercised by lower-tier tests:
 *
 * <ul>
 *   <li>PROJECT_GROUP owner deletion is rejected — exercises the {@code
 *       group.getOwner().equals(toDelete)} branch of {@code validateGroupMembershipCriteria}, which
 *       no other test reaches (lab-group rejections fire on the earlier {@code isOnlyGroupPi}
 *       branch, covered by {@code UserDeletionManagerTestIT.deletePi}).
 *   <li>COLLABORATION_GROUP regular-member deletion succeeds — exercises the multi-group iteration
 *       in {@code strictPreserveDataForGroup}, with the user-to-delete belonging to two groups.
 *   <li>PROJECT_GROUP regular-member deletion succeeds — distinct membership semantics from
 *       lab-group regular-member deletion already covered by {@code
 *       SysadminUsersAPIControllerMVCIT.deleteRegularUserInGroup}.
 * </ul>
 *
 * <p>See also: {@code UserDeletionManagerTest}, {@code UserDeletionManagerTestIT}, and {@code
 * SysadminUsersAPIControllerMVCIT} for the service- and HTTP-tier coverage this file deliberately
 * does not duplicate.
 */
public class SysadminApiControllerUserDeletionTest extends SysadminApiControllerTestSupport {

  @Autowired private UserExportHandler originalUserExportHandler;

  private UserExportHandler mockUserExportHandler;

  @Before
  public void mockUserExport() throws Exception {
    // Mock the user export so the non-temp deletion path doesn't try to write to the filestore.
    mockUserExportHandler = mock(UserExportHandler.class);
    @SuppressWarnings("unchecked")
    Future<ArchiveResult> fakeArchive = (Future<ArchiveResult>) mock(Future.class);
    when(fakeArchive.get()).thenReturn(new ArchiveResult());
    when(mockUserExportHandler.doUserArchive(any(), any(), any(User.class), any()))
        .thenReturn(fakeArchive);
    ReflectionTestUtils.setField(sysadminApiController, "userExportHandler", mockUserExportHandler);
  }

  @After
  public void restoreUserExport() {
    ReflectionTestUtils.setField(
        sysadminApiController, "userExportHandler", originalUserExportHandler);
  }

  @Test
  public void deleteAnyUserFailsWhenUserIsProjectGroupOwner() throws Exception {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("powner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("pmember"));
    initialiseContentWithEmptyContent(owner, member);
    Group projectGroup = createProjectGroupForUsers(owner, "", "", owner, member);
    assertNotNull(projectGroup);
    User aged = ageTwoYears(owner);

    logoutAndLoginAsSysAdmin();
    assertDeleteAnyUserRejectedWithGroupRoleMessage(aged);
  }

  @Test
  public void deleteAnyUserSucceedsForCollaborationGroupRegularMember() {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    assertNotNull(collabGroup);
    User toDelete = tg1.u1();
    ageOtherMembersLastLogin(tg1, toDelete);
    ageOtherMembersLastLogin(tg2, toDelete);
    toDelete = ageTwoYears(toDelete);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteAnyUserOlderThan1Year(
        request, twoYearsAgo(), toDelete.getId(), sysadmin);

    assertUserDeleted(toDelete);
  }

  @Test
  public void deleteAnyUserSucceedsForProjectGroupRegularMember() throws Exception {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("powner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("pmember"));
    initialiseContentWithEmptyContent(owner, member);
    Group projectGroup = createProjectGroupForUsers(owner, "", "", owner, member);
    assertNotNull(projectGroup);
    ageLastLogin(owner);
    User aged = ageTwoYears(member);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteAnyUserOlderThan1Year(
        request, twoYearsAgo(), aged.getId(), sysadmin);

    assertUserDeleted(aged);
  }

  // ---- helpers -------------------------------------------------------------

  private User ageTwoYears(User user) {
    modifyUserCreationDate(user, localDateToDateUTC(LocalDate.now().minusYears(2)));
    return userMgr.save(user);
  }

  private Date twoYearsAgo() {
    return localDateToDateUTC(LocalDate.now().minusYears(2));
  }

  /**
   * Ages every member of the test group except {@code keep} so the strict-preserve check passes.
   */
  private void ageOtherMembersLastLogin(TestGroup tg, User keep) {
    for (User u : tg.getUnameToUser().values()) {
      if (!u.equals(keep)) {
        ageLastLogin(u);
      }
    }
  }

  private void ageLastLogin(User user) {
    User reloaded = userMgr.get(user.getId());
    MockAndStubUtils.modifyUserLastLoginDate(
        reloaded, localDateToDateUTC(LocalDate.now().minusYears(2)));
    userMgr.save(reloaded);
  }

  private void assertUserDeleted(User user) {
    assertFalse(
        "user " + user.getUsername() + " (" + user.getId() + ") should have been deleted",
        userDao.exists(user.getId()));
  }

  private void assertDeleteAnyUserRejectedWithGroupRoleMessage(User toDelete) {
    try {
      sysadminApiController.deleteAnyUserOlderThan1Year(
          request, twoYearsAgo(), toDelete.getId(), sysadmin);
      fail(
          "expected IllegalArgumentException because "
              + toDelete.getUsername()
              + " holds a non-deletable group role, but no exception was thrown");
    } catch (IllegalArgumentException expected) {
      assertThat(
          expected.getMessage(),
          anyOf(
              containsString("only admin or PI"),
              containsString("owner of a labgroup"),
              containsString("only group owner")));
    }
  }
}

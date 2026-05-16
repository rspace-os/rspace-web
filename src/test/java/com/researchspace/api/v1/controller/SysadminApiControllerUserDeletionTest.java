package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.DateUtil.localDateToDateUTC;
import static com.researchspace.testutils.MockAndStubUtils.modifyUserCreationDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
import javax.servlet.ServletRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Spring transactional integration tests for the sysadmin user-deletion endpoints (both {@link
 * SysadminApiController#deleteAnyUserOlderThan1Year(ServletRequest, Date, Long, User)} and {@link
 * SysadminApiController#deleteTempUserOlderThan1Year(ServletRequest, Long, User)}).
 *
 * <p>Each test sets up a Group of one specific type with at least two members, makes the
 * user-to-delete the PI (LAB_GROUP, self-service LAB_GROUP, COLLABORATION_GROUP) or the group owner
 * (PROJECT_GROUP), then ages the user past the 1-year creation-date cut-off and calls the endpoint
 * as a sysadmin.
 *
 * <p>Expected behaviour:
 *
 * <ul>
 *   <li>The non-temp endpoint goes through {@code isUserRemovable}, which runs the group-role guard
 *       and rejects with a "only admin or PI" / "owner of a labgroup" message.
 * </ul>
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

  // ---- LAB_GROUP -----------------------------------------------------------

  @Test
  public void deleteAnyUserFailsWhenUserIsLabGroupPi() {
    TestGroup tg = createTestGroup(2);
    User pi = ageTwoYears(tg.getPi());
    Group group = tg.getGroup();
    assertTrue(
        "precondition: PI should be sole PI of the lab group", group.getPiusers().size() == 1);

    logoutAndLoginAsSysAdmin();
    assertDeleteAnyUserRejectedWithGroupRoleMessage(pi);
  }

  // ---- self-service LAB_GROUP ---------------------------------------------

  @Test
  public void deleteAnyUserFailsWhenUserIsSelfServiceLabGroupPi() {
    TestGroup tg = createTestGroup(2);
    Group group = tg.getGroup();
    group.setSelfService(true);
    grpdao.save(group);
    User pi = ageTwoYears(tg.getPi());
    assertTrue(
        "precondition: group should be self-service", grpdao.get(group.getId()).isSelfService());

    logoutAndLoginAsSysAdmin();
    assertDeleteAnyUserRejectedWithGroupRoleMessage(pi);
  }

  // ---- COLLABORATION_GROUP -------------------------------------------------

  @Test
  public void deleteAnyUserFailsWhenUserIsCollaborationGroupPi() {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    assertNotNull(collabGroup);
    User pi = ageTwoYears(tg1.getPi());

    logoutAndLoginAsSysAdmin();
    assertDeleteAnyUserRejectedWithGroupRoleMessage(pi);
  }

  // ---- PROJECT_GROUP -------------------------------------------------------

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

  // ---- successful deletion of non-PI / non-owner members ------------------

  @Test
  public void deleteAnyUserSucceedsForLabGroupRegularMember() {
    TestGroup tg = createTestGroup(2);
    ageOtherMembersLastLogin(tg, tg.u1());
    User toDelete = ageTwoYears(tg.u1());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteAnyUserOlderThan1Year(
        request, twoYearsAgo(), toDelete.getId(), sysadmin);

    assertUserDeleted(toDelete);
  }

  @Test
  public void deleteTempUserSucceedsForLabGroupRegularMember() {
    TestGroup tg = createTestGroup(2);
    User toDelete = makeTempAndAge(tg.u1());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteTempUserOlderThan1Year(request, toDelete.getId(), sysadmin);

    assertUserDeleted(toDelete);
  }

  @Test
  public void deleteAnyUserSucceedsForSelfServiceLabGroupRegularMember() {
    TestGroup tg = createTestGroup(2);
    Group group = tg.getGroup();
    group.setSelfService(true);
    grpdao.save(group);
    ageOtherMembersLastLogin(tg, tg.u1());
    User toDelete = ageTwoYears(tg.u1());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteAnyUserOlderThan1Year(
        request, twoYearsAgo(), toDelete.getId(), sysadmin);

    assertUserDeleted(toDelete);
  }

  @Test
  public void deleteTempUserSucceedsForSelfServiceLabGroupRegularMember() {
    TestGroup tg = createTestGroup(2);
    Group group = tg.getGroup();
    group.setSelfService(true);
    grpdao.save(group);
    User toDelete = makeTempAndAge(tg.u1());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteTempUserOlderThan1Year(request, toDelete.getId(), sysadmin);

    assertUserDeleted(toDelete);
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
  public void deleteTempUserSucceedsForCollaborationGroupRegularMember() {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    assertNotNull(collabGroup);
    User toDelete = makeTempAndAge(tg1.u1());

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteTempUserOlderThan1Year(request, toDelete.getId(), sysadmin);

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

  @Test
  public void deleteTempUserSucceedsForProjectGroupRegularMember() throws Exception {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("powner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("pmember"));
    initialiseContentWithEmptyContent(owner, member);
    Group projectGroup = createProjectGroupForUsers(owner, "", "", owner, member);
    assertNotNull(projectGroup);
    User toDelete = makeTempAndAge(member);

    logoutAndLoginAsSysAdmin();
    sysadminApiController.deleteTempUserOlderThan1Year(request, toDelete.getId(), sysadmin);

    assertUserDeleted(toDelete);
  }

  // ---- helpers -------------------------------------------------------------

  private User ageTwoYears(User user) {
    modifyUserCreationDate(user, localDateToDateUTC(LocalDate.now().minusYears(2)));
    return userMgr.save(user);
  }

  private Date twoYearsAgo() {
    return localDateToDateUTC(LocalDate.now().minusYears(2));
  }

  private User makeTempAndAge(User user) {
    User reloaded = userMgr.get(user.getId());
    reloaded.setTempAccount(true);
    modifyUserCreationDate(reloaded, localDateToDateUTC(LocalDate.now().minusYears(2)));
    return userMgr.save(reloaded);
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

package com.researchspace.dao;

import static org.junit.Assert.*;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.UserProfile;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.model.views.UserView;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestGroup;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

public class UserDaoTest extends BaseDaoTestCase {

  private @Autowired RoleDao rdao;
  private @Autowired GroupDao grpdao;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test(expected = DataAccessException.class)
  public void testGetUserInvalid() throws Exception {
    // should throw DataAccessException
    userDao.get(1000L);
  }

  @Test
  public void testGetUser() throws Exception {
    User user = userDao.get(-1L);

    assertNotNull(user);
    assertEquals(2, user.getRoles().size()); // pi and user
    assertTrue(user.isEnabled());
  }

  @Test
  public void testGetUserBasicInfoWithRole() throws Exception {

    List<UserRoleView> users = userDao.getUsersBasicInfoWithRoles();
    List<User> userEntities = userDao.getAll();
    // anonymous user excluded from getUsersBasicInfoWithRoles()
    assertEquals(users.size(), userEntities.size() - 1);
    assertEquals(2, actualRoleCountForUser(users, "user1a")); // pi; user + pi role
    assertEquals(1, actualRoleCountForUser(users, "user2b"));
  }

  private int actualRoleCountForUser(List<UserRoleView> users, String username) {
    return users.stream()
        .filter(view -> view.getUsername().equals(username))
        .findFirst()
        .get()
        .getRoles()
        .size();
  }

  @Test
  public void testGetUserPassword() throws Exception {
    User user = userDao.get(-1L);
    String password = userDao.getUserPassword(user.getUsername());
    assertNotNull(password);
    log.debug("password: " + password);
  }

  @Test(expected = DataAccessException.class)
  public void testUpdateUser() throws Exception {
    User user = userDao.get(-1L);
    userDao.saveUser(user);
    flush();
    user = userDao.get(-1L);
    // verify that violation occurs when adding new user with same username
    user.setId(null);
    // should throw data exception
    userDao.saveUser(user);
  }

  @Test
  public void testAddUserRole() throws Exception {
    User user = userDao.get(-1L);
    final int INITIAL_ROLE_COUNT = user.getRoles().size();

    Role role = rdao.getRoleByName(Constants.ADMIN_ROLE);
    user.addRole(role);
    userDao.saveUser(user);
    flush();

    user = userDao.get(-1L);
    assertEquals(INITIAL_ROLE_COUNT + 1, user.getRoles().size());

    // add the same role twice - should result in no additional role
    user.addRole(role);
    userDao.saveUser(user);
    flush();

    user = userDao.get(-1L);
    assertEquals("more than 2 roles", INITIAL_ROLE_COUNT + 1, user.getRoles().size());

    user.getRoles().remove(role);
    userDao.saveUser(user);
    flush();

    user = userDao.get(-1L);
    assertEquals(INITIAL_ROLE_COUNT, user.getRoles().size());
  }

  @Test(expected = DataAccessException.class)
  public void testAddAndRemoveUser() throws Exception {

    flush();
    User user = TestFactory.createAnyUser("testuser");
    Role role = rdao.getRoleByName(Constants.USER_ROLE);
    assertNotNull(role.getId());
    user.addRole(role);

    user = userDao.saveUser(user);
    assertNotNull(user.getId());
    user = userDao.get(user.getId());

    userDao.remove(user.getId());
    flush();

    // should throw DataAccessException
    userDao.get(user.getId());
  }

  @Test
  public void testPreferences() {
    User user = userDao.get(-1L);
    assertEquals(0, user.getUserPreferences().size());
    UserPreference up =
        new UserPreference(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF, user, "true");
    user.setPreference(up);
    userDao.save(user);

    User u2 = userDao.get(-1L);
    assertEquals(1, u2.getUserPreferences().size());
    assertTrue(
        u2.getUserPreferences()
            .iterator()
            .next()
            .getValue()
            .equalsIgnoreCase(Boolean.TRUE.toString()));

    // alter prefs
    up.setValue("false");
    userDao.save(u2);
    User u3 = userDao.get(-1L);
    assertEquals(1, u3.getUserPreferences().size());
    assertTrue(
        u3.getUserPreferences()
            .iterator()
            .next()
            .getValue()
            .equalsIgnoreCase(Boolean.FALSE.toString()));
  }

  @Test
  public void testUserExists() throws Exception {
    boolean b = userDao.exists(-1L);
    assertTrue(b);
  }

  @Test
  public void testUserNotExists() throws Exception {
    boolean b = userDao.exists(111L);
    assertFalse(b);
  }

  @Test
  public void testGetAllUsers() {
    int enabledB4 = userDao.getUsers().size();
    User pi1 = TestFactory.createAnyUser("pi1");
    // not retrieved
    pi1.setEnabled(true);
    userDao.save(pi1);
    assertEquals(enabledB4 + 1, userDao.getUsers().size());
    // now is retrieved
    pi1.setEnabled(false);

    userDao.save(pi1);
    assertEquals(enabledB4, userDao.getUsers().size());
  }

  @Test
  public void testGetPis() {
    // test depends on other tests for a clean set up, this addition allows the test to pass
    // standalone
    Set<User> pisAndAdminsPriorToTest = userDao.getAllGroupPis(null);
    User pi1 = createAndSaveUserIfNotExists("uniquePI", Constants.PI_ROLE);
    User u1 = createAndSaveUserIfNotExists("u1");
    User pi2 = createAndSaveUserIfNotExists("pi2", Constants.PI_ROLE);
    User u2 = createAndSaveUserIfNotExists("u2");
    // create 2 groups each with a pi and a user
    Group g1 = createGroup("g1", pi1);

    g1.addMember(pi1, RoleInGroup.PI);
    g1.addMember(u1, RoleInGroup.DEFAULT);
    Group g2 = createGroup("g2", pi2);
    g2.addMember(pi2, RoleInGroup.PI);
    g2.addMember(u2, RoleInGroup.RS_LAB_ADMIN);
    grpdao.save(g1);
    grpdao.save(g2);

    Set<User> pisAndAdmins = userDao.getAllGroupPis(null);
    assertEquals(pisAndAdminsPriorToTest.size() + 2, pisAndAdmins.size());
    assertTrue(pisAndAdmins.contains(pi2));
    assertTrue(pisAndAdmins.contains(pi1));
    assertFalse(pisAndAdmins.contains(u2));

    Set<User> matchingPisAndAdmins = userDao.getAllGroupPis("uniquePI");
    assertEquals(1, matchingPisAndAdmins.size());
    assertTrue(matchingPisAndAdmins.contains(pi1));
  }

  @Test
  public void testDBPaginatedSearch() throws Exception {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setResultsPerPage(5); //
    pgCrit.setSortOrder(SortOrder.DESC);
    pgCrit.setOrderBy("lastName");
    ISearchResults<User> results = userDao.searchUsers(pgCrit);
    // anonymous user excluded from search
    assertEquals(userDao.getAll().size() - 1, results.getTotalHits().intValue());
    // assumes we get more users than can be paginated
    assertEquals(pgCrit.getResultsPerPage().intValue(), results.getResults().size());

    pgCrit.setResultsPerPage(50000); // make sure we get all users so we get genuine first and
    // last by sort order
    ISearchResults<User> results3 = userDao.searchUsers(pgCrit);
    User first = results3.getResults().get(0);

    User last = getLastUserInResultList(pgCrit, results3);
    // swap sort order, check order is done in search
    pgCrit.setSortOrder(SortOrder.ASC);
    ISearchResults<User> results2 = userDao.searchUsers(pgCrit);
    assertEquals(last, results2.getResults().get(0));
    assertEquals(first, getLastUserInResultList(pgCrit, results2));
  }

  @Test
  public void testSaveRetrieveUserPasswordToken() throws Exception {
    TokenBasedVerification upc =
        new TokenBasedVerification("a@b.com", null, TokenBasedVerificationType.PASSWORD_CHANGE);
    upc = userDao.saveTokenBasedVerification(upc);
    assertNotNull(upc.getId());
    TokenBasedVerification retrieved = userDao.getByToken(upc.getToken());
    assertNotNull(retrieved);
    assertEquals(retrieved, upc);
    assertNull(userDao.getByToken("anytoken"));
  }

  @Test
  public void testGetSaveProfile() throws IOException {
    User pi1 = TestFactory.createAnyUser("pi1");
    userDao.saveUser(pi1);
    UserProfile profile = userDao.getUserProfileByUser(pi1);
    assertNull(profile); // not set yet

    UserProfile defaultProfile = new UserProfile(pi1);
    defaultProfile = userDao.saveUserProfile(defaultProfile);
    assertNotNull(defaultProfile.getId());

    byte[] image = getAnyImageByteArray();
    ImageBlob blob = new ImageBlob(image);
    defaultProfile.setProfilePicture(blob);
    defaultProfile = userDao.saveUserProfile(defaultProfile);
    assertNotNull(defaultProfile.getProfilePicture());

    assertEquals(defaultProfile, userDao.getUserProfileById(defaultProfile.getId()));
    final long UNKNOWNID = -1234;
    assertNull(userDao.getUserProfileById(UNKNOWNID));
  }

  /**
   * Gets an image byte array
   *
   * @return
   * @throws IOException
   */
  protected byte[] getAnyImageByteArray() throws IOException {
    return IOUtils.toByteArray(
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("tester.png"));
  }

  /**
   * Gets total count of users with the specified role
   *
   * @param role
   * @return
   */
  private int getNumUsersInRole(Role role) {
    return userDao
        .listUsersByRole(role, PaginationCriteria.createDefaultForClass(User.class))
        .getTotalHits()
        .intValue();
  }

  @Test
  public void testListUsersByRole() {
    int B4count_admins = getNumUsersInRole(Role.ADMIN_ROLE);
    int B4count_users = getNumUsersInRole(Role.USER_ROLE);
    createAndSaveAdminUser();
    assertEquals(B4count_admins + 1, getNumUsersInRole(Role.ADMIN_ROLE));
    // Add another user in default role
    createAndSaveUserIfNotExists("useralpr");
    // Admin count is unaffected
    assertEquals(B4count_admins + 1, getNumUsersInRole(Role.ADMIN_ROLE));
    // User count is incremented
    assertEquals(B4count_users + 1, getNumUsersInRole(Role.USER_ROLE));
  }

  @Test
  public void testListAvailableAdmins() {
    int B4count_admins = userDao.getAvailableAdminsForCommunity().size();
    User newAdmin = createAndSaveAdminUser();

    List<User> availableAdmins = userDao.getAvailableAdminsForCommunity();
    assertEquals(B4count_admins + 1, availableAdmins.size());
    assertTrue(availableAdmins.contains(newAdmin));
    // now disable admin account, should not be retrieved
    newAdmin.setEnabled(false);
    userDao.save(newAdmin);
    assertEquals(B4count_admins, getAvailableAdminCount());

    // now lock admin account, should not be retrieved
    newAdmin.setEnabled(true);
    newAdmin.setAccountLocked(true);
    userDao.save(newAdmin);
    assertEquals(B4count_admins, getAvailableAdminCount());

    // now unlock user
    newAdmin.setAccountLocked(false);
    userDao.save(newAdmin);

    // now add admin to a community
    createAndSaveCommunity(newAdmin, "commid1");
    // admins
    List<User> availableAdminsAfter = userDao.getAvailableAdminsForCommunity();
    assertEquals(B4count_admins, getAvailableAdminCount());
    assertFalse(availableAdminsAfter.contains(newAdmin));
  }

  int getAvailableAdminCount() {
    List<User> availableAdmins = userDao.getAvailableAdminsForCommunity();
    return availableAdmins.size();
  }

  private User getLastUserInResultList(
      PaginationCriteria<User> pgCrit, ISearchResults<User> results) {
    return results.getResults().get(results.getHits() - 1);
  }

  @Test
  public void testListUsersByCommunity() {
    TestGroup tg = createTestGroup(3);
    TestGroup tg2 = createTestGroup(4);

    User pi = createAndSaveAPi();
    User u2 = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, u2);
    Group g1 = createGroup("g1", pi);
    addUsersToGroup(pi, g1, u2);
    User admin = createAndSaveAdminUser();
    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("newCommunity"));
    logoutAndLoginAs(admin);
    communityMgr.addGroupToCommunity(g1.getId(), comm.getId(), admin);
    communityMgr.addGroupToCommunity(tg.getGroup().getId(), comm.getId(), admin);
    communityMgr.addGroupToCommunity(tg2.getGroup().getId(), comm.getId(), admin);

    User outsidePI = createAndSaveAPi();
    initialiseContentWithEmptyContent(outsidePI);
    assertFalse(userDao.isUserInAdminsCommunity(outsidePI.getUsername(), comm.getId()));

    Group otherGrp = createGroup("other", outsidePI);
    addUsersToGroup(outsidePI, otherGrp);

    RSpaceTestUtils.logout();
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setResultsPerPage(20);
    ISearchResults<User> results = userDao.listUsersInCommunity(comm.getId(), pgCrit);
    final int EXPECTED_HITS = 11;
    assertEquals(EXPECTED_HITS, results.getTotalHits().longValue());
    assertEquals(EXPECTED_HITS, results.getResults().size());
    // no duplicates
    assertEquals(EXPECTED_HITS, new HashSet<>(results.getResults()).size());
    assertTrue(results.getResults().contains(pi));
    assertTrue(results.getResults().contains(tg.getPi()));
    assertTrue(results.getResults().contains(tg2.getPi()));
    assertTrue(results.getResults().contains(u2));

    assertTrue(userDao.isUserInAdminsCommunity(pi.getUsername(), comm.getId()));

    assertEquals(EXPECTED_HITS, userDao.getUserIdsInAdminsCommunity(admin).size());

    UserSearchCriteria sc = new UserSearchCriteria();
    sc.setAllFields(tg.getUserByPrefix("u1").getUsername());
    pgCrit.setSearchCriteria(sc);
    ISearchResults<User> filtered = userDao.listUsersInCommunity(comm.getId(), pgCrit);
    assertEquals(1, filtered.getTotalHits().intValue());
    assertEquals(1, filtered.getResults().size());

    sc.setAllFields(tg.getUserByPrefix("u1").getUsername().substring(1, 7));
    ISearchResults<User> filtered2 = userDao.listUsersInCommunity(comm.getId(), pgCrit);
    assertEquals(1, filtered2.getTotalHits().intValue());
    assertEquals(1, filtered2.getResults().size());

    // test ordering
    pgCrit.setSortOrder(SortOrder.DESC);
    pgCrit.setOrderBy("email");
    pgCrit.setSearchCriteria(null);
    ISearchResults<User> orderedByEmailDesc = userDao.listUsersInCommunity(comm.getId(), pgCrit);
    List<String> emails =
        orderedByEmailDesc.getResults().stream().map(User::getEmail).collect(Collectors.toList());
    assertStringsInDescendingOrder(emails);
    pgCrit.setSortOrder(SortOrder.ASC);
    ISearchResults<User> orderedByEmailAsc = userDao.listUsersInCommunity(comm.getId(), pgCrit);
    List<String> emailsAsc =
        orderedByEmailAsc.getResults().stream().map(User::getEmail).collect(Collectors.toList());
    assertStringsInAscendingOrder(emailsAsc);
  }

  void assertStringsInDescendingOrder(List<String> strings) {
    for (int i = 0; i < strings.size() - 1; i++) {
      assertFalse(strings.get(i).toLowerCase().compareTo(strings.get(i + 1).toLowerCase()) < 0);
    }
  }

  void assertStringsInAscendingOrder(List<String> strings) {
    for (int i = 0; i < strings.size() - 1; i++) {
      assertFalse(strings.get(i).toLowerCase().compareTo(strings.get(i + 1).toLowerCase()) > 0);
    }
  }

  @Test
  public void testGetUserStatistics() throws IllegalAddChildOperation {
    final int ANY_TIME_RANGE = 7;
    UserStatistics b4Stats = userDao.getUserStats(ANY_TIME_RANGE);

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("zzuser12345"));
    UserStatistics afterCreationStats = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalUsers() + 1, afterCreationStats.getTotalUsers());
    assertEquals(b4Stats.getTotalEnabledUsers() + 1, afterCreationStats.getTotalEnabledUsers());
    assertEquals(b4Stats.getTotalLockedUsers(), afterCreationStats.getTotalLockedUsers());

    // set user 'active'
    user.setLastLogin(new Date());
    userDao.save(user);
    UserStatistics afterLoginStats = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalActiveUsers() + 1, afterLoginStats.getTotalActiveUsers());

    // disable user, check counts
    user.setEnabled(false);
    userDao.save(user);
    UserStatistics afterDisableStats = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalEnabledUsers(), afterDisableStats.getTotalEnabledUsers());
    assertEquals(b4Stats.getTotalUsers() + 1, afterDisableStats.getTotalUsers());

    // reenable user, check counts
    user.setEnabled(true);
    userDao.save(user);
    UserStatistics afterReenableStats = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalEnabledUsers() + 1, afterReenableStats.getTotalEnabledUsers());
    assertEquals(b4Stats.getTotalUsers() + 1, afterReenableStats.getTotalUsers());

    // check admin users
    createAndSaveUserIfNotExists("sysadminXX", Constants.SYSADMIN_ROLE);
    UserStatistics stats = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalEnabledSysAdmins() + 1, stats.getTotalEnabledSysAdmins());

    User rspaceAdmin = createAndSaveUserIfNotExists("adminXX", Constants.ADMIN_ROLE);
    UserStatistics stats2 = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalEnabledRSpaceAdmins() + 1, stats2.getTotalEnabledRSpaceAdmins());
    // disable am
    rspaceAdmin.setEnabled(false);
    userDao.save(user);
    stats2 = userDao.getUserStats(ANY_TIME_RANGE);
    assertEquals(b4Stats.getTotalEnabledRSpaceAdmins(), stats2.getTotalEnabledRSpaceAdmins());
  }

  @Test
  public void testSearchUsersByTerm() throws IllegalAddChildOperation {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("zzuser"));
    user.setFirstName(getRandomAlphabeticString("ooooaaaggg"));
    user.setLastName(getRandomAlphabeticString("ooooaaaggg"));
    user.setEmail(getRandomAlphabeticString("ooooaaaggg") + "@rspace.com");
    // check user can be identified
    List<User> foundByFirstName = userDao.searchUsers(user.getFirstName().substring(1, 16));
    assertEquals(1, foundByFirstName.size());
    assertEquals(user, foundByFirstName.get(0));
    List<User> foundByLastName = userDao.searchUsers(user.getLastName().substring(1, 16));
    assertEquals(1, foundByLastName.size());
    assertEquals(user, foundByLastName.get(0));
    List<User> foundByEmail = userDao.searchUsers(user.getEmail().substring(1, 16));
    assertEquals(1, foundByEmail.size());
    assertEquals(user, foundByEmail.get(0));
    List<User> foundByUsername = userDao.searchUsers(user.getUsername().substring(1, 12));
    assertEquals(1, foundByUsername.size());
    assertEquals(user, foundByUsername.get(0));
  }

  @Test
  public void testSearchUsers() throws IllegalAddChildOperation {
    // create 6 users
    List<User> created = new ArrayList<User>();
    for (int i = 0; i < 6; i++) {
      User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("zzuser" + i));
      user.setLastName(i + "");
      userDao.save(user);
      created.add(user);
    }
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    UserSearchCriteria userSearchCriteria = new UserSearchCriteria();

    pgCrit.setResultsPerPage(5); // should get 2 pages
    pgCrit.setOrderBy("username");
    pgCrit.setSortOrder(SortOrder.ASC);

    userSearchCriteria.setOnlyEnabled(true);
    pgCrit.setSearchCriteria(userSearchCriteria);

    ISearchResults<User> res1 = userDao.searchUsers(pgCrit);
    assertEquals(5, res1.getHitsPerPage());
    assertEquals(5, res1.getResults().size());
    long totalCount = res1.getTotalHits();
    assertEquals("admin", res1.getResults().get(0).getUsername());

    // test account locked filter, now using enabled attribute instead of locked
    created.get(0).setEnabled(false);
    userDao.save(created.get(0));

    // get onlyEnabled users
    userSearchCriteria.setOnlyEnabled(true);
    pgCrit.setSearchCriteria(userSearchCriteria);
    assertEquals(totalCount - 1, userDao.searchUsers(pgCrit).getTotalHits().intValue());

    userSearchCriteria.setOnlyEnabled(false);
    pgCrit.setSearchCriteria(userSearchCriteria);
    assertEquals(totalCount, userDao.searchUsers(pgCrit).getTotalHits().intValue());

    // test filter
    userSearchCriteria.setAllFields("sysadm");
    userSearchCriteria.setOnlyEnabled(true);
    pgCrit.setSearchCriteria(userSearchCriteria);

    assertEquals(SYS_ADMIN_UNAME, userDao.searchUsers(pgCrit).getResults().get(0).getUsername());

    pgCrit.setOrderBy("lastName");
    pgCrit.setSortOrder(SortOrder.DESC);
    userSearchCriteria.setAllFields(null);
    userSearchCriteria.setOnlyEnabled(true);
    pgCrit.setSearchCriteria(userSearchCriteria);

    // make sure there are no duplicates
    assertEquals(5, userDao.searchUsers(pgCrit).getResults().size());

    // check no results handled gracefully
    userSearchCriteria.setAllFields("xxxxxxx");
    pgCrit.setSearchCriteria(userSearchCriteria);
    assertEquals(0, userDao.searchUsers(pgCrit).getResults().size());

    // filter by temp users - none yet
    userSearchCriteria = new UserSearchCriteria();
    userSearchCriteria.setTempAccountsOnly(true);
    pgCrit.setSearchCriteria(userSearchCriteria);
    assertEquals(0, userDao.searchUsers(pgCrit).getResults().size());
    createAndSaveTmpUserIfNotExists("tmp");
    assertEquals(1, userDao.searchUsers(pgCrit).getResults().size());

    pgCrit.setSearchCriteria(null);
    flush();
    long totalUsers = userDao.searchUsers(pgCrit).getTotalHits();
    userSearchCriteria = new UserSearchCriteria();
    userSearchCriteria.setCreationDateEarlierThan(LocalDate.now().plusDays(10));
    pgCrit.setSearchCriteria(userSearchCriteria);

    assertTrue(userDao.searchUsers(pgCrit).getTotalHits() > 0);
    userSearchCriteria.setCreationDateEarlierThan(LocalDate.now().minusYears(20));
    assertEquals(0, userDao.searchUsers(pgCrit).getTotalHits().intValue());
  }

  @Test
  public void testSearchUsersByTags() throws IllegalAddChildOperation {
    // create 3 users
    List<User> created = new ArrayList<User>();
    for (int i = 0; i < 3; i++) {
      User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("zzuser" + i));
      user.setTagsList(List.of("zzTestTag", "zzTestUserTag" + i));
      userDao.save(user);
      created.add(user);
    }
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setResultsPerPage(5); // should get 2 pages
    pgCrit.setOrderBy("username");
    pgCrit.setSortOrder(SortOrder.ASC);

    UserSearchCriteria userSearchCriteria = new UserSearchCriteria();
    pgCrit.setSearchCriteria(userSearchCriteria);
    userSearchCriteria.setTags(new String[] {"zzTestTag"});

    // by common tag
    ISearchResults<User> res1 = userDao.searchUsers(pgCrit);
    assertEquals(3, res1.getTotalHits().intValue());

    userSearchCriteria.setTags(new String[] {"zzTestTag", "zzTestUserTag2"});
    pgCrit.setSearchCriteria(userSearchCriteria);

    // by two tags
    ISearchResults<User> res2 = userDao.searchUsers(pgCrit);
    assertEquals(1, res2.getTotalHits().intValue());
  }

  @Test
  public void testGetViewableUserList() {
    final int n3 = 3;
    User pi = createAndSaveUserIfNotExists("pi1", Constants.PI_ROLE);
    User u1 = createAndSaveUserIfNotExists("u1");
    User u2 = createAndSaveUserIfNotExists("u2");

    Group g1 = createGroup("g1", pi);
    g1.addMember(pi, RoleInGroup.PI);
    g1.addMember(u1, RoleInGroup.DEFAULT);
    g1.addMember(u2, RoleInGroup.DEFAULT);
    grpdao.save(g1);
    List<User> userList = userDao.getViewableUsersByRole(pi);
    assertEquals(n3, userList.size());
    List<User> ownerList = userDao.getViewableSharedRecordOwners(pi);
    assertEquals(0, ownerList.size());

    User admin = createAndSaveAdminUser();
    initialiseContentWithEmptyContent(admin);
    logoutAndLoginAs(admin);
    assertEquals(1, userDao.getViewableUsersByRole(admin).size());
  }

  @Test
  public void testGetUsernameByToken() {
    User user = createAndSaveRandomUser();
    TokenBasedVerification upc =
        new TokenBasedVerification(
            user.getEmail(), null, TokenBasedVerificationType.PASSWORD_CHANGE);
    upc = userDao.saveTokenBasedVerification(upc);
    assertEquals(user.getUsername(), userDao.getUsernameByToken(upc.getToken()).get());
  }

  @Test
  public void testGetUsersView() {
    List<UserView> userView = userDao.getAllUsersView();
    assertTrue(userView.size() > 0);
  }

  @Test
  public void getUserViewByUsername() {
    User user = createAndSaveRandomUser();
    UserView userView = userDao.getUserViewByUsername(user.getUsername());
    assertEquals(user.getEmail(), userView.getEmail());
  }
}

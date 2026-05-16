package com.researchspace.sysadmin;

import static com.researchspace.core.util.DateUtil.localDateToDateUTC;
import static com.researchspace.testutils.MockAndStubUtils.modifyUserCreationDate;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.testutils.MockAndStubUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the three operator SQL scripts in {@code scripts/}:
 *
 * <ul>
 *   <li>{@code find_temp_users_created_more_than_one_year_ago.sql}
 *   <li>{@code find_users_having_lastlogin_more_than_one_year_ago.sql}
 *   <li>{@code find_groups_having_max_lastlogin_more_than_one_year_ago.sql}
 * </ul>
 *
 * <p>Each test seeds users and groups into the transactional DB, then loads the SQL from the script
 * file at runtime and executes it via {@link JdbcTemplate}. Assertions are membership-based (the
 * IDs we created are or aren't in the result), so baseline / seed data does not interfere.
 */
public class FindDeletableUsersAndGroupsTest extends SpringTransactionalTest {

  private static final String TEMP_USERS_SCRIPT =
      "find_temp_users_created_more_than_one_year_ago.sql";
  private static final String OLD_LOGIN_USERS_SCRIPT =
      "find_users_having_lastlogin_more_than_one_year_ago.sql";
  private static final String OLD_LOGIN_GROUPS_SCRIPT =
      "find_groups_having_max_lastlogin_more_than_one_year_ago.sql";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Before
  public void setUpFlush() throws Exception {
    super.setUp();
  }

  // ---- find_temp_users_created_more_than_one_year_ago.sql -----------------

  @Test
  public void tempUsersScript_findsAgedTempUser() {
    User aged = createUser(/*temp*/ true, LocalDate.now().minusYears(2));
    List<Long> result = runScriptForUserIds(TEMP_USERS_SCRIPT);
    assertTrue("aged temp user should be returned", result.contains(aged.getId()));
  }

  @Test
  public void tempUsersScript_ignoresFreshTempUser() {
    User fresh = createUser(/*temp*/ true, LocalDate.now().minusDays(10));
    List<Long> result = runScriptForUserIds(TEMP_USERS_SCRIPT);
    assertFalse(
        "temp user created less than 1 year ago should NOT be returned",
        result.contains(fresh.getId()));
  }

  @Test
  public void tempUsersScript_ignoresAgedNonTempUser() {
    User aged = createUser(/*temp*/ false, LocalDate.now().minusYears(2));
    List<Long> result = runScriptForUserIds(TEMP_USERS_SCRIPT);
    assertFalse(
        "non-temp user (even if aged) should NOT be returned", result.contains(aged.getId()));
  }

  @Test
  public void tempUsersScript_mixedScenarioReturnsOnlyAgedTempUsers() {
    User agedTemp = createUser(true, LocalDate.now().minusYears(2));
    User freshTemp = createUser(true, LocalDate.now().minusDays(10));
    User agedNonTemp = createUser(false, LocalDate.now().minusYears(2));

    List<Long> result = runScriptForUserIds(TEMP_USERS_SCRIPT);

    assertTrue(result.contains(agedTemp.getId()));
    assertFalse(result.contains(freshTemp.getId()));
    assertFalse(result.contains(agedNonTemp.getId()));
  }

  // ---- find_users_having_lastlogin_more_than_one_year_ago.sql -------------

  @Test
  public void oldLoginUsersScript_ignoresUserWithNullLastLogin() {
    User neverLoggedIn = createUserWithLastLogin(null);
    List<Long> result = runScriptForUserIds(OLD_LOGIN_USERS_SCRIPT);
    assertFalse(
        "user with null lastLogin should NOT be returned", result.contains(neverLoggedIn.getId()));
  }

  @Test
  public void oldLoginUsersScript_findsUserWithAgedLastLogin() {
    User stale = createUserWithLastLogin(LocalDate.now().minusYears(2));
    List<Long> result = runScriptForUserIds(OLD_LOGIN_USERS_SCRIPT);
    assertTrue(
        "user with lastLogin > 1 year ago should be returned", result.contains(stale.getId()));
  }

  @Test
  public void oldLoginUsersScript_ignoresUserWithRecentLastLogin() {
    User recent = createUserWithLastLogin(LocalDate.now().minusDays(7));
    List<Long> result = runScriptForUserIds(OLD_LOGIN_USERS_SCRIPT);
    assertFalse(
        "user with recent lastLogin should NOT be returned", result.contains(recent.getId()));
  }

  @Test
  public void oldLoginUsersScript_mixedScenarioReturnsOnlyAgedLogins() {
    User aged = createUserWithLastLogin(LocalDate.now().minusYears(2));
    User recent = createUserWithLastLogin(LocalDate.now().minusDays(7));
    User never = createUserWithLastLogin(null);

    List<Long> result = runScriptForUserIds(OLD_LOGIN_USERS_SCRIPT);

    assertTrue(result.contains(aged.getId()));
    assertFalse(result.contains(recent.getId()));
    assertFalse(result.contains(never.getId()));
  }

  // ---- find_groups_having_max_lastlogin_more_than_one_year_ago.sql --------

  @Test
  public void oldLoginGroupsScript_ignoresGroupWhereAllMembersHaveNullLastLogin() {
    Group group = createGroupWithMemberLastLogins(/*pi*/ null, /*member*/ null);
    List<Long> result = runScriptForGroupIds(OLD_LOGIN_GROUPS_SCRIPT);
    assertFalse(
        "group with all-null member lastLogin should NOT be returned (MAX is null)",
        result.contains(group.getId()));
  }

  @Test
  public void oldLoginGroupsScript_findsGroupWhereAllMembersHaveAgedLastLogin() {
    Group group =
        createGroupWithMemberLastLogins(
            LocalDate.now().minusYears(2), LocalDate.now().minusYears(3));
    List<Long> result = runScriptForGroupIds(OLD_LOGIN_GROUPS_SCRIPT);
    assertTrue(
        "group with all aged member lastLogin should be returned", result.contains(group.getId()));
  }

  @Test
  public void oldLoginGroupsScript_ignoresGroupWithASingleRecentlyActiveMember() {
    Group group =
        createGroupWithMemberLastLogins(
            LocalDate.now().minusDays(7), LocalDate.now().minusYears(3));
    List<Long> result = runScriptForGroupIds(OLD_LOGIN_GROUPS_SCRIPT);
    assertFalse(
        "group with at least one recent login should NOT be returned",
        result.contains(group.getId()));
  }

  @Test
  public void oldLoginGroupsScript_findsGroupWithMixOfNullAndAgedLastLogin() {
    // MAX(lastLogin) ignores null, so a group with (null, aged) has MAX = aged and qualifies.
    Group group = createGroupWithMemberLastLogins(null, LocalDate.now().minusYears(2));
    List<Long> result = runScriptForGroupIds(OLD_LOGIN_GROUPS_SCRIPT);
    assertTrue(
        "group with mixed (null + aged) lastLogin should be returned (MAX ignores nulls)",
        result.contains(group.getId()));
  }

  @Test
  public void oldLoginGroupsScript_multipleGroupsOnlyQualifyingAppears() {
    Group qualifies =
        createGroupWithMemberLastLogins(
            LocalDate.now().minusYears(2), LocalDate.now().minusYears(3));
    Group disqualifies =
        createGroupWithMemberLastLogins(
            LocalDate.now().minusDays(7), LocalDate.now().minusYears(3));

    List<Long> result = runScriptForGroupIds(OLD_LOGIN_GROUPS_SCRIPT);

    assertTrue(result.contains(qualifies.getId()));
    assertFalse(result.contains(disqualifies.getId()));
  }

  // ---- helpers ------------------------------------------------------------

  /** Executes the given script file (located under repo-root/scripts/) and returns id results. */
  private List<Long> runScriptForUserIds(String scriptFilename) {
    String sql = readScript(scriptFilename);
    flushSession();
    return jdbcTemplate.queryForList(sql, Long.class);
  }

  private List<Long> runScriptForGroupIds(String scriptFilename) {
    String sql = readScript(scriptFilename);
    flushSession();
    return jdbcTemplate.queryForList(sql, Long.class);
  }

  private String readScript(String scriptFilename) {
    Path scriptPath = Paths.get(System.getProperty("user.dir"), "scripts", scriptFilename);
    try {
      return Files.readString(scriptPath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read SQL script at " + scriptPath, e);
    }
  }

  /** Hibernate sees in-memory entity changes; flush forces them to the JDBC connection. */
  private void flushSession() {
    sessionFactory.getCurrentSession().flush();
  }

  private User createUser(boolean tempAccount, LocalDate creationDate) {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("u"));
    user.setTempAccount(tempAccount);
    modifyUserCreationDate(user, localDateToDateUTC(creationDate));
    return userMgr.save(user);
  }

  private User createUserWithLastLogin(LocalDate lastLogin) {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("u"));
    if (lastLogin != null) {
      MockAndStubUtils.modifyDateField(
          user, localDateToDateUTC(lastLogin), User.class, "setLastLogin");
    }
    return userMgr.save(user);
  }

  private Group createGroupWithMemberLastLogins(LocalDate piLastLogin, LocalDate memberLastLogin) {
    User pi = createAndSaveAPi();
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("u"));
    initialiseContentWithEmptyContent(pi, member);
    Group group = createGroup(getRandomAlphabeticString("g"), pi);
    addUsersToGroup(pi, group, member);

    setLastLogin(pi, piLastLogin);
    setLastLogin(member, memberLastLogin);
    return group;
  }

  private void setLastLogin(User user, LocalDate when) {
    User reloaded = userMgr.get(user.getId());
    MockAndStubUtils.modifyDateField(
        reloaded, when == null ? null : localDateToDateUTC(when), User.class, "setLastLogin");
    userMgr.save(reloaded);
  }
}

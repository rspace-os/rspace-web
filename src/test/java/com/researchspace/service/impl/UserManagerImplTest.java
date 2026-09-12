package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.model.Role.SYSTEM_ROLE;
import static com.researchspace.testutils.TestFactory.createACommunity;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.RoleDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class UserManagerImplTest extends BaseManagerMockTestCase {

  private @InjectMocks UserManagerImpl userManager;
  private @InjectMocks RoleManagerImpl roleManager;
  private @Mock UserDao userDao;
  private @Mock RoleDao roleDao;
  private @Mock CommunityDao communityDao;
  private @Mock AnalyticsManager analyticsManager;

  @BeforeEach
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(
        userManager, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  public void testGetUser() throws Exception {
    final User testData = new User("1");
    testData.getRoles().add(Role.USER_ROLE);

    when(userDao.get(1L)).thenReturn(testData);

    User user = userManager.getUser("1");
    assertTrue(user != null);
    assert user != null;
    assertTrue(user.getRoles().size() == 1);
  }

  @Test
  public void testSaveUser() throws Exception {
    final User testData = new User("1");
    testData.setPassword("pwprd");
    testData.getRoles().add(Role.USER_ROLE);

    // set expected behavior on dao
    when(userDao.get(1L)).thenReturn(testData);

    final User user = userManager.getUser("1");
    when(userDao.saveUser(user)).thenReturn(user);

    User returned = userManager.saveNewUser(user);
    Mockito.verify(userDao, Mockito.times(1)).saveUser(user);
    Mockito.verify(analyticsManager, Mockito.times(1)).userCreated(user);
    assertTrue(returned.getRoles().size() == 1);
  }

  @Test
  public void testAddAndRemoveUser() throws Exception {
    User user = TestFactory.createAnyUser("any");
    final String uname = user.getUsername();

    // set expected behavior on role dao
    when(roleDao.getRoleByName(Constants.SYSADMIN_ROLE)).thenReturn(Role.SYSTEM_ROLE);

    Role role = roleManager.getRole(Constants.SYSADMIN_ROLE);
    user.addRole(role);

    // set expected behavior on user dao

    final User user1 = user;
    when(userDao.saveUser(user)).thenReturn(user1);
    when(userDao.getUserByEmail(user1.getEmail())).thenReturn(Collections.emptyList());

    user.setTempAccount(true);
    user = userManager.saveNewUser(user);

    assertTrue(user.getUsername().equals(uname));
    assertTrue(user.getRoles().size() == 1);
  }

  @Test
  public void testUserExistsException() throws Exception {
    // set expectations
    final User user = new User("admin");
    user.setEmail("matt@raibledesigns.com");
    user.setPassword("pwprd");
    final Exception ex = new DataIntegrityViolationException("");
    when(userDao.saveUser(user)).thenThrow(ex);

    // run test
    CoreTestUtils.assertExceptionThrown(
        () -> userManager.saveNewUser(user), UserExistsException.class);

    verify(analyticsManager, never()).userCreated(Mockito.any(User.class));
  }

  @Test
  public void checkAnalyticsNotCalledForTemporaryUser() throws UserExistsException {

    final User testData = new User("1");
    testData.setPassword("pwprd");
    testData.setTempAccount(true);

    // set expected behavior on dao
    when(userDao.get(1L)).thenReturn(testData);

    final User user = userManager.getUser("1");
    when(userDao.saveUser(user)).thenReturn(user);

    userManager.saveNewUser(user);
    verify(analyticsManager, never()).userCreated(Mockito.any(User.class));
  }

  @Test
  public void isUserInAdminsCommunityThrowsIAEIfNotAdmin() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          User nonAdmin = createAnyUser("any");
          User any = createAnyUser("any");
          userManager.isUserInAdminsCommunity(nonAdmin, any.getUsername());
        });
  }

  @Test
  public void isUserInAdminsCommunitySysAdminReturnsTrue() {
    User sysAdmin = createAnyUser("any");
    sysAdmin.setRoles(toSet(SYSTEM_ROLE));
    User any = createAnyUser("any");
    assertTrue(userManager.isUserInAdminsCommunity(sysAdmin, any.getUsername()));
  }

  @Test
  public void isUserInAdminsCommunityFalseIfNoCommunities() {
    User admin = createAdminUser();
    User any = createAnyUser("any");
    when(communityDao.listCommunitiesForAdmin(admin.getId())).thenReturn(Collections.emptyList());
    assertFalse(userManager.isUserInAdminsCommunity(admin, any.getUsername()));
  }

  @Test
  public void isUserInAdminsCommunityTrueIfInCommunity() {
    List<Community> comms = createCommunityWithId();
    User admin = createAdminUser();
    User any = createAnyUser("any");
    when(communityDao.listCommunitiesForAdmin(admin.getId())).thenReturn(comms);
    when(userDao.isUserInAdminsCommunity(any.getUsername(), comms.get(0).getId())).thenReturn(true);
    assertTrue(userManager.isUserInAdminsCommunity(admin, any.getUsername()));
  }

  @Test
  public void isUserInAdminsCommunityFalseIfNotInCommunity() {
    List<Community> comms = createCommunityWithId();
    User admin = createAdminUser();
    User any = createAnyUser("any");
    when(communityDao.listCommunitiesForAdmin(admin.getId())).thenReturn(comms);
    when(userDao.isUserInAdminsCommunity(any.getUsername(), comms.get(0).getId()))
        .thenReturn(false);
    assertFalse(userManager.isUserInAdminsCommunity(admin, any.getUsername()));
  }

  private User createAdminUser() {
    User admin = createAnyUser("any");
    admin.setRoles(toSet(Role.ADMIN_ROLE));
    return admin;
  }

  private List<Community> createCommunityWithId() {
    List<Community> comms = toList(createACommunity());
    comms.get(0).setId(1L);
    return comms;
  }

  /** A user whose UI_JSON_SETTINGS blob currently holds the given JSON (null for never written). */
  private User userWithUiJsonSettings(String storedJson) {
    User user = createAnyUser("jbloggs");
    user.setId(7L);
    if (storedJson != null) {
      user.setPreference(new UserPreference(Preference.UI_JSON_SETTINGS, user, storedJson));
    }
    when(userDao.getUserByUsername("jbloggs")).thenReturn(user);
    when(userDao.lockRowForUpdate(7L)).thenReturn(user);
    // the blob is read as a scalar under its own lock, not from the entity
    when(userDao.getPreferenceValueForUpdate(7L, Preference.UI_JSON_SETTINGS))
        .thenReturn(storedJson);
    return user;
  }

  private String storedUiJsonSettings(User user) {
    return user.getValueForPreference(Preference.UI_JSON_SETTINGS).getValue();
  }

  @Test
  public void mergeUiJsonSettingKeepsTheKeysItWasNotAskedToChange() throws Exception {
    // The whole UI_JSON_SETTINGS blob is one column, so the client used to read it, merge one key
    // and post the lot back. Two overlapping writers each merged into the same snapshot and the
    // later post dropped the earlier one's key. The merge happens here instead, under a row lock.
    User user = userWithUiJsonSettings("{\"GALLERY_VIEW_MODE\":{\"value\":\"grid\"}}");
    when(userDao.save(user)).thenReturn(user);

    userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "{\"value\":\"name\"}", user.getUsername());

    JsonNode merged = new ObjectMapper().readTree(storedUiJsonSettings(user));
    assertEquals("grid", merged.path("GALLERY_VIEW_MODE").path("value").asText());
    assertEquals("name", merged.path("GALLERY_SORT_BY").path("value").asText());
  }

  @Test
  public void mergeUiJsonSettingMergesIntoTheLockedColumnValueNotTheEntitySnapshot()
      throws Exception {
    // The entity the lock returns holds this transaction's snapshot of the blob (lockRowForUpdate
    // serialises, it does not refresh), so the merge must read the stored value as a scalar under
    // its own lock. The entity here carries an older blob than the column; only a merge into the
    // scalar's value keeps the key a concurrent writer added and its change to a shared key.
    User user = createAnyUser("jbloggs");
    user.setId(7L);
    user.setPreference(
        new UserPreference(
            Preference.UI_JSON_SETTINGS, user, "{\"GALLERY_VIEW_MODE\":{\"value\":\"list\"}}"));
    when(userDao.getUserByUsername("jbloggs")).thenReturn(user);
    when(userDao.lockRowForUpdate(7L)).thenReturn(user);
    when(userDao.getPreferenceValueForUpdate(7L, Preference.UI_JSON_SETTINGS))
        .thenReturn(
            "{\"GALLERY_VIEW_MODE\":{\"value\":\"grid\"},\"SYSADMIN_USERS_TABLE_COLUMNS\":{\"value\":1}}");
    when(userDao.save(user)).thenReturn(user);

    userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "{\"value\":\"name\"}", "jbloggs");

    JsonNode merged = new ObjectMapper().readTree(storedUiJsonSettings(user));
    // the key another writer committed after this transaction's snapshot survives...
    assertEquals(1, merged.path("SYSADMIN_USERS_TABLE_COLUMNS").path("value").asInt());
    // ...and so does its change to a key the snapshot also had
    assertEquals("grid", merged.path("GALLERY_VIEW_MODE").path("value").asText());
    assertEquals("name", merged.path("GALLERY_SORT_BY").path("value").asText());
  }

  @Test
  public void mergeUiJsonSettingWritesTheFirstKeyWhenNothingIsStoredYet() throws Exception {
    User user = userWithUiJsonSettings(null);
    when(userDao.save(user)).thenReturn(user);

    userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "{\"value\":\"name\"}", "jbloggs");

    JsonNode merged = new ObjectMapper().readTree(storedUiJsonSettings(user));
    assertEquals("name", merged.path("GALLERY_SORT_BY").path("value").asText());
  }

  @Test
  public void mergeUiJsonSettingRejectsAKeyThatIsNotAPreferenceName() {
    // The key is written verbatim into the user's single settings column, so an unconstrained one
    // lets a caller fill that column with arbitrary names until it hits the TEXT limit, after which
    // every keyed write for that user fails for good. Only a name the client actually declares is
    // accepted.
    // no DAO stubbing: the key is rejected before the user is even read
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("../evil key", "{}", "jbloggs"));
    assertThrows(
        IllegalArgumentException.class, () -> userManager.mergeUiJsonSetting("", "{}", "jbloggs"));
    // A null key is the same 400, not the NPE an immutable set's contains(null) would raise.
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting(null, "{}", "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  @Test
  public void mergeUiJsonSettingRejectsAWellFormedKeyThatNoPreferenceDeclares() {
    // A syntax rule alone (uppercase identifier) leaves the key space unbounded: every distinct
    // name a caller invents becomes another property of the blob, and nothing ever deletes one. The
    // oversize guard then makes that permanent, because once the accumulated junk brings the column
    // to its limit EVERY later keyed write for that user is rejected for good. Only the names the
    // client declares are accepted, so junk never enters the blob in the first place (Copilot
    // review, PR #1090).
    // no DAO stubbing: the key is rejected before the user is even read
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("AAAAAAAA", "{}", "jbloggs"));
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("UNUSED_1", "{}", "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  /** The client's own declaration of the preference names, the source this allowlist must track. */
  private static final Path UI_PREFERENCES_SOURCE =
      Path.of("src/main/webapp/ui/src/hooks/api/useUiPreference.tsx");

  /** An entry of that file's PREFERENCES map, e.g. {@code GALLERY_VIEW_MODE: Symbol.for("...")}. */
  private static final Pattern DECLARED_PREFERENCE =
      Pattern.compile("(\\w+):\\s*Symbol\\.for\\(\"(\\w+)\"\\)");

  /**
   * The names the frontend declares, read from its source at test time.
   *
   * <p>Deliberately NOT a hand-written copy. Transcribing them here made a third copy of one list
   * (the TS map, the Java allowlist, this test), and a third copy cannot catch the drift the test
   * exists to catch: adding a preference to the TS map and forgetting UI_JSON_SETTINGS_KEYS left
   * every Java test green while the new preference silently stopped persisting (parallel review).
   * Reading the real file is the same technique InventoryOperationsErrorCatalogTest uses.
   */
  private static List<String> declaredPreferenceNames() throws IOException {
    String source = Files.readString(UI_PREFERENCES_SOURCE);
    // Only the PREFERENCES map, so an unrelated Symbol.for elsewhere in the file cannot leak in.
    int start = source.indexOf("export const PREFERENCES");
    assertTrue(start >= 0, () -> "no PREFERENCES map in " + UI_PREFERENCES_SOURCE);
    int end = source.indexOf("};", start);
    assertTrue(end > start, () -> "unterminated PREFERENCES map in " + UI_PREFERENCES_SOURCE);

    List<String> names = new ArrayList<>();
    Matcher matcher = DECLARED_PREFERENCE.matcher(source.substring(start, end));
    while (matcher.find()) {
      assertEquals(
          matcher.group(1),
          matcher.group(2),
          "a PREFERENCES entry's key and its Symbol.for name must agree");
      names.add(matcher.group(1));
    }
    assertFalse(names.isEmpty(), () -> "no preference names parsed from " + UI_PREFERENCES_SOURCE);
    return names;
  }

  @Test
  public void mergeUiJsonSettingAcceptsEveryDeclaredPreferenceName() throws IOException {
    // The counterpart to the rejection above: the allowlist has to admit every name the client
    // declares in its PREFERENCES map, or a legitimate preference silently stops persisting.
    for (String declared : declaredPreferenceNames()) {
      User user = userWithUiJsonSettings("{}");
      when(userDao.save(user)).thenReturn(user);
      userManager.mergeUiJsonSetting(declared, "{\"value\":1}", "jbloggs");
      assertTrue(
          storedUiJsonSettings(user).contains(declared), declared + " must be an accepted key");
    }
  }

  @Test
  public void mergeUiJsonSettingRejectsAMergeThatWouldOverflowTheStoredColumn() {
    // The merged blob is one TEXT column (65535 chars). The UserPreference constructor fail-fast
    // validates that limit (SettingsType.validate), so a merge that would overflow throws before
    // anything is saved and the controller maps it to a 400; this test pins that the keyed path
    // cannot reach the database with an oversized blob (Copilot review, PR #1090).
    //
    // The overflow is reached by ACCUMULATION, not by one huge value: the per-key ceiling (S6)
    // rejects a single value big enough to overflow the column on its own, before the lock, so
    // this guard now only fires when several within-ceiling values together exceed the column.
    userWithUiJsonSettings("{\"GALLERY_VIEW_MODE\":{\"value\":\"" + "x".repeat(60_000) + "\"}}");
    String withinPerKeyCeiling = "{\"value\":\"" + "x".repeat(8_000) + "\"}";
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", withinPerKeyCeiling, "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  @Test
  public void mergeUiJsonSettingRejectsASingleValueAboveThePerKeyCeiling() {
    // The column-level guard above only fires once the MERGED blob overflows, so one allowed key
    // holding a near-65535-char value passes it and then makes every later keyed write for that
    // user overflow permanently: the same wedge the allowlist exists to prevent, reached through
    // one key instead of many. The per-key ceiling is checked before the row lock, so an oversized
    // value neither reaches the database nor holds a lock (parallel review, S6).
    String oversized = "{\"value\":\"" + "x".repeat(9_000) + "\"}";
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", oversized, "jbloggs"));
    verify(userDao, never()).lockRowForUpdate(Mockito.anyLong());
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  @Test
  public void mergeUiJsonSettingRejectsAValueThatIsNotJson() {
    // The value is stored verbatim inside the blob, so an unparseable one would corrupt every
    // other key in it on the next read.
    // no DAO stubbing: the value is rejected before the user is even read
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "not json", "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }
}

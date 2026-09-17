package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.model.Role.SYSTEM_ROLE;
import static com.researchspace.testutils.TestFactory.createACommunity;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.RoleDao;
import com.researchspace.dao.UserAccountEventDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.preference.Preference;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
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
  private @Mock UserAccountEventDao accountEventDao;
  private @Mock IPermissionUtils permissionUtils;
  private @Mock AnalyticsManager analyticsManager;
  private @Mock IPropertyHolder properties;
  private @Mock IVerificationPasswordValidator verificationPasswordValidator;

  @BeforeEach
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(
        userManager, "messages", new MessageSourceUtils(new JsonMessageSource()));
    // UserManagerImpl declares a UserDao constructor, so @InjectMocks satisfies it by
    // constructor injection and does not also field-inject. Wire its @Autowired fields by hand.
    ReflectionTestUtils.setField(userManager, "communityDao", communityDao);
    ReflectionTestUtils.setField(userManager, "roleDao", roleDao);
    ReflectionTestUtils.setField(userManager, "accountEventDao", accountEventDao);
    ReflectionTestUtils.setField(userManager, "permissnUtils", permissionUtils);
    ReflectionTestUtils.setField(userManager, "analyticsManager", analyticsManager);
    ReflectionTestUtils.setField(userManager, "properties", properties);
    ReflectionTestUtils.setField(
        userManager, "verificationPasswordValidator", verificationPasswordValidator);
  }

  @Test
  public void testGetUser() throws Exception {
    final User testData = new User("1");
    testData.getRoles().add(Role.USER_ROLE);

    when(userDao.get(1L)).thenReturn(testData);

    User user = userManager.getUser("1");
    assertTrue(user != null);
    assert user != null;
    assertThat(user.getRoles()).hasSize(1);
  }

  @Test
  public void testSaveUser() throws Exception {
    final User testData = new User("1");
    testData.setPassword("pwprd");
    testData.getRoles().add(Role.USER_ROLE);

    when(userDao.get(1L)).thenReturn(testData);

    final User user = userManager.getUser("1");
    when(userDao.saveUser(user)).thenReturn(user);

    User returned = userManager.saveNewUser(user);
    Mockito.verify(userDao, Mockito.times(1)).saveUser(user);
    Mockito.verify(analyticsManager, Mockito.times(1)).userCreated(user);
    assertThat(returned.getRoles()).hasSize(1);
  }

  @Test
  public void testAddAndRemoveUser() throws Exception {
    User user = TestFactory.createAnyUser("any");
    final String uname = user.getUsername();

    when(roleDao.getRoleByName(Constants.SYSADMIN_ROLE)).thenReturn(Role.SYSTEM_ROLE);

    Role role = roleManager.getRole(Constants.SYSADMIN_ROLE);
    user.addRole(role);

    final User user1 = user;
    when(userDao.saveUser(user)).thenReturn(user1);
    when(userDao.getUserByEmail(user1.getEmail())).thenReturn(Collections.emptyList());

    user.setTempAccount(true);
    user = userManager.saveNewUser(user);

    assertTrue(user.getUsername().equals(uname));
    assertThat(user.getRoles()).hasSize(1);
  }

  @Test
  public void testUserExistsException() throws Exception {
    final User user = new User("admin");
    user.setEmail("matt@raibledesigns.com");
    user.setPassword("pwprd");
    final Exception ex = new DataIntegrityViolationException("");
    when(userDao.saveUser(user)).thenThrow(ex);

    // run test
    assertThrows(UserExistsException.class, () -> userManager.saveNewUser(user));

    verify(analyticsManager, never()).userCreated(Mockito.any(User.class));
  }

  @Test
  public void checkAnalyticsNotCalledForTemporaryUser() throws UserExistsException {

    final User testData = new User("1");
    testData.setPassword("pwprd");
    testData.setTempAccount(true);

    when(userDao.get(1L)).thenReturn(testData);

    final User user = userManager.getUser("1");
    when(userDao.saveUser(user)).thenReturn(user);

    userManager.saveNewUser(user);
    verify(analyticsManager, never()).userCreated(Mockito.any(User.class));
  }

  @Test
  public void isUserInAdminsCommunityThrowsIAEIfNotAdmin() {
    User nonAdmin = createAnyUser("any");
    User any = createAnyUser("any");
    String username = any.getUsername();

    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.isUserInAdminsCommunity(nonAdmin, username));
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

  private User userWithUiJsonSettings(String storedJson) {
    User user = createAnyUser("jbloggs");
    user.setId(7L);
    if (storedJson != null) {
      user.setPreference(new UserPreference(Preference.UI_JSON_SETTINGS, user, storedJson));
    }
    when(userDao.getUserByUsername("jbloggs")).thenReturn(user);
    return user;
  }

  private String storedUiJsonSettings(User user) {
    return user.getValueForPreference(Preference.UI_JSON_SETTINGS).getValue();
  }

  @Test
  public void mergeUiJsonSettingKeepsTheKeysItWasNotAskedToChange() throws Exception {
    // The whole UI_JSON_SETTINGS blob is one column, so the client used to read it, merge one key
    // and post the lot back. Two overlapping writers each merged into the same snapshot and the
    // later post dropped the earlier one's key. The merge happens here instead.
    User user = userWithUiJsonSettings("{\"GALLERY_VIEW_MODE\":{\"value\":\"grid\"}}");
    when(userDao.save(user)).thenReturn(user);

    userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "{\"value\":\"name\"}", user.getUsername());

    JsonNode merged = new ObjectMapper().readTree(storedUiJsonSettings(user));
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
   * every Java test green while the new preference silently stopped persisting. Reading the real
   * file is the same technique InventoryOperationsErrorCatalogTest uses.
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
    userWithUiJsonSettings("{\"GALLERY_VIEW_MODE\":{\"value\":\"" + "x".repeat(62_000) + "\"}}");
    String withinPerKeyCeiling = "{\"value\":\"" + "x".repeat(6_000) + "\"}";
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", withinPerKeyCeiling, "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  @Test
  public void mergeUiJsonSettingRejectsAMergeWhoseBytesOverflowTheColumnThoughItsCharactersDoNot() {
    userWithUiJsonSettings(
        "{\"GALLERY_VIEW_MODE\":{\"value\":\"" + "\u20ac".repeat(25_000) + "\"}}");

    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "{\"value\":\"name\"}", "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  @Test
  public void mergeUiJsonSettingRejectsASingleValueAboveThePerKeyCeiling() {
    // The column-level guard above only fires once the MERGED blob overflows, so one allowed key
    // holding a near-65535-char value passes it and then makes every later keyed write for that
    // user overflow permanently: the same wedge the allowlist exists to prevent, reached through
    // one key instead of many. The per-key ceiling is checked before the user is even read, so an
    // oversized value never reaches the database.
    String oversized = "{\"value\":\"" + "x".repeat(9_000) + "\"}";
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", oversized, "jbloggs"));
    verify(userDao, never()).getUserByUsername(Mockito.anyString());
    verify(userDao, never()).save(Mockito.any(User.class));
  }

  @Test
  public void mergeUiJsonSettingRejectsAValueThatIsNotJson() {
    // The value is stored verbatim inside the blob, so an unparseable one would corrupt every
    // other key in it on the next read.
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "not json", "jbloggs"));
    verify(userDao, never()).save(Mockito.any(User.class));
  }
}

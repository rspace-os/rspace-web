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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    when(userDao.getForUpdate(7L)).thenReturn(user);
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
  public void mergeUiJsonSettingLocksTheUserRowBeforeReadingTheBlob() throws Exception {
    // Reading the stored value before taking the lock would reintroduce the race one layer down.
    User user = userWithUiJsonSettings("{}");
    when(userDao.save(user)).thenReturn(user);

    userManager.mergeUiJsonSetting("GALLERY_SORT_BY", "{\"value\":\"name\"}", "jbloggs");

    InOrder inOrder = Mockito.inOrder(userDao);
    inOrder.verify(userDao).getForUpdate(7L);
    inOrder.verify(userDao).save(user);
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

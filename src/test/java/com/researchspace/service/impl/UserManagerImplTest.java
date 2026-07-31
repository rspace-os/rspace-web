package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.model.Role.SYSTEM_ROLE;
import static com.researchspace.testutils.TestFactory.createACommunity;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.RoleDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.TestFactory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
public class UserManagerImplTest extends BaseManagerMockTestCase {

  private @InjectMocks UserManagerImpl userManager;
  private @InjectMocks RoleManagerImpl roleManager;
  private @Mock UserDao userDao;
  private @Mock RoleDao roleDao;
  private @Mock CommunityDao communityDao;
  private @Mock AnalyticsManager analyticsManager;

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
    assertThrows(UserExistsException.class, () -> userManager.saveNewUser(user));

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
    User nonAdmin = createAnyUser("any");
    User any = createAnyUser("any");
    assertThrows(
        IllegalArgumentException.class,
        () -> userManager.isUserInAdminsCommunity(nonAdmin, any.getUsername()));
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
}

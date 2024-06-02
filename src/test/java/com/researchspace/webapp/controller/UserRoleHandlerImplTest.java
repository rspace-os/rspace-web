package com.researchspace.webapp.controller;

import static com.researchspace.model.record.TestFactory.createAnyGroup;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.UserPermissionUtils;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserManager;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UserRoleHandlerImplTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  @Mock UserManager userManager;
  @Mock UserPermissionUtils userPermUtils;
  @Mock IPermissionUtils permUtils;
  @Mock IContentInitializer contentInitialiser;
  @Mock RoleManager roleMgr;
  @InjectMocks UserRoleHandlerImpl roleHandler;
  User admin;

  @Before
  public void setUp() throws Exception {
    admin = TestFactory.createAnyUserWithRole("admin", Role.SYSTEM_ROLE.getName());
    when(roleMgr.getRole(Role.PI_ROLE.getName())).thenReturn(Role.PI_ROLE);
  }

  @Test
  public void grantPiRoleToUser() {
    User toPromote = TestFactory.createAnyUser("user");
    roleHandler.grantGlobalPiRoleToUser(admin, toPromote);
    verify(userManager).save(toPromote);
  }

  @Test
  public void grantPiRoleToPiDoesNothing() {
    User toPromote = createAPi();
    roleHandler.grantGlobalPiRoleToUser(admin, toPromote);
    verify(userManager, never()).save(toPromote);
  }

  private User createAPi() {
    User u = TestFactory.createAnyUserWithRole("user", Role.PI_ROLE.getName());
    u.addRole(Role.USER_ROLE); // pi's always have user role too
    return u;
  }

  @Test(expected = AuthorizationException.class)
  public void grantPiRoleRequiresAuth() {
    User toPromote = TestFactory.createAnyUser("user");
    setUpThrowAuthException(toPromote);
    roleHandler.grantGlobalPiRoleToUser(admin, toPromote);
    verify(userManager, never()).save(toPromote);
  }

  private void setUpThrowAuthException(User target) {
    doThrow(AuthorizationException.class)
        .when(userPermUtils)
        .assertHasPermissionsOnTargetUser(
            Mockito.eq(admin), Mockito.eq(target), Mockito.anyString());
  }

  @Test
  public void revokePiRole() {
    User toDemote = createAPi();
    when(userManager.save(toDemote)).thenReturn(toDemote);
    toDemote = roleHandler.revokeGlobalPiRoleFromUser(admin, toDemote);
    verify(userManager).save(toDemote);
    assertFalse(toDemote.hasRole(Role.PI_ROLE));
    assertTrue(toDemote.hasRole(Role.USER_ROLE));
  }

  @Test
  public void revokePiRoleFromNonPiDoesNothing() {
    User user = createAnyUser("any");
    user.addRole(Role.USER_ROLE);
    user = roleHandler.revokeGlobalPiRoleFromUser(admin, user);
    verify(userManager, never()).save(user);
    assertFalse(user.hasRole(Role.PI_ROLE));
    assertTrue(user.hasRole(Role.USER_ROLE));
  }

  @Test(expected = IllegalStateException.class)
  public void revokePiRoleFailsIfPiIsPiOfGroup() {
    User toDemote = createAPi();
    Group grp = createAnyGroup(toDemote, null);
    toDemote = roleHandler.revokeGlobalPiRoleFromUser(admin, toDemote);
  }

  @Test(expected = AuthorizationException.class)
  public void revokePiRoleRequiresAuth() {
    User toDemote = createAPi();
    toDemote.addRole(Role.USER_ROLE); // pi's always have user role too
    setUpThrowAuthException(toDemote);
    toDemote = roleHandler.revokeGlobalPiRoleFromUser(admin, toDemote);
    verify(userManager, never()).save(toDemote);
    assertTrue(toDemote.hasRole(Role.PI_ROLE));
  }
}

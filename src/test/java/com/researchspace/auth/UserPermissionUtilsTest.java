package com.researchspace.auth;

import static com.researchspace.testutils.TestFactory.createAnyUserWithRole;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPermissionUtilsTest {

  User sysadminSubject, communityAdminSubject, piSubject, userSubject;
  private @Mock UserManager userManager;
  @InjectMocks UserPermissionUtils userPermissionUtils;

  @BeforeEach
  void setUp() {
    sysadminSubject = createAnyUserWithRole("sysadminSubject", Role.SYSTEM_ROLE.getName());
    communityAdminSubject = createAnyUserWithRole("communityAdmin", Role.ADMIN_ROLE.getName());
    piSubject = createAnyUserWithRole("piSubject", Role.PI_ROLE.getName());
    userSubject = createAnyUserWithRole("userSubject", Role.USER_ROLE.getName());
    communityAdminSubject.setId(1L);
  }

  @ParameterizedTest
  @DisplayName("Any target user is valid for sysadmin subject")
  @MethodSource("targetUsersByRole")
  void sysadminSubjectRoleAlwaysValid(String username, String role) {
    User target = createAnyUserWithRole(username, role);
    assertTrue(
        userPermissionUtils.isTargetUserValidForSubjectRole(sysadminSubject, target.getUsername()));
  }

  @ParameterizedTest
  @DisplayName("No target user is valid for pi subject")
  @MethodSource("targetUsersByRole")
  void piSubjectRoleNeverValid(String username, String role) {
    User target = createAnyUserWithRole(username, role);
    assertFalse(
        userPermissionUtils.isTargetUserValidForSubjectRole(piSubject, target.getUsername()));
  }

  @ParameterizedTest
  @DisplayName("No target user is valid for user subject")
  @MethodSource("targetUsersByRole")
  void userSubjectRoleNeverValid(String username, String role) {
    User target = createAnyUserWithRole(username, role);
    assertFalse(
        userPermissionUtils.isTargetUserValidForSubjectRole(userSubject, target.getUsername()));
  }

  @DisplayName("CA can't access any sysadmin")
  @Test
  void communityAdminCannotAccessSysadminRole() {
    stubGetUser(sysadminSubject);
    assertFalse(
        userPermissionUtils.isTargetUserValidForSubjectRole(
            communityAdminSubject, sysadminSubject.getUsername()));
  }

  @DisplayName("CA cannot access another CA")
  @Test
  void communityAdminCannotAccessAnotherCommunityAdmin() {
    User targetCommunityAdmin = createTargetCommunityAdmin();
    stubGetUser(targetCommunityAdmin);

    assertFalse(
        userPermissionUtils.isTargetUserValidForSubjectRole(
            communityAdminSubject, targetCommunityAdmin.getUsername()));
  }

  @DisplayName("CA can access another user or PI in the same community")
  @ParameterizedTest
  @ValueSource(strings = {"ROLE_PI", "ROLE_USER"})
  void communityAdminCanAccessPIOrUserInSameCommunity(String roleName) {
    User target = createAnyUserWithRole("target", roleName);
    stubGetUser(target);
    setTargetUserInSameCommunity(target, true);

    assertTrue(
        userPermissionUtils.isTargetUserValidForSubjectRole(
            communityAdminSubject, target.getUsername()));
  }

  @DisplayName("CA cannot access another user or PI in a different community")
  @ParameterizedTest
  @ValueSource(strings = {"ROLE_PI", "ROLE_USER"})
  void communityAdminCannotAccessPIOrUserInDifferentCommunity(String roleName) {
    User target = createAnyUserWithRole("target", roleName);
    stubGetUser(target);
    setTargetUserInSameCommunity(target, false);

    assertFalse(
        userPermissionUtils.isTargetUserValidForSubjectRole(
            communityAdminSubject, target.getUsername()));
  }

  private User createTargetCommunityAdmin() {
    User targetCommunityAdmin =
        createAnyUserWithRole("targetCommunityAdmin", Role.ADMIN_ROLE.getName());
    targetCommunityAdmin.setId(2L);
    return targetCommunityAdmin;
  }

  private void setTargetUserInSameCommunity(User targetAdmin, boolean sameCommunity) {
    when(userManager.isUserInAdminsCommunity(communityAdminSubject, targetAdmin.getUsername()))
        .thenReturn(sameCommunity);
  }

  private void stubGetUser(User target) {
    when(userManager.getUserByUsername(target.getUsername())).thenReturn(target);
  }

  private static Stream<Arguments> targetUsersByRole() {
    return Stream.of(
        Arguments.of("otherSysadminTarget", Role.SYSTEM_ROLE.getName()),
        Arguments.of("CommunityAdminTarget", Role.ADMIN_ROLE.getName()),
        Arguments.of("piTarget", Role.PI_ROLE.getName()),
        Arguments.of("userTarget", Role.USER_ROLE.getName()));
  }
}

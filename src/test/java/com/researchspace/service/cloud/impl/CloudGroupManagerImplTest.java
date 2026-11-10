package com.researchspace.service.cloud.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudGroupManagerImplTest {

  @Mock private UserManager userManager;
  @Mock private RoleManager roleManager;
  @Mock private GroupManager groupManager;
  @Mock private IPermissionUtils permUtils;

  @InjectMocks private CloudGroupManagerImpl cloudGroupManager;

  private User toPromote;

  @BeforeEach
  void setUp() {
    toPromote = TestFactory.createAnyUser("toPromote");
    toPromote.setId(1L);
    toPromote.setContentInitialized(true);
    when(userManager.get(toPromote.getId())).thenReturn(toPromote);
    when(roleManager.getRole(Constants.PI_ROLE)).thenReturn(Role.PI_ROLE);
    when(userManager.save(toPromote)).thenReturn(toPromote);
  }

  @Test
  void selfPromotionLeavesCollabGroupRolesUnchanged() {
    cloudGroupManager.promoteUserToPI(toPromote, toPromote);

    verify(groupManager, never()).setPiRoleInCollaborationGroups(any(), any());
  }

  @Test
  void promotionByAnotherUserSetsCollabGroupPiRole() {
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName());
    sysadmin.setId(2L);

    cloudGroupManager.promoteUserToPI(toPromote, sysadmin);

    verify(groupManager).setPiRoleInCollaborationGroups(toPromote, sysadmin);
  }
}

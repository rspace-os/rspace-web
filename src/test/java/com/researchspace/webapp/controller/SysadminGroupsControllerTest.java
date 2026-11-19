package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.GroupUsageInfo;
import com.researchspace.admin.service.SysadminGroupManager;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.service.LabGroupStorageAlertManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class SysadminGroupsControllerTest {

  @Mock UserManager userManager;
  @Mock MessageSourceUtils messages;
  @Mock SysadminGroupManager sysMgr;
  @Mock SystemPropertyPermissionManager systemPropertyPermissionManager;
  @Mock LabGroupStorageAlertManager storageAlertManager;
  @InjectMocks SysadminGroupsController controller;

  @Test
  void communityAdminCannotSetTheThreshold() {
    signedInAs(Role.ADMIN_ROLE);

    assertThrows(AuthorizationException.class, () -> controller.setStorageAlertThreshold("100"));

    verifyNoInteractions(storageAlertManager);
  }

  @Test
  void sysadminSavesTheThreshold() {
    User sysadmin = signedInAs(Role.SYSTEM_ROLE);
    when(storageAlertManager.setThresholdMb("100", sysadmin)).thenReturn(Optional.of(100L));

    AjaxReturnObject<String> result = controller.setStorageAlertThreshold("100");

    assertEquals("100", result.getData());
    assertNull(result.getErrorMsg());
  }

  @Test
  void sysadminRemovesTheThreshold() {
    User sysadmin = signedInAs(Role.SYSTEM_ROLE);
    when(storageAlertManager.setThresholdMb("", sysadmin)).thenReturn(Optional.empty());

    AjaxReturnObject<String> result = controller.setStorageAlertThreshold("");

    assertEquals("", result.getData());
    assertNull(result.getErrorMsg());
  }

  @Test
  void invalidThresholdComesBackAsAMessage() {
    User sysadmin = signedInAs(Role.SYSTEM_ROLE);
    LocalizedIllegalArgumentException refused =
        new LocalizedIllegalArgumentException(
            "system.groupList.storageAlert.errors.invalidThreshold");
    when(storageAlertManager.setThresholdMb("1.5", sysadmin)).thenThrow(refused);
    when(messages.getMessage(refused)).thenReturn("Enter a whole number");

    AjaxReturnObject<String> result = controller.setStorageAlertThreshold("1.5");

    assertNull(result.getData());
    assertThat(result.getErrorMsg().getErrorMessages()).containsExactly("Enter a whole number");
  }

  @Test
  void groupsPageShowsTheThresholdToASysadmin() {
    signedInAs(Role.SYSTEM_ROLE);
    noGroups();
    when(storageAlertManager.getThresholdMb()).thenReturn(Optional.of(100L));
    Model model = new ExtendedModelMap();

    controller.listGroups(
        model, PaginationCriteria.createDefaultForClass(Group.class), new GroupSearchCriteria());

    assertEquals("100", model.getAttribute("storageAlertThresholdMb"));
  }

  @Test
  void groupsPageShowsAnEmptyFieldWhileTheAlertIsOff() {
    signedInAs(Role.SYSTEM_ROLE);
    noGroups();
    when(storageAlertManager.getThresholdMb()).thenReturn(Optional.empty());
    Model model = new ExtendedModelMap();

    controller.listGroups(
        model, PaginationCriteria.createDefaultForClass(Group.class), new GroupSearchCriteria());

    assertEquals("", model.getAttribute("storageAlertThresholdMb"));
  }

  @Test
  void groupsPageDoesNotGiveTheThresholdToACommunityAdmin() {
    signedInAs(Role.ADMIN_ROLE);
    noGroups();
    Model model = new ExtendedModelMap();

    controller.listGroups(
        model, PaginationCriteria.createDefaultForClass(Group.class), new GroupSearchCriteria());

    assertFalse(model.containsAttribute("storageAlertThresholdMb"));
    verifyNoInteractions(storageAlertManager);
  }

  private User signedInAs(Role role) {
    User user = TestFactory.createAnyUser("subject");
    user.addRole(role);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    return user;
  }

  private void noGroups() {
    when(sysMgr.getGroupUsageInfo(any(), any()))
        .thenReturn(new SearchResultsImpl<>(List.<GroupUsageInfo>of(), 0, 0L));
  }
}

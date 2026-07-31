package com.researchspace.webapp.controller;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.GroupManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.security.Principal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
public class GroupControllerTest {
  @Mock UserManager userMgr;
  @Mock GroupManager grpMgr;
  @Mock IPermissionUtils permissionUtils;
  @Mock ApplicationEventPublisher publisher;
  @Mock Principal principal;
  @Mock IPropertyHolder properties;
  @Mock IGroupPermissionUtils groupPermissionUtils;
  @Mock SystemPropertyPermissionManager systemPropertyPermissionUtils;
  @Mock SystemPropertyPermissionManager systemPropertyPermissionManager;

  @InjectMocks GroupController grpController;

  User userPI = TestFactory.createAnyUserWithRole("pi", Role.PI_ROLE.getName());
  User userA = TestFactory.createAnyUserWithRole("userA", Role.USER_ROLE.getName());
  User userB = TestFactory.createAnyUserWithRole("userB", Role.USER_ROLE.getName());
  Group group = TestFactory.createAnyGroup(userPI, new User[] {userA});
  StaticMessageSource messages = new StaticMessageSource();

  @BeforeEach
  public void setUp() throws Exception {
    messages.addMessage("errors.maxlength", Locale.getDefault(), "toobig");
    grpController.setMessageSource(new MessageSourceUtils(messages));

    Set<Group> cononectedGroups = new HashSet<>();
    cononectedGroups.add(group);
    userA.setConnectedGroups(cononectedGroups);
    userB.setConnectedGroups(new HashSet<>());
  }

  @Test
  public void renameGroup() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(userPI);
    when(grpMgr.getGroup(1L)).thenReturn(group);
    AjaxReturnObject<String> response = grpController.renameGroup(new ExtendedModelMap(), 1L, "");
    assertNotNull(response.getError());

    response =
        grpController.renameGroup(
            new ExtendedModelMap(), 1L, randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH + 1));
    assertNotNull(response.getError());
    assertEquals("toobig", response.getError().getAllErrorMessagesAsStringsSeparatedBy(""));
    Mockito.verifyNoInteractions(publisher);
    verify(grpMgr, never()).saveGroup(group, false, userPI);

    response =
        grpController.renameGroup(
            new ExtendedModelMap(), 1L, randomAlphabetic(BaseRecord.DEFAULT_VARCHAR_LENGTH));
    assertNull(response.getError());
    verify(grpMgr).saveGroup(group, false, userPI);
  }

  @Test
  public void getGroupForPI() {
    when(userMgr.getUserByUsername(any())).thenReturn(userPI);
    when(grpMgr.getGroupWithCommunities(1L)).thenReturn(group);
    when(properties.isProfileHidingEnabled()).thenReturn(false);
    when(groupPermissionUtils.subjectCanAlterGroupRole(any(), any(), any())).thenReturn(false);
    when(permissionUtils.isPermitted(any(), any(), any())).thenReturn(true);
    when(systemPropertyPermissionUtils.isPropertyAllowed(any(Group.class), anyString()))
        .thenReturn(true);

    String response = null;

    try {
      response = grpController.viewGroup(new ExtendedModelMap(), 1L, principal);
    } catch (Exception e) {
      fail(e.getMessage());
    }

    assertEquals(GroupController.GROUPS_VIEW_NAME, response);
  }

  @Test
  public void getGroupForGroupMember() {
    when(userMgr.getUserByUsername(any())).thenReturn(userA);
    when(grpMgr.getGroupWithCommunities(1L)).thenReturn(group);
    when(properties.isProfileHidingEnabled()).thenReturn(false);
    when(groupPermissionUtils.subjectCanAlterGroupRole(any(), any(), any())).thenReturn(false);
    when(permissionUtils.isPermitted(any(), any(), any())).thenReturn(true);
    when(systemPropertyPermissionUtils.isPropertyAllowed(any(Group.class), anyString()))
        .thenReturn(true);

    String response = null;

    try {
      response = grpController.viewGroup(new ExtendedModelMap(), 1L, principal);
    } catch (Exception e) {
      fail(e.getMessage());
    }

    assertEquals(GroupController.GROUPS_VIEW_NAME, response);
  }

  @Test
  public void getGroupForNonGroupMember() {
    when(userMgr.getUserByUsername(any())).thenReturn(userB);
    when(grpMgr.getGroupWithCommunities(1L)).thenReturn(group);
    when(properties.isProfileHidingEnabled()).thenReturn(false);
    when(groupPermissionUtils.subjectCanAlterGroupRole(any(), any(), any())).thenReturn(false);
    when(permissionUtils.isPermitted(any(), any(), any())).thenReturn(false);
    when(systemPropertyPermissionUtils.isPropertyAllowed(any(Group.class), anyString()))
        .thenReturn(false);

    String response = null;

    try {
      response = grpController.viewGroup(new ExtendedModelMap(), 1L, principal);
    } catch (Exception e) {
      fail(e.getMessage());
    }

    assertEquals(GroupController.GROUPS_VIEW_PUBLIC_NAME, response);
  }
}

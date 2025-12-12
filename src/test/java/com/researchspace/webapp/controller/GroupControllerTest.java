package com.researchspace.webapp.controller;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.ExtendedModelMap;

public class GroupControllerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
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

  @Before
  public void setUp() throws Exception {
    messages.addMessage("errors.maxlength", Locale.getDefault(), "toobig");
    grpController.setMessageSource(new MessageSourceUtils(messages));

    userA.setConnectedGroups(new ArrayList<>(Arrays.asList(group)));
    userB.setConnectedGroups(new ArrayList<>());
  }

  @After
  public void tearDown() throws Exception {}

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
    Mockito.verifyZeroInteractions(publisher);
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

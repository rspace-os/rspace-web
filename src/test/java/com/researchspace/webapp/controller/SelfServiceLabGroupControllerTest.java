package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.service.GroupManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.CloudGroupManager;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.cloud.CommunityUserManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

@RunWith(MockitoJUnitRunner.class)
public class SelfServiceLabGroupControllerTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  @InjectMocks private SelfServiceLabGroupController controller;
  @Mock private CloudGroupManager cloudGroupManager;
  @Mock private GroupManager groupManager;
  @Mock private CreateCloudGroupValidator validator;
  @Mock private IControllerInputValidator inputValidator;
  @Mock private BindingResult bindingResult;
  @Mock private HttpServletRequest request;
  @Mock private CreateCloudGroup createCloudGroup;
  @Mock private User piUser;
  @Mock private User invitedUser1;
  @Mock private User invitedUser2;
  @Mock private UserManager userManager;
  @Mock private CloudNotificationManager cloudNotificationManager;
  @Mock private CommunityUserManager cloudUserManager;
  @Mock private Group newLabGroup;
  @Mock private IPermissionUtils permissionUtils;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private MessageSourceUtils messages;
  private Long newLabGroupID = 10L;

  @Before
  public void setUp() throws Exception {
    when(createCloudGroup.getEmails()).thenReturn(new String[] {""});
    when(createCloudGroup.getGroupName()).thenReturn("grpName");
    when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    when(cloudGroupManager.createAndSaveGroup(
            eq(true), eq("grpName"), eq(piUser), eq(piUser), eq(GroupType.LAB_GROUP), eq(piUser)))
        .thenReturn(newLabGroup);
    when(newLabGroup.getId()).thenReturn(newLabGroupID);
  }

  @Test
  public void shouldValidate() {
    controller.createSelfServiceLabGroup(createCloudGroup, bindingResult, request);
    verify(validator, times(1)).validate(any(), eq(bindingResult));
  }

  @Test
  public void shouldReturnAjaxObjectWithErrors() {
    when(bindingResult.hasErrors()).thenReturn(true);
    ErrorList errorList = new ErrorList();
    errorList.addErrorMsg("a");
    errorList.addErrorMsg("b");
    when(inputValidator.populateErrorList(eq(bindingResult), any(ErrorList.class)))
        .thenReturn(errorList);
    AjaxReturnObject response =
        controller.createSelfServiceLabGroup(createCloudGroup, bindingResult, request);
    String errors = response.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy(",");
    assertEquals("a,b", errors);
  }

  @Test
  public void shouldCreateAndSaveSelfServiceLabGroup() {
    AjaxReturnObject response =
        controller.createSelfServiceLabGroup(createCloudGroup, bindingResult, request);
    assertNull(response.getErrorMsg());
    assertTrue(response.isSuccess());
    assertEquals(((Map) response.getData()).get("newGroup"), "" + newLabGroupID);
    verify(publisher).publishEvent(any(GenericEvent.class));
  }

  @Test
  public void shouldReturnErrorWhenDuplicateNameForSelfServiceLabGroup() {
    BindingResult errorsEx = new BeanPropertyBindingResult(createCloudGroup, "");
    when(createCloudGroup.getGroupName()).thenReturn("grpName");
    Group existingWithSameDisplayName = new Group();
    existingWithSameDisplayName.setDisplayName("grpName");
    when(groupManager.listGroupsForOwner(eq(piUser)))
        .thenReturn(List.of(existingWithSameDisplayName));
    when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    IControllerInputValidator validator = new DTOControllerValidatorImpl();
    ReflectionTestUtils.setField(controller, "inputValidator", validator);
    MessageSourceUtils messageSourceUtils = new MessageSourceUtils();
    ReflectionTestUtils.setField(validator, "messages", messages);
    when(messages.getMessage(any(ObjectError.class)))
        .thenReturn("You have already created a Group with the name: grpName");
    AjaxReturnObject response =
        controller.createSelfServiceLabGroup(createCloudGroup, errorsEx, request);
    String errors = response.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy(",");
    assertEquals("You have already created a Group with the name: grpName", errors);
  }

  @Test
  public void shouldSendJoinGroupRequestsAndEmailsIfUsersInvitedToJoin() {
    String[] emails = new String[] {"email1@com", "email2@com"};
    List<User> invitedUsers = List.of(invitedUser1, invitedUser2);
    when(createCloudGroup.getGroupName()).thenReturn("grpName");
    when(createCloudGroup.getEmails()).thenReturn(emails);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    when(cloudGroupManager.createAndSaveGroup(
            eq(true), eq("grpName"), eq(piUser), eq(piUser), eq(GroupType.LAB_GROUP), eq(piUser)))
        .thenReturn(newLabGroup);
    when(cloudUserManager.createInvitedUserList(eq(Arrays.asList(emails))))
        .thenReturn(invitedUsers);
    controller.createSelfServiceLabGroup(createCloudGroup, bindingResult, request);
    verify(permissionUtils).refreshCache();
    verify(cloudNotificationManager).sendJoinGroupRequest(eq(piUser), eq(newLabGroup));
    verify(cloudNotificationManager, times(1))
        .sendJoinGroupInvitationEmail(eq(piUser), eq(invitedUser1), eq(newLabGroup), eq(request));
    verify(cloudNotificationManager, times(1))
        .sendJoinGroupInvitationEmail(eq(piUser), eq(invitedUser2), eq(newLabGroup), eq(request));
  }

  @Test
  public void shouldNotSendJoinGroupRequestsIfNoUsersInvitedToJoin() {
    String[] emails = new String[] {"email1@com", "email2@com"};
    List<User> invitedUsers = List.of(invitedUser1, invitedUser2);
    lenient().when(createCloudGroup.getGroupName()).thenReturn("grpName");
    lenient().when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    lenient()
        .when(
            cloudGroupManager.createAndSaveGroup(
                eq(true),
                eq("grpName"),
                eq(piUser),
                eq(piUser),
                eq(GroupType.LAB_GROUP),
                eq(piUser)))
        .thenReturn(newLabGroup);
    lenient()
        .when(cloudUserManager.createInvitedUserList(eq(Arrays.asList(emails))))
        .thenReturn(invitedUsers);
    controller.createSelfServiceLabGroup(createCloudGroup, bindingResult, request);
    verify(permissionUtils, never()).refreshCache();
    verify(cloudNotificationManager, never()).sendJoinGroupRequest(eq(piUser), eq(newLabGroup));
    verify(cloudNotificationManager, never())
        .sendJoinGroupInvitationEmail(eq(piUser), any(User.class), eq(newLabGroup), eq(request));
  }

  @Test
  public void shouldDeleteSelfServiceLabGroupWhenPIisOwner() {
    Long groupID = 1L;
    when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    when(groupManager.getGroup(eq(groupID))).thenReturn(newLabGroup);
    when(newLabGroup.isSelfService()).thenReturn(true);
    when(newLabGroup.getOwner()).thenReturn(piUser);
    // Note that the redirect is actually handled by JS code.
    String redirectUrl = controller.removeGroup(groupID);
    assertEquals("redirect:/userform", redirectUrl);
    verify(groupManager).removeGroup(eq(groupID), eq(piUser));
    verify(publisher).publishEvent(any(GenericEvent.class));
  }

  @Test
  public void shouldNotDeleteLabGroupWhenNotSelfServiceEVenWhenPIisCreator() {
    Long groupID = 1L;
    when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    when(groupManager.getGroup(eq(groupID))).thenReturn(newLabGroup);
    lenient().when(newLabGroup.isSelfService()).thenReturn(false);
    lenient().when(newLabGroup.getOwner()).thenReturn(piUser);
    // Note that the redirect is actually handled by JS code.
    assertThrows(AuthorizationException.class, () -> controller.removeGroup(groupID));
  }

  @Test
  public void shouldNotDeleteLabGroupWhenPIisNotCreator() {
    Long groupID = 1L;
    when(userManager.getAuthenticatedUserInSession()).thenReturn(piUser);
    when(groupManager.getGroup(eq(groupID))).thenReturn(newLabGroup);
    when(newLabGroup.isSelfService()).thenReturn(true);
    when(newLabGroup.getOwner()).thenReturn(invitedUser1);
    // Note that the redirect is actually handled by JS code.
    assertThrows(AuthorizationException.class, () -> controller.removeGroup(groupID));
  }
}

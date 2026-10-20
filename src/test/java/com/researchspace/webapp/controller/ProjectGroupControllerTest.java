package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.service.GroupManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.CloudGroupManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BindingResult;

@ExtendWith(MockitoExtension.class)
public class ProjectGroupControllerTest {
  @InjectMocks private ProjectGroupController controller;
  @Mock private UserManager userManager;
  @Mock private GroupManager groupManager;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManager;
  @Mock private CloudGroupManager cloudGroupManager;
  @Mock private CreateCloudGroupValidator cloudGroupValidator;
  @Mock private MessageSourceUtils messages;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private CreateCloudGroup createCloudGroup;
  @Mock private BindingResult bindingResult;
  @Mock private HttpServletRequest request;
  @Mock private User owner;
  @Mock private Group newGroup;

  @BeforeEach
  public void stubSessionUser() {
    when(userManager.getAuthenticatedUserInSession()).thenReturn(owner);
  }

  private void stubProjectGroups(boolean allowed) {
    when(systemPropertyPermissionManager.isPropertyAllowed(
            owner, SystemPropertyName.ALLOW_PROJECT_GROUPS))
        .thenReturn(allowed);
  }

  @Test
  public void shouldRefuseFormWhenProjectGroupsDenied() {
    stubProjectGroups(false);
    assertThrows(
        AuthorizationException.class, () -> controller.projectGroupForm(new ExtendedModelMap()));
  }

  @Test
  public void shouldRefuseCreationWhenProjectGroupsDenied() {
    stubProjectGroups(false);
    assertThrows(
        AuthorizationException.class,
        () -> controller.createProjectGroup(createCloudGroup, bindingResult, request));
    verifyNoInteractions(cloudGroupValidator, cloudGroupManager, groupManager);
  }

  @Test
  public void shouldRefuseDeletionWhenProjectGroupsDenied() {
    stubProjectGroups(false);
    assertThrows(AuthorizationException.class, () -> controller.removeGroup(1L));
    verifyNoInteractions(groupManager);
  }

  @Test
  public void shouldCreateProjectGroupWhenAllowed() {
    stubProjectGroups(true);
    when(createCloudGroup.getGroupName()).thenReturn("grpName");
    when(createCloudGroup.getEmails()).thenReturn(new String[] {""});
    when(cloudGroupManager.createAndSaveProjectGroup("grpName", owner, owner)).thenReturn(newGroup);
    when(newGroup.getId()).thenReturn(10L);
    AjaxReturnObject<Map<String, String>> response =
        controller.createProjectGroup(createCloudGroup, bindingResult, request);
    assertTrue(response.isSuccess());
    assertEquals("10", response.getData().get("newGroup"));
    verify(publisher).publishEvent(any(GenericEvent.class));
  }
}

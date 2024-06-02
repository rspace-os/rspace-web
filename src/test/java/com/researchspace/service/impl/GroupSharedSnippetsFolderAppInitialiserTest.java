package com.researchspace.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class GroupSharedSnippetsFolderAppInitialiserTest {
  @Mock private ApplicationContext mockAppContext;
  @Mock private GroupManager groupManagerMock;
  @Mock private UserManager userManagerMock;
  @Mock private Group mockGroup;
  @Mock private User userMock;
  @Mock private SecurityManager securityManagerMock;
  @Mock private Subject subjectMock;
  private GroupSharedSnippetsFolderAppInitialiser testee;
  private ArgumentCaptor<Callable> captor;

  @BeforeEach
  public void setup() throws IOException {
    ThreadContext
        .unbindSubject(); // Need this else the first subject created is bound and re-used in all
    // tests!
    SecurityUtils.setSecurityManager(securityManagerMock);
    testee = new GroupSharedSnippetsFolderAppInitialiser();
    when(securityManagerMock.createSubject(any(SubjectContext.class))).thenReturn(subjectMock);
    ReflectionTestUtils.setField(testee, "groupManager", groupManagerMock);
    ReflectionTestUtils.setField(testee, "userManager", userManagerMock);
    when(groupManagerMock.list()).thenReturn(List.of(mockGroup));
    when(userManagerMock.getUserByUsername(eq(Constants.SYSADMIN_UNAME))).thenReturn(userMock);
    captor = ArgumentCaptor.forClass(Callable.class);
    when(subjectMock.execute(captor.capture())).thenReturn(true);
  }

  @Test
  public void shouldCallAuthenticatedActionAsSysadminOnAppStartup() {
    testee.onAppStartup(mockAppContext);
    verify(subjectMock).login(any(GlobalInitSysadminAuthenticationToken.class));
    verify(subjectMock, times(2)).logout();
  }

  @Test
  public void authenticatedActionShouldCreateGroupCommunalFoldersOnAppStartup() throws Exception {
    testee.onAppStartup(mockAppContext);
    Callable authenticatedAction = captor.getValue();
    when(mockGroup.getId()).thenReturn(1L);
    when(userMock.getUsername()).thenReturn("sysadmin");
    authenticatedAction.call();
    verify(groupManagerMock).createSharedCommunalGroupFolders(eq(1L), eq("sysadmin"));
  }
}

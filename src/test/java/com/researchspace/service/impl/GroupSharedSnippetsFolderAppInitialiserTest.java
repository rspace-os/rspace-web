package com.researchspace.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.auth.GlobalInitSysadminAuthenticationToken;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
public class GroupSharedSnippetsFolderAppInitialiserTest {
  @Mock private ApplicationContext mockAppContext;
  @Mock private Group mockGroup;
  @Mock private User userMock;
  @Mock private Subject subjectMock;

  @Mock private GroupManager groupManagerMock;
  @Mock private UserManager userManagerMock;
  @InjectMocks private GroupSharedSnippetsFolderAppInitialiser testee;

  private ArgumentCaptor<Callable<Boolean>> captor;

  @BeforeEach
  public void setup() {
    // Bind a thread-local Subject instead of mutating the global SecurityManager
    ThreadContext.bind(subjectMock);
    when(groupManagerMock.list()).thenReturn(List.of(mockGroup));
    when(userManagerMock.getUserByUsername(Constants.SYSADMIN_UNAME)).thenReturn(userMock);
    captor = ArgumentCaptor.forClass(Callable.class);
    when(subjectMock.execute(captor.capture())).thenReturn(true);
  }

  @AfterEach
  public void tearDown() {
    ThreadContext.unbindSubject();
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
    Callable<Boolean> authenticatedAction = captor.getValue();
    when(mockGroup.getId()).thenReturn(1L);
    when(userMock.getUsername()).thenReturn("sysadmin");
    authenticatedAction.call();
    verify(groupManagerMock).createSharedCommunalGroupFolders(1L, "sysadmin");
  }
}

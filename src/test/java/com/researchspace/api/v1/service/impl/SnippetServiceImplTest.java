package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.RecordManager;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
class SnippetServiceImplTest {

  @Mock private RecordManager recordManager;

  @Mock private PermissionUtils permissionUtils;

  @InjectMocks private SnippetServiceImpl service;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setUsername("alice");
  }

  @Test
  void getSnippetChecksPermissionsAndReturnsSnippet() {
    long id = 123L;
    Snippet snippet = new Snippet();
    snippet.setId(id);

    when(recordManager.getAsSubclass(id, Snippet.class)).thenReturn(snippet);

    Snippet result = service.getSnippet(id, user);

    assertSame(snippet, result);
    verify(permissionUtils).assertIsPermitted(snippet, PermissionType.READ, user, "read snippet");
  }

  @Test
  void whenSnippetNotFoundThenExceptionBubblesUp() {
    long id = 999L;
    when(recordManager.getAsSubclass(id, Snippet.class))
        .thenThrow(new ObjectRetrievalFailureException("Snippet", id));

    assertThrows(ObjectRetrievalFailureException.class, () -> service.getSnippet(id, user));
  }

  @Test
  void whenUserNotPermittedThenExceptionBubblesUp() {
    long id = 123L;
    Snippet snippet = new Snippet();
    when(recordManager.getAsSubclass(id, Snippet.class)).thenReturn(snippet);

    doThrow(new AuthorizationException())
        .when(permissionUtils)
        .assertIsPermitted(snippet, PermissionType.READ, user, "read snippet");

    assertThrows(AuthorizationException.class, () -> service.getSnippet(id, user));
  }
}

package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.RecordManager;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectRetrievalFailureException;

class SnippetServiceImplTest {

  private RecordManager recordManager;
  private PermissionUtils permissionUtils;
  private SnippetServiceImpl service;
  private User user;

  @BeforeEach
  void setUp() {
    recordManager = org.mockito.Mockito.mock(RecordManager.class);
    permissionUtils = org.mockito.Mockito.mock(PermissionUtils.class);
    service = new SnippetServiceImpl(recordManager, permissionUtils);
    user = new User();
    user.setUsername("alice");
  }

  @Test
  void getSnippet_success_returnsSnippet_andChecksPermission() {
    long id = 42L;
    Snippet snippet = new Snippet();
    snippet.setId(id);

    when(recordManager.getAsSubclass(id, Snippet.class)).thenReturn(snippet);

    Snippet result = service.getSnippet(id, user);

    assertSame(snippet, result);
    verify(recordManager).getAsSubclass(id, Snippet.class);
    verify(permissionUtils).assertIsPermitted(snippet, PermissionType.READ, user, "read snippet");
    verifyNoMoreInteractions(recordManager, permissionUtils);
  }

  @Test
  void getSnippet_missing_bubblesUp() {
    long id = 99L;
    when(recordManager.getAsSubclass(id, Snippet.class))
        .thenThrow(new ObjectRetrievalFailureException("Snippet", id));

    assertThrows(ObjectRetrievalFailureException.class, () -> service.getSnippet(id, user));
  }

  @Test
  void getSnippet_unauthorized_bubblesUp() {
    long id = 7L;
    Snippet snippet = new Snippet();
    when(recordManager.getAsSubclass(id, Snippet.class)).thenReturn(snippet);

    doThrow(new AuthorizationException("nope"))
        .when(permissionUtils)
        .assertIsPermitted(snippet, PermissionType.READ, user, "read snippet");

    assertThrows(AuthorizationException.class, () -> service.getSnippet(id, user));
  }
}

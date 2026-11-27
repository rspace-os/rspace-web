package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
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

  @Mock private FolderManager folderManager;

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
    long id = -999L;
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

  @Test
  void galleryParentFolderIsTheReadableSharedFolderWithTheLowestId() {
    Folder higher = sharedFolderInGallery(true);
    Folder lower = sharedFolderInGallery(true);
    when(higher.getId()).thenReturn(20L);
    when(lower.getId()).thenReturn(10L);
    Snippet snippet = snippetSharedInto(higher, lower);

    assertEquals(Optional.of(lower), service.getGalleryParentFolder(snippet, user));
  }

  @Test
  void galleryParentFolderSkipsAnUnreadableSharedFolder() {
    Folder unreadable = sharedFolderInGallery(false);
    Folder readable = sharedFolderInGallery(true);
    Snippet snippet = snippetSharedInto(unreadable, readable);

    assertEquals(Optional.of(readable), service.getGalleryParentFolder(snippet, user));
  }

  private Folder galleryRoot;

  private Folder sharedFolderInGallery(boolean readable) {
    if (galleryRoot == null) {
      galleryRoot = mock(Folder.class);
      when(folderManager.getGalleryRootFolderForUser(user)).thenReturn(galleryRoot);
    }
    Folder folder = mock(Folder.class);
    RSPath pathToGalleryRoot = mock(RSPath.class);
    when(pathToGalleryRoot.isEmpty()).thenReturn(false);
    when(folder.getShortestPathToParent(galleryRoot)).thenReturn(pathToGalleryRoot);
    when(permissionUtils.isPermitted(folder, PermissionType.READ, user)).thenReturn(readable);
    return folder;
  }

  private Snippet snippetSharedInto(Folder... folders) {
    Snippet snippet = mock(Snippet.class);
    User owner = new User();
    owner.setUsername("bob");
    when(snippet.getOwner()).thenReturn(owner);
    when(snippet.getOwnerParent()).thenReturn(Optional.of(mock(Folder.class)));
    Set<RecordToFolder> parents = new LinkedHashSet<>();
    for (Folder folder : folders) {
      RecordToFolder relation = mock(RecordToFolder.class);
      when(relation.getFolder()).thenReturn(folder);
      parents.add(relation);
    }
    when(snippet.getParents()).thenReturn(parents);
    return snippet;
  }
}

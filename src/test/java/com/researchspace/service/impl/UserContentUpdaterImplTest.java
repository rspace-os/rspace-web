package com.researchspace.service.impl;

import static com.researchspace.service.UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserFolderCreator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class UserContentUpdaterImplTest {

  private UserContentUpdater testee;
  @Mock private UserFolderCreator userFolderCreatorMock;
  @Mock private RecordManager recordManagerMock;
  @Mock private User userMock;
  @Mock private Folder userSnippetFolder;
  @Mock private Folder previouslyCreatedSnippetFolder;

  @Before
  public void setUp() {
    testee = new UserContentUpdaterImpl();
    ReflectionTestUtils.setField(testee, "userFolderCreator", userFolderCreatorMock);
    ReflectionTestUtils.setField(testee, "recordManager", recordManagerMock);
    when(recordManagerMock.getGallerySubFolderForUser(eq(Folder.SNIPPETS_FOLDER), eq(userMock)))
        .thenReturn(userSnippetFolder);
  }

  @Test
  public void shouldCreateSharedSnippetFolderOnUserContentUpdateIfNotCreatedAlready() {
    when(userSnippetFolder.getSubFolderByName(
            SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME))
        .thenReturn(null);
    testee.doUserContentUpdates(userMock);
    verify(userFolderCreatorMock).createSharedSnippetFolder(eq(userMock), eq(userSnippetFolder));
  }

  @Test
  public void shouldNotCreateSharedSnippetFolderOnUserContentUpdateIfCreatedAlready() {
    when(userSnippetFolder.getSubFolderByName(
            SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME))
        .thenReturn(previouslyCreatedSnippetFolder);
    testee.doUserContentUpdates(userMock);
    verify(userFolderCreatorMock, never())
        .createSharedSnippetFolder(eq(userMock), eq(userSnippetFolder));
  }
}

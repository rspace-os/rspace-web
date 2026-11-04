package com.researchspace.search.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentSearchBaseTest {

  @TempDir File indexFolder;

  @Test
  void searchAcceptsIndexDirectoryCreatedByConcurrentCaller() throws IOException {
    File directory = mock(File.class);
    when(directory.isDirectory()).thenReturn(true);
    when(directory.canWrite()).thenReturn(true);
    when(directory.exists()).thenReturn(false);
    when(directory.mkdir()).thenReturn(false);
    when(directory.toPath()).thenReturn(indexFolder.toPath());
    LuceneSearchStrategy searcher = new LuceneSearchStrategy();
    searcher.setIndexFolderDirectly(directory);
    User user = TestFactory.createAnyUser("searcher");

    assertEquals(List.of(), searcher.searchFiles("missing", user));
  }

  @Test
  void searchRejectsFailedIndexDirectoryCreation() {
    File directory = mock(File.class);
    when(directory.isDirectory()).thenReturn(true, false);
    when(directory.canWrite()).thenReturn(true);
    when(directory.exists()).thenReturn(false);
    when(directory.mkdir()).thenReturn(false);
    LuceneSearchStrategy searcher = new LuceneSearchStrategy();
    searcher.setIndexFolderDirectly(directory);
    User user = TestFactory.createAnyUser("searcher");

    IOException exception =
        assertThrows(IOException.class, () -> searcher.searchFiles("missing", user));
    assertEquals(
        "Lucene index could not be created for path " + AttachmentSearchBase.DEFAULTINDEX_DIRECTORY,
        exception.getMessage());
  }
}

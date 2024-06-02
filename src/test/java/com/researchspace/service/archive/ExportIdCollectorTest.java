package com.researchspace.service.archive;

import static com.researchspace.model.core.RecordType.FOLDER;
import static com.researchspace.model.core.RecordType.MEDIA_FILE;
import static com.researchspace.model.core.RecordType.NORMAL;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.FolderManager;
import com.researchspace.service.archive.export.ExportIdCollector;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExportIdCollectorTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock FolderManager folderMgr;

  ExportIdCollector exportIdCollector;

  @Before
  public void setUp() throws Exception {
    exportIdCollector = new ExportIdCollector(folderMgr, new ArchiveExportConfig());
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void emptyNotebooksAndFoldersIncluded() { // rspac-1361

    User user = createAnyUser("any");
    Folder folder = TestFactory.createAFolder("testFolder", user);
    folder.setId(21L);
    Notebook notebook = TestFactory.createANotebook("testNotebook", user);
    notebook.setId(22L);

    Mockito.when(folderMgr.getFolder(folder.getId(), user)).thenReturn(folder);
    Mockito.when(folderMgr.getFolder(notebook.getId(), user)).thenReturn(notebook);

    Long[] ids = new Long[] {folder.getId(), notebook.getId()};
    String[] types = new String[] {folder.getType(), notebook.getType()};

    ImmutableExportRecordList recordList = exportIdCollector.getRecordsToArchive(ids, types, user);
    assertEquals(0, recordList.getRecordsToExport().size());
    assertEquals(2, recordList.getFolderTree().size());
  }

  @Test
  public void mediaIdsAreAtFrontOfListToBeExportedFirst() { // rspac-1333
    // odd ids are docs, even are media
    Long[] ids = new Long[] {1L, 4L, 3L, 2L, 8L, 7L, 5L, 6L, 9L};
    String[] types =
        new String[] {
          NORMAL.name(),
          MEDIA_FILE.name(),
          NORMAL.name(),
          MEDIA_FILE.name(),
          MEDIA_FILE.name(),
          NORMAL.name(),
          NORMAL.name(),
          MEDIA_FILE.name(),
          FOLDER.name()
        };
    ImmutableExportRecordList recordList =
        exportIdCollector.getRecordsToArchive(ids, types, createAnyUser("any"));
    // media
    assertTrue(
        recordList.getRecordsToExport().subList(0, 4).stream()
            .allMatch(oid -> oid.getDbId() % 2 == 0));
    // normal
    assertTrue(
        recordList.getRecordsToExport().subList(4, 8).stream()
            .allMatch(oid -> oid.getDbId() % 2 == 1));
  }
}

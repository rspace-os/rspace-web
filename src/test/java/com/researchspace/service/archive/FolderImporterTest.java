package com.researchspace.service.archive;

import static com.researchspace.core.util.XMLReadWriteUtils.fromXML;
import static org.junit.Assert.assertEquals;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ArchiveModel;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.service.ImportContext;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.function.Predicate;
import javax.xml.bind.JAXBException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;

public class FolderImporterTest extends SpringTransactionalTest {

  private @Autowired FolderTreeImporter folderImporter;
  File folderXml;
  final int MEDIA_GALLERY_FIXED_FOLDER_COUNT = 7;

  class ArchiveModelTSS extends ArchiveModel {
    SemanticVersion version;

    @Override
    public SemanticVersion getSourceRSpaceVersion() {
      return version;
    }
  }

  @Before
  public void setUp() throws Exception {
    folderXml = RSpaceTestUtils.getResource("archives/folderTree.xml");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testCreateFolderTreeTotalCounts()
      throws FileNotFoundException, JAXBException, SAXException {
    User anyUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(anyUser);
    logoutAndLoginAs(anyUser);
    ImmutableExportRecordList folderList = fromXML(folderXml, ExportRecordList.class, null, null);

    int numFoldersToImport = folderList.getFolderTree().size();
    int numNotebooksToImport = getNotebookToImportCount(folderList);
    System.err.println(" to import is " + numFoldersToImport);
    int currentFoldersForUser = getFolderCount(anyUser).intValue();
    int currentNotebooksForUser = getNotebookCount(anyUser).intValue();
    System.err.println(" curr count is  " + currentFoldersForUser);
    ArchivalImportConfig cfg = new ArchivalImportConfig();
    ArchiveModelTSS am = new ArchiveModelTSS();
    am.version = new SemanticVersion("1.36");
    cfg.setUser(anyUser.getUsername());
    Map<Long, Folder> oldId2NewFolder =
        folderImporter.createFolderTree(
            folderXml, new ImportContext(), cfg, am, new ImportArchiveReport());
    int afterimportFoldersForUser = getFolderCount(anyUser).intValue();
    final int expectedFoldersToReuse =
        MEDIA_GALLERY_FIXED_FOLDER_COUNT + 1 + 1 + 1; // for gallery root, api folder and templates;
    assertEquals(
        numFoldersToImport - expectedFoldersToReuse,
        afterimportFoldersForUser - currentFoldersForUser);
    int afterimportNotebooksForUser = getNotebookCount(anyUser).intValue();
    assertEquals(numNotebooksToImport, afterimportNotebooksForUser - currentNotebooksForUser);
    assertTreeStructure(folderList, oldId2NewFolder, anyUser);
  }

  private void assertTreeStructure(
      ImmutableExportRecordList rl, Map<Long, Folder> oldId2NewFolder, User anyUser) {
    for (ArchiveFolder archiveFolder : rl.getFolderTree()) {
      Long id = archiveFolder.getId();
      Long parentId = archiveFolder.getParentId();
      // String name = archiveFolder.getName();
      if (parentId != null) {
        String parentName = rl.getArchiveFolder(parentId).get().getName();
        if (archiveFolder.isSystemFolder()
            && archiveFolder.getName().equals(Folder.TEMPLATE_MEDIA_FOLDER_NAME)) {
          assertEquals(anyUser.getUsername(), oldId2NewFolder.get(id).getParent().getName());
        } else if (archiveFolder.isSystemFolder() && archiveFolder.getName().equals("Gallery")) {
          assertEquals(anyUser.getUsername(), oldId2NewFolder.get(id).getParent().getName());
        } else if (archiveFolder.isApiFolder()) {
          assertEquals(anyUser.getUsername(), oldId2NewFolder.get(id).getParent().getName());
        } else {
          assertEquals(parentName, oldId2NewFolder.get(id).getParent().getName());
        }
      }
    }
  }

  private int getNotebookToImportCount(ImmutableExportRecordList rl) {
    return (int) rl.getFolderTree().stream().filter(isNotebook()).count();
  }

  private Predicate<? super ArchiveFolder> isNotebook() {
    return af -> af.getType().equals("NOTEBOOK");
  }
}

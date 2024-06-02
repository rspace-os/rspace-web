package com.researchspace.service.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.archive.export.ExportArchiveTreeTraversor;
import com.researchspace.testutils.FolderTestUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExportTreeTraversorTest {

  ExportArchiveTreeTraversor traversor;
  User owner;
  Folder f1, f2, f3;
  UserFolderSetup setup;
  ArchiveExportConfig archiveCfg;

  @Before
  public void setUp() throws Exception {
    owner = TestFactory.createAnyUser("any");
    setup = FolderTestUtils.createDefaultFolderStructure(owner);
    archiveCfg = new ArchiveExportConfig();
    resetTreeTraverser();

    f2 = createAFolder("f2");
    f3 = createAFolder("f3");
    setup.getUserRoot().addChild(f2, owner);
    f2.addChild(f3, owner);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testProcessDoesntIncludeDeletedFolders() {
    setup.getUserRoot().process(traversor);
    int beforeDeleteCount = folderTreeSize();
    resetTreeTraverser();
    f3.setRecordDeleted(true);
    setup.getUserRoot().process(traversor);
    assertEquals(beforeDeleteCount - 1, folderTreeSize());
  }

  private void resetTreeTraverser() {
    traversor = new ExportArchiveTreeTraversor(archiveCfg);
  }

  private int folderTreeSize() {
    return traversor.getExportRecordList().getFolderTree().size();
  }

  // deleted media files might still be linked to
  // these files are copied into document export folders if required when exporting FieldContents.
  @Test
  public void testProcessDoesNotIncludeDeletedMediaItems() {
    EcatImage image = TestFactory.createEcatImage(3L);
    setup.getMediaImgExamples().addChild(image, owner);
    setup.getUserRoot().process(traversor);
    int beforeDeleteCount = recordsToExportCount();
    resetTreeTraverser();
    image.setRecordDeleted(true);
    setup.getUserRoot().process(traversor);
    assertEquals(beforeDeleteCount, recordsToExportCount());
  }

  private int recordsToExportCount() {
    return traversor.getExportRecordList().getRecordsToExport().size();
  }

  @Test
  public void testProcess() {
    final int expected = 3;
    f1 = createAFolder("f1");
    f1.addChild(f2, owner);
    f1.process(traversor);
    assertEquals(expected, folderTreeSize());
    assertEquals(0, recordsToExportCount());

    resetTreeTraverser();
    // ignore system folders
    f1.addType(RecordType.SYSTEM);
    f1.setName(Folder.SHARED_FOLDER_NAME);
    f1.process(traversor);
    // f1 not processed, and neither are children
    assertEquals(0, folderTreeSize());
  }

  // RSPAC-1411
  @Test
  public void testProcessIncludeExampleFolderOrNot() {
    final int expected = 3;
    f1 = createAFolder("f1");
    f1.addChild(f2, owner);
    f1.setName(Folder.EXAMPLES_FOLDER);
    f1.process(traversor);
    archiveCfg.setExportScope(ExportScope.SELECTION);
    // default export type is
    assertEquals(expected, folderTreeSize());

    resetTreeTraverser();
    archiveCfg.setExportScope(ExportScope.GROUP);
    // default export type is
    assertEquals(0, folderTreeSize());

    resetTreeTraverser();
    archiveCfg.setExportScope(ExportScope.USER);
    // default export type is
    assertEquals(0, folderTreeSize());
  }

  @Test
  public void testProcessIncludeTemplateFolderOrNot() {
    final int expected = 3;
    f1 = TestFactory.createASystemFolder(Folder.TEMPLATE_MEDIA_FOLDER_NAME, owner);
    f1.setId(RandomUtils.nextLong());
    f1.addChild(f2, owner);
    f1.process(traversor);
    archiveCfg.setExportScope(ExportScope.SELECTION);

    assertEquals(expected, folderTreeSize());

    resetTreeTraverser();
    archiveCfg.setExportScope(ExportScope.GROUP);
    archiveCfg.setArchiveType(ArchiveExportConfig.HTML);
    f1.process(traversor);

    assertEquals(0, folderTreeSize());
    resetTreeTraverser();
    archiveCfg.setArchiveType(ArchiveExportConfig.XML);
    // only include  templates from XML
    f1.process(traversor);
    assertEquals(expected, folderTreeSize());

    resetTreeTraverser();
    archiveCfg.setExportScope(ExportScope.USER);

    archiveCfg.setArchiveType(ArchiveExportConfig.HTML);
    f1.process(traversor);
    assertEquals(0, folderTreeSize());
    resetTreeTraverser();
    // only include  templates from XML
    archiveCfg.setArchiveType(ArchiveExportConfig.XML);
    f1.process(traversor);
    // default export type is
    assertEquals(expected, folderTreeSize());
  }

  @Test
  public void testProcessIncludesGallery() {
    setup.getUserRoot().process(traversor);
    assertTrue(
        traversor.getExportRecordList().getFolderTree().stream()
            .anyMatch(af -> af.getName().equals("Gallery")));
  }

  private Folder createAFolder(String name) {
    Folder f = TestFactory.createAFolder(name, owner);
    f.setId(RandomUtils.nextLong());
    return f;
  }
}

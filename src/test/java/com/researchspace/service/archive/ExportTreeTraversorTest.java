package com.researchspace.service.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.archive.export.ExportArchiveTreeTraversor;
import com.researchspace.service.impl.FolderManagerImpl;
import com.researchspace.service.impl.MockFolderStructure;
import com.researchspace.testutils.FolderTestUtils;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExportTreeTraversorTest {

  ExportArchiveTreeTraversor traversor;
  User owner;
  Folder f1, f2, f3;
  UserFolderSetup setup;
  ArchiveExportConfig archiveCfg;

  @Mock FolderDao folderDao;
  FolderManager folderMgr;
  // the in-memory tree is only linked through child.parents, so the fake DAO searches these
  final List<BaseRecord> allRecords = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    owner = TestFactory.createAnyUser("any");
    MockFolderStructure structure = new MockFolderStructure();
    setup = FolderTestUtils.createDefaultFolderStructure(structure, owner, null);
    archiveCfg = new ArchiveExportConfig();
    resetTreeTraverser();

    f2 = createAFolder("f2");
    f3 = createAFolder("f3");
    setup.getUserRoot().addChild(f2, owner);
    f2.addChild(f3, owner);

    allRecords.addAll(structure.getSavedFolders());
    when(folderDao.getChildren(any()))
        .thenAnswer(
            inv ->
                allRecords.stream()
                    .filter(
                        r ->
                            r.getParents().stream()
                                .anyMatch(rtf -> rtf.getFolder() == inv.getArgument(0)))
                    .collect(Collectors.toList()));
    folderMgr = new FolderManagerImpl(null, folderDao, null);
  }

  @Test
  public void testProcessDoesntIncludeDeletedFolders() {
    folderMgr.process(setup.getUserRoot(), traversor);
    int beforeDeleteCount = folderTreeSize();
    resetTreeTraverser();
    f3.setRecordDeleted(true);
    folderMgr.process(setup.getUserRoot(), traversor);
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
    // Examples (holding the image) is only exported for a selection
    archiveCfg.setExportScope(ExportScope.SELECTION);
    EcatImage image = TestFactory.createEcatImage(3L);
    setup.getMediaImgExamples().addChild(image, owner);
    allRecords.add(image);
    folderMgr.process(setup.getUserRoot(), traversor);
    assertThat(traversor.getExportRecordList().getRecordsToExport()).contains(image.getOid());
    int beforeDeleteCount = recordsToExportCount();
    resetTreeTraverser();
    image.setRecordDeleted(true);
    folderMgr.process(setup.getUserRoot(), traversor);
    assertEquals(beforeDeleteCount - 1, recordsToExportCount());
  }

  private int recordsToExportCount() {
    return traversor.getExportRecordList().getRecordsToExport().size();
  }

  @Test
  public void testProcess() {
    final int expected = 3;
    f1 = createAFolder("f1");
    f1.addChild(f2, owner);
    folderMgr.process(f1, traversor);
    assertEquals(expected, folderTreeSize());
    assertEquals(0, recordsToExportCount());

    resetTreeTraverser();
    // ignore system folders
    f1.addType(RecordType.SYSTEM);
    f1.setName(Folder.SHARED_FOLDER_NAME);
    f1.setSystemFolder(true);
    folderMgr.process(f1, traversor);
    // f1 not processed, and neither are children
    assertEquals(0, folderTreeSize());
  }

  // RSPAC-1411
  @Test
  public void testProcessIncludeExampleFolderOrNot() {
    f1 = createAFolder(Folder.EXAMPLES_FOLDER);
    f1.setSystemFolder(true);
    f1.addChild(f2, owner);

    archiveCfg.setExportScope(ExportScope.SELECTION);
    folderMgr.process(f1, traversor);
    assertEquals(3, folderTreeSize());

    for (ExportScope scope : List.of(ExportScope.GROUP, ExportScope.USER)) {
      resetTreeTraverser();
      archiveCfg.setExportScope(scope);
      folderMgr.process(f1, traversor);
      assertEquals(0, folderTreeSize());
    }
  }

  @Test
  public void testProcessIncludeTemplateFolderOrNot() {
    final int expected = 3;
    f1 = TestFactory.createASystemFolder(Folder.TEMPLATE_MEDIA_FOLDER_NAME, owner);
    f1.setId(RandomUtils.nextLong());
    allRecords.add(f1);
    f1.addChild(f2, owner);
    archiveCfg.setExportScope(ExportScope.SELECTION);
    folderMgr.process(f1, traversor);

    assertEquals(expected, folderTreeSize());

    resetTreeTraverser();
    archiveCfg.setExportScope(ExportScope.GROUP);
    archiveCfg.setArchiveType(ArchiveExportConfig.HTML);
    folderMgr.process(f1, traversor);

    assertEquals(0, folderTreeSize());
    resetTreeTraverser();
    archiveCfg.setArchiveType(ArchiveExportConfig.XML);
    // only include  templates from XML
    folderMgr.process(f1, traversor);
    assertEquals(expected, folderTreeSize());

    resetTreeTraverser();
    archiveCfg.setExportScope(ExportScope.USER);

    archiveCfg.setArchiveType(ArchiveExportConfig.HTML);
    folderMgr.process(f1, traversor);
    assertEquals(0, folderTreeSize());
    resetTreeTraverser();
    // only include  templates from XML
    archiveCfg.setArchiveType(ArchiveExportConfig.XML);
    folderMgr.process(f1, traversor);
    // default export type is
    assertEquals(expected, folderTreeSize());
  }

  @Test
  public void testProcessIncludesGallery() {
    folderMgr.process(setup.getUserRoot(), traversor);
    assertThat(traversor.getExportRecordList().getFolderTree())
        .anyMatch(af -> af.getName().equals("Gallery"));
  }

  private Folder createAFolder(String name) {
    Folder f = TestFactory.createAFolder(name, owner);
    f.setId(RandomUtils.nextLong());
    allRecords.add(f);
    return f;
  }
}

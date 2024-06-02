package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.netfiles.NetFilesTestFactory;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.impl.NfsExportManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class NfsExportManagerTest extends SpringTransactionalTest {

  @Autowired private NfsExportManager nfsExportManager;

  @Autowired private DiskSpaceChecker diskSpaceChecker;

  private User user;
  private NfsFileSystem testFileSystem;
  private NfsFileStore testFileStore;

  @Before
  public void setUp() throws Exception {

    user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    testFileStore = NetFilesTestFactory.createAnyNfsFileStore(user);
    testFileSystem = testFileStore.getFileSystem();

    nfsMgr.saveNfsFileSystem(testFileSystem);
    nfsMgr.saveNfsFileStore(testFileStore);
  }

  @Test
  public void generateExportPlanForDocWithFileAndFolderLink() throws IOException {

    Long testFileSystemId = testFileSystem.getId();
    Long testFileStoreId = testFileStore.getId();
    String testNfsFilePath = "/nfs1.txt";
    String testNfsFolderPath = "/nfsFolder";
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "nfs links");
    addNfsFileStoreLink(doc.getFields().get(0), user, testFileStoreId, testNfsFilePath, false);
    addNfsFileStoreLink(doc.getFields().get(0), user, testFileStoreId, testNfsFolderPath, true);

    List<GlobalIdentifier> docsToExport = new ArrayList<>();
    docsToExport.add(doc.getOid());

    NfsExportPlan exportPlan = nfsExportManager.generateQuickExportPlan(docsToExport);
    assertNotNull(exportPlan);
    assertEquals(1, exportPlan.getFoundFileSystems().size());
    assertEquals(testFileSystemId, exportPlan.getFoundFileSystems().get(0).getId());
    assertEquals(1, exportPlan.getFoundFileStoresByIdMap().size());
    assertEquals(
        testFileStoreId, exportPlan.getFoundFileStoresByIdMap().get(testFileStoreId).getId());
    assertEquals(2, exportPlan.getFoundNfsLinks().size());
    assertEquals(2, exportPlan.getFoundFileSystems().get(0).getFoundNfsLinks().size());
    Iterator<NfsElement> foundLinksIterator = exportPlan.getFoundNfsLinks().values().iterator();
    assertEquals(testNfsFilePath, foundLinksIterator.next().getPath());
    assertEquals(testNfsFolderPath, foundLinksIterator.next().getPath());
    assertEquals(1, exportPlan.countFileSystemsRequiringLogin());
    assertEquals(0, exportPlan.getCheckedNfsLinks().size());
    assertEquals(0, exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size());

    // empty nfsClients map - user not yet logged anywhere
    Map<Long, NfsClient> nfsClients = new HashMap<>();
    nfsExportManager.checkLoggedAsStatusForFileSystemsInExportPlan(exportPlan, nfsClients, user);
    assertEquals(1, exportPlan.getFoundFileSystems().size());
    assertEquals(1, exportPlan.countFileSystemsRequiringLogin());
    assertEquals(0, exportPlan.getCheckedNfsLinks().size());
    assertEquals(0, exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size());

    // nfsClients map with mock client that is logged in and recognises path to test file
    NfsClient mockNfsClient = mock(NfsClient.class);
    when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    nfsClients.put(testFileSystemId, mockNfsClient);

    // should update plan so user appears logged in
    nfsExportManager.checkLoggedAsStatusForFileSystemsInExportPlan(exportPlan, nfsClients, user);
    assertEquals(1, exportPlan.getFoundFileSystems().size());
    assertEquals(0, exportPlan.countFileSystemsRequiringLogin());
    assertEquals(0, exportPlan.getCheckedNfsLinks().size());
    assertEquals(0, exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size());
  }

  @Test
  public void testFileSystemScanForFoundLinkDetails() throws IOException {

    // nfsClients map with mock client that is logged in
    Map<Long, NfsClient> nfsClients = new HashMap<>();
    NfsClient mockNfsClient = mock(NfsClient.class);
    when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    nfsClients.put(testFileSystem.getId(), mockNfsClient);

    // a plan with found filestore and element
    NfsExportPlan exportPlan = new NfsExportPlan();
    exportPlan.addFoundFileSystem(testFileSystem.toFileSystemInfo());
    exportPlan.addFoundFileStore(testFileStore);
    NfsElement testFileNfsLink = new NfsElement(testFileStore.getId(), "/test.txt");
    exportPlan.addFoundNfsLink(
        testFileSystem.getId(),
        testFileStore.getAbsolutePath(testFileNfsLink.getPath()),
        testFileNfsLink);

    // let's add broken nfs file link (the path that doesn't exist on connected filestore)
    NfsElement brokenFileNfsLink = new NfsElement(testFileStore.getId(), "/test_moved.txt");
    exportPlan.addFoundNfsLink(
        testFileSystem.getId(),
        testFileStore.getAbsolutePath(brokenFileNfsLink.getPath()),
        brokenFileNfsLink);

    // nfs client recognising path to a test file, but not to moved file
    NfsFileDetails testFileDetails = new NfsFileDetails("test.txt");
    when(mockNfsClient.queryForNfsFile(
            new NfsTarget(testFileStore.getPath() + "/" + testFileDetails.getName())))
        .thenReturn(testFileDetails);

    // scan should check the files, find both but second with connected 'not available' msg
    nfsExportManager.scanFileSystemsForFoundNfsLinks(exportPlan, nfsClients, null);
    assertEquals(2, exportPlan.getCheckedNfsLinks().size());
    assertEquals(2, exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size());
    Map<String, String> messages = exportPlan.getCheckedNfsLinkMessages();
    assertEquals(1, messages.size());
    assertEquals(
        NfsExportManagerImpl.RESOURCE_NOT_ACCESSIBLE_MSG, messages.values().iterator().next());
    assertTrue(messages.keySet().iterator().next().endsWith("test_moved.txt"));
    assertEquals(1, exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinkMessages().size());

    // verify the archive size limit properties are set
    assertEquals(diskSpaceChecker.getMaxArchiveSizeMB(), exportPlan.getMaxArchiveSizeMBProp());
    assertEquals(
        diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB(),
        exportPlan.getCurrentlyAllowedArchiveSizeMB());
  }
}

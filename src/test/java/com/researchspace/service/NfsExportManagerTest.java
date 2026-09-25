package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.impl.NfsExportManagerImpl;
import com.researchspace.testutils.NetFilesTestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class NfsExportManagerTest extends SpringTransactionalTest {

  @Autowired private NfsExportManager nfsExportManager;

  @Autowired private DiskSpaceChecker diskSpaceChecker;

  @Autowired private com.researchspace.service.MessageSourceUtils messageSource;

  private User user;
  private NfsFileSystem testFileSystem;
  private NfsFileStore testFileStore;

  @BeforeEach
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
    assertThat(exportPlan.getFoundFileSystems()).hasSize(1);
    assertEquals(testFileSystemId, exportPlan.getFoundFileSystems().get(0).getId());
    assertThat(exportPlan.getFoundFileStoresByIdMap()).hasSize(1);
    assertEquals(
        testFileStoreId, exportPlan.getFoundFileStoresByIdMap().get(testFileStoreId).getId());
    assertThat(exportPlan.getFoundNfsLinks()).hasSize(2);
    assertThat(exportPlan.getFoundFileSystems().get(0).getFoundNfsLinks()).hasSize(2);
    Iterator<NfsElement> foundLinksIterator = exportPlan.getFoundNfsLinks().values().iterator();
    assertEquals(testNfsFilePath, foundLinksIterator.next().getPath());
    assertEquals(testNfsFolderPath, foundLinksIterator.next().getPath());
    assertEquals(1, exportPlan.countFileSystemsRequiringLogin());
    assertThat(exportPlan.getCheckedNfsLinks()).isEmpty();
    assertThat(exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks()).isEmpty();

    // empty nfsClients map - user not yet logged anywhere
    Map<Long, NfsClient> nfsClients = new HashMap<>();
    nfsExportManager.checkLoggedAsStatusForFileSystemsInExportPlan(exportPlan, nfsClients, user);
    assertThat(exportPlan.getFoundFileSystems()).hasSize(1);
    assertEquals(1, exportPlan.countFileSystemsRequiringLogin());
    assertThat(exportPlan.getCheckedNfsLinks()).isEmpty();
    assertThat(exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks()).isEmpty();

    // nfsClients map with mock client that is logged in and recognises path to test file
    NfsClient mockNfsClient = mock(NfsClient.class);
    when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    nfsClients.put(testFileSystemId, mockNfsClient);

    // should update plan so user appears logged in
    nfsExportManager.checkLoggedAsStatusForFileSystemsInExportPlan(exportPlan, nfsClients, user);
    assertThat(exportPlan.getFoundFileSystems()).hasSize(1);
    assertEquals(0, exportPlan.countFileSystemsRequiringLogin());
    assertThat(exportPlan.getCheckedNfsLinks()).isEmpty();
    assertThat(exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks()).isEmpty();
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
    assertThat(exportPlan.getCheckedNfsLinks()).hasSize(2);
    assertThat(exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinks()).hasSize(2);
    Map<String, String> messages = exportPlan.getCheckedNfsLinkMessages();
    assertThat(messages).hasSize(1);
    assertEquals(
        messageSource.getMessage(NfsExportManagerImpl.RESOURCE_NOT_ACCESSIBLE_MSG_KEY),
        messages.values().iterator().next());
    assertThat(messages.keySet().iterator().next()).endsWith("test_moved.txt");
    assertThat(exportPlan.getFoundFileSystems().get(0).getCheckedNfsLinkMessages()).hasSize(1);

    // verify the archive size limit properties are set
    assertEquals(diskSpaceChecker.getMaxArchiveSizeMB(), exportPlan.getMaxArchiveSizeMBProp());
    assertEquals(
        diskSpaceChecker.getCurrentlyAllowedArchiveSizeMB(),
        exportPlan.getCurrentlyAllowedArchiveSizeMB());
  }
}

package com.researchspace.service;

import static com.researchspace.model.netfiles.NetFilesTestFactory.createAnyNfsFileSystem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.NfsDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NetFilesTestFactory;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

public class NfsManagerTest extends SpringTransactionalTest {

  private static final String USER = "user1a";

  @Autowired private NfsDao nfsDao;
  @Autowired private UserManager userManager;
  @Mock private FileStore fileStore;
  @Mock private NfsClient nfsClient;

  private NfsFileSystem testFileSystem;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    testFileSystem = createAnyNfsFileSystem();
    nfsMgr.setFileStore(fileStore);
  }

  @Test
  public void testSaveDeleteFilestore() {

    User user = userManager.getUserByUsername(USER);
    nfsMgr.saveNfsFileSystem(testFileSystem);

    int initialFolderCount = nfsMgr.getFileStoreInfosForUser(user).size();

    NfsFileStore fileStore = NetFilesTestFactory.createAnyNfsFileStore(user);
    fileStore.setFileSystem(testFileSystem);
    nfsMgr.saveNfsFileStore(fileStore);
    assertNotNull(fileStore.getId());
    assertFalse(fileStore.isDeleted());
    assertEquals(initialFolderCount + 1, nfsMgr.getFileStoreInfosForUser(user).size());
    assertNotNull(fileStore.getFileSystem());
    assertEquals(testFileSystem, fileStore.getFileSystem());

    nfsMgr.markFileStoreAsDeleted(fileStore);
    NfsFileStore deletedFolder = nfsMgr.getNfsFileStore(fileStore.getId());
    assertNotNull(deletedFolder);
    assertTrue(deletedFolder.isDeleted());
    assertEquals(initialFolderCount, nfsMgr.getFileStoreInfosForUser(user).size());
  }

  @Test
  public void saveDeleteTestFileSystem() {

    int initialFileSystemsCount = nfsMgr.getFileSystems().size();

    assertNull(testFileSystem.getId());
    nfsMgr.saveNfsFileSystem(testFileSystem);
    assertNotNull(testFileSystem.getId());
    assertFalse(testFileSystem.isDisabled());
    assertEquals(initialFileSystemsCount + 1, nfsMgr.getFileSystems().size());

    nfsMgr.deleteNfsFileSystem(testFileSystem.getId());
    assertEquals(initialFileSystemsCount, nfsMgr.getFileSystems().size());
  }

  @Test
  public void testListingFileSystems() {

    int initialFileSystemsCount = nfsMgr.getFileSystems().size();
    int initialActiveFileSystemsCount = nfsMgr.getActiveFileSystems().size();

    // add 2 filesystems
    NfsFileSystem fileSystem1 = new NfsFileSystem();
    NfsFileSystem fileSystem2 = new NfsFileSystem();
    fileSystem2.setDisabled(true);

    nfsMgr.saveNfsFileSystem(fileSystem1);
    nfsMgr.saveNfsFileSystem(fileSystem2);

    assertEquals(initialFileSystemsCount + 2, nfsMgr.getFileSystems().size());
    assertEquals(initialActiveFileSystemsCount + 1, nfsMgr.getActiveFileSystems().size());

    nfsMgr.deleteNfsFileSystem(fileSystem1.getId());
    nfsMgr.deleteNfsFileSystem(fileSystem2.getId());
    assertEquals(initialFileSystemsCount, nfsMgr.getFileSystems().size());
  }

  @Test
  public void testCantDeleteFileSystemWithExistingFileStore() {
    NfsFileSystem fileSystem = new NfsFileSystem();
    nfsMgr.saveNfsFileSystem(fileSystem);

    NfsFileStore fileStore = new NfsFileStore();
    fileStore.setFileSystem(fileSystem);
    nfsMgr.saveNfsFileStore(fileStore);

    boolean deleted = nfsMgr.deleteNfsFileSystem(fileSystem.getId());
    assertFalse(deleted);
    NfsFileSystem retrievedFileSystem = nfsMgr.getFileSystem(fileSystem.getId());
    assertNotNull(retrievedFileSystem);

    // user marked file store as deleted, but they might created links using it, so still don't
    // allow
    nfsMgr.markFileStoreAsDeleted(fileStore);

    boolean deletedAgain = nfsMgr.deleteNfsFileSystem(fileSystem.getId());
    assertFalse(deletedAgain);
    NfsFileSystem retrievedAgain = nfsMgr.getFileSystem(fileSystem.getId());
    assertNotNull(retrievedAgain);

    // real delete of a file store
    nfsDao.deleteNfsFileStore(fileStore);
    boolean finalDelete = nfsMgr.deleteNfsFileSystem(fileSystem.getId());
    assertTrue(finalDelete);

    try {
      nfsMgr.getFileSystem(fileSystem.getId());
      fail();
    } catch (IllegalStateException e) {
      // expected
    }
    NfsFileSystem finalRetrieve = nfsDao.getNfsFileSystem(fileSystem.getId());
    assertNull(finalRetrieve);
  }

  @Test
  public void testUploadFiles() throws IOException, UnsupportedOperationException {
    List<EcatMediaFile> listRecordToMove = List.of(new EcatImage(), new EcatAudio());

    when(nfsClient.supportWritePermission()).thenReturn(true);
    when(fileStore.findFile(any())).thenReturn(new File(""));

    nfsMgr.uploadFilesToNfs(listRecordToMove, "", nfsClient);

    verify(fileStore, times(2)).findFile(any());
    verify(nfsClient).uploadFilesToNfs(any(), any());
  }
}

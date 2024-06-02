package com.researchspace.dao.customliquibaseupdates.v26;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.service.NfsManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/* this class was used for testing 0.26 upgrade and is now obsolete */
@Ignore
public class NfsFileSystemImporter0_26IT extends RealTransactionSpringTestBase {

  @Autowired private NfsManager netFilesManager;

  @Autowired private NfsDao nfsDao;

  private NfsFileSystemImporter nfsFileSystemImporter;

  private static NfsFileStore testPreExistingFileStore;
  private static NfsFileSystem testImportedFileSystem;

  @Before
  public void before() {
    // created outside spring, as it will be created by liquibase in reality
    nfsFileSystemImporter = new NfsFileSystemImporter();

    if (testPreExistingFileStore == null) {
      testPreExistingFileStore = new NfsFileStore();
      netFilesManager.saveNfsFileStore(testPreExistingFileStore);
    }
  }

  @After
  public void cleanUp() throws Exception {

    testPreExistingFileStore.setFileSystem(null);
    netFilesManager.saveNfsFileStore(testPreExistingFileStore);

    if (testImportedFileSystem != null && testImportedFileSystem.getId() != null) {
      netFilesManager.deleteNfsFileSystem(testImportedFileSystem.getId());
      testImportedFileSystem = null;
    }

    super.tearDown();
  }

  @Test
  public void testImportSambaFileSystemConfig() throws SetupException, CustomChangeException {

    checkJustOnePreExistingFileStore();

    /* creating test config */
    String testFileSystemName = "test Samba FileSystem Name";
    String testFileSystemUrl = "smb://samba.test";
    String testSambaDomain = "ED";

    NfsFileSystemDeploymentPropertyProvider sambaPropertyProvider =
        new NfsFileSystemDeploymentPropertyProvider();
    sambaPropertyProvider.setFileStoresEnabled("true");
    sambaPropertyProvider.setFileStoresName(testFileSystemName);
    sambaPropertyProvider.setServerUrl(testFileSystemUrl);
    sambaPropertyProvider.setAuthTypeString("password");
    sambaPropertyProvider.setClientTypeString("samba");
    sambaPropertyProvider.setSambaUserDomain(testSambaDomain);

    List<NfsFileSystem> initialFileSystems = netFilesManager.getFileSystems();
    assertTrue(
        "there shouldn't be any file systems on initial import", initialFileSystems.isEmpty());

    /* run update class */
    openTransaction();
    nfsFileSystemImporter.setUp();
    nfsFileSystemImporter.setPropertyProvider(sambaPropertyProvider);
    nfsFileSystemImporter.execute(null);
    commitTransaction();

    /* checking imported filesystem */
    List<NfsFileSystem> newFileSystems = netFilesManager.getFileSystems();

    assertNotNull(newFileSystems);
    assertEquals(1, newFileSystems.size());

    NfsFileSystem nfsFileSystem = newFileSystems.get(0);
    testImportedFileSystem = nfsFileSystem; // for cleanup

    assertNotNull(nfsFileSystem);
    assertFalse(nfsFileSystem.isDisabled());
    assertEquals(testFileSystemName, nfsFileSystem.getName());
    assertEquals(testFileSystemUrl, nfsFileSystem.getUrl());
    assertEquals(NfsClientType.SAMBA, nfsFileSystem.getClientType());
    assertEquals(
        NfsFileSystemOption.SAMBA_DOMAIN + "=" + testSambaDomain + "\n",
        nfsFileSystem.getClientOptions());
    assertEquals(NfsAuthenticationType.PASSWORD, nfsFileSystem.getAuthType());
    assertEquals("", nfsFileSystem.getAuthOptions());

    checkPreExistingFileStorePointsToFileSystem();
  }

  @Test
  public void testImportSftpFileSystemConfig() throws SetupException, CustomChangeException {

    checkJustOnePreExistingFileStore();

    /* creating test config */
    String testFileSystemName = "test Sftp FileSystem Name";
    String testFileSystemUrl = "sftp://sftp.test";
    String testPubKeyDialogUrl = "http://test.url";
    String testSftpServerPublicKey =
        "AAAAB3NzaC1yc2EAAAABIwAAAQEAwXzR98FvQaeMwpMy+sC/yPmnftzsj948ik9LtmZb"
            + "zfSe36woJSOT9Q+qhhnBNchBpfodwTIEkQ8ZrwFEO/eLaJVfILWFSG6swsbOOeun1en2ALkmoiKOA3jGQMRn88u6bXXH"
            + "T3Q7996yUPTRhO0OK1juMVgkD+ZQt2wj7DCZqZ5hNv4IiEeC/Z0mZDxpTTJgeJVSkIqQtIkKvJJeizN2VDwRGYUoz1Se"
            + "QI05V7hvLSqeRmmvMz2aDBDb2TURFZPZSE5dwKhRzMMBjOeAMcglzRvWfrmO7rcv+Z77gwJFjwzymZ4QOJDUXAAbCAmS"
            + "hRlFIPZesHGJFIf7f6BrPhMxdQ==";

    NfsFileSystemDeploymentPropertyProvider sambaPropertyProvider =
        new NfsFileSystemDeploymentPropertyProvider();
    sambaPropertyProvider.setFileStoresEnabled("false");
    sambaPropertyProvider.setFileStoresName(testFileSystemName);
    sambaPropertyProvider.setServerUrl(testFileSystemUrl);
    sambaPropertyProvider.setAuthTypeString("pubKey");
    sambaPropertyProvider.setPubKeyRegistrationDialogUrl(testPubKeyDialogUrl);
    sambaPropertyProvider.setClientTypeString("sftp");
    sambaPropertyProvider.setSftpServerPubKey(testSftpServerPublicKey);

    List<NfsFileSystem> initialFileSystems = netFilesManager.getFileSystems();
    assertTrue(
        "there shouldn't be any file systems on initial import", initialFileSystems.isEmpty());

    /* run update class */
    openTransaction();
    nfsFileSystemImporter.setUp();
    nfsFileSystemImporter.setPropertyProvider(sambaPropertyProvider);
    nfsFileSystemImporter.execute(null);
    commitTransaction();

    /* checking imported filesystem */
    List<NfsFileSystem> newFileSystems = netFilesManager.getFileSystems();

    assertNotNull(newFileSystems);
    assertEquals(1, newFileSystems.size());

    NfsFileSystem nfsFileSystem = newFileSystems.get(0);
    testImportedFileSystem = nfsFileSystem; // for cleanup

    assertNotNull(nfsFileSystem);
    assertTrue(nfsFileSystem.isDisabled());
    assertEquals(testFileSystemName, nfsFileSystem.getName());
    assertEquals(testFileSystemUrl, nfsFileSystem.getUrl());
    assertEquals(NfsClientType.SFTP, nfsFileSystem.getClientType());
    assertEquals(
        NfsFileSystemOption.SFTP_SERVER_PUBLIC_KEY + "=" + testSftpServerPublicKey + "\n",
        nfsFileSystem.getClientOptions());
    assertEquals(NfsAuthenticationType.PUBKEY, nfsFileSystem.getAuthType());
    assertEquals(
        NfsFileSystemOption.PUBLIC_KEY_REGISTRATION_DIALOG_URL + "=" + testPubKeyDialogUrl + "\n",
        nfsFileSystem.getAuthOptions());

    checkPreExistingFileStorePointsToFileSystem();
  }

  private void checkJustOnePreExistingFileStore() {
    openTransaction();
    List<NfsFileStore> fileStores = nfsDao.getFileStores();
    commitTransaction();
    assertEquals(1, fileStores.size());
  }

  private void checkPreExistingFileStorePointsToFileSystem() {
    openTransaction();
    List<NfsFileStore> fileStores = nfsDao.getFileStores();
    commitTransaction();
    assertEquals(1, fileStores.size());

    testPreExistingFileStore = fileStores.get(0);
    assertNotNull(
        "preexisting file store should point to newly imported file system",
        testPreExistingFileStore.getFileSystem());
    assertEquals(testImportedFileSystem.getId(), testPreExistingFileStore.getFileSystem().getId());
  }
}

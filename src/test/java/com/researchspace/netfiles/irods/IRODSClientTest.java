package com.researchspace.netfiles.irods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.domain.Collection;
import org.irods.jargon.core.pub.domain.DataObject;
import org.irods.jargon.core.pub.io.IRODSFileInputStream;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class IRODSClientTest {

  private NfsFileStore testFileStore;

  @Mock private JargonFacade jargonFacade;

  @Mock private IRODSAccount irodsAccount;

  @Mock private IRODSFileInputStream mockInputStream;

  @InjectMocks private IRODSClient subjectUnderTest;

  private static final String IRODS_HOME_DIR = "/tempZone/home/alice";
  private static final String IRODS_ABS_PATH = "/var/lib/irods/Vault";

  @BeforeEach
  public void setup() {
    openMocks(this);
    when(irodsAccount.getUserName()).thenReturn("alice");
    when(irodsAccount.getHomeDirectory()).thenReturn(IRODS_HOME_DIR);
    testFileStore = new NfsFileStore();
    testFileStore.setId(1L);
    testFileStore.setPath(IRODS_HOME_DIR);
  }

  @Test
  void testTryAndConnectToTarget() throws JargonException, MalformedURLException, NfsException {
    subjectUnderTest.tryConnectAndReadTarget(IRODS_HOME_DIR);
    verify(jargonFacade).tryConnectAndReadTarget(IRODS_HOME_DIR, irodsAccount);
  }

  @Test
  void testFileTreeCreationSimpleCase() throws NfsException, JargonException {
    when(jargonFacade.getListAllDataObjectsAndCollectionsUnderPath(IRODS_HOME_DIR, irodsAccount))
        .thenReturn(getTestListingForUserHomeDirectory());
    NfsFileTreeNode fileTreeNode =
        subjectUnderTest.createFileTree(IRODS_HOME_DIR, "", testFileStore);
    // Check root node has path set
    assertEquals(IRODS_HOME_DIR, fileTreeNode.getNodePath());
    assertEquals(2, fileTreeNode.getNodes().size());
    List<NfsFileTreeNode> nodes = fileTreeNode.getNodes();
    // Check first node has /tempZone/home/alice/test as path and nfsId is set
    assertEquals("/tempZone/home/alice/test", nodes.get(0).getNodePath());
    assertEquals(10045L, nodes.get(0).getNfsId());
    // Check second node has /tempZone/home/alice/training_jpgs as path and nfsId is set
    assertEquals("/tempZone/home/alice/training_jpgs", nodes.get(1).getNodePath());
    assertEquals(10024L, nodes.get(1).getNfsId());

    verify(jargonFacade).getListAllDataObjectsAndCollectionsUnderPath(IRODS_HOME_DIR, irodsAccount);
  }

  @Test
  void testFileTreeCreationWithFiles() throws JargonException, NfsException {
    String testPath = IRODS_HOME_DIR + "/training_jpgs";
    when(jargonFacade.getListAllDataObjectsAndCollectionsUnderPath(testPath, irodsAccount))
        .thenReturn(getTestListingForFolderWithFiles());
    NfsFileTreeNode fileTreeNode = subjectUnderTest.createFileTree(testPath, "", testFileStore);
    // Check root node has path set
    assertEquals(testPath, fileTreeNode.getNodePath());
    assertEquals(2, fileTreeNode.getNodes().size());
    List<NfsFileTreeNode> nodes = fileTreeNode.getNodes();
    // Check first node has /tempZone/home/alice/test as path and nfsId is set
    assertEquals("coffee.jpg", nodes.get(0).getNodePath());
    assertEquals("1:/training_jpgs/coffee.jpg", nodes.get(0).getLogicPath());
    assertEquals(10030L, nodes.get(0).getNfsId());
    // Check second node has /tempZone/home/alice/training_jpgs as path and nfsId is set
    assertEquals("eggs.jpg", nodes.get(1).getNodePath());
    assertEquals("1:/training_jpgs/eggs.jpg", nodes.get(1).getLogicPath());
    assertEquals(10028L, nodes.get(1).getNfsId());

    verify(jargonFacade).getListAllDataObjectsAndCollectionsUnderPath(testPath, irodsAccount);
  }

  @Test
  void testFileTreeCreationNfsExceptionThrownFromJargonException()
      throws NfsException, JargonException {
    when(jargonFacade.getListAllDataObjectsAndCollectionsUnderPath(IRODS_HOME_DIR, irodsAccount))
        .thenThrow(new JargonException("Error from Jargon"));
    assertThrows(NfsException.class, () -> subjectUnderTest.createFileTree("", "", testFileStore));
  }

  @Test
  void testQueryForNfsFileByIdWhenNfsIdPassedIn() throws JargonException {
    when(jargonFacade.getIRODSDataObjectById(10024L, irodsAccount))
        .thenReturn(getTestIRODSDataObject());
    NfsFileDetails fileDetails =
        subjectUnderTest.queryForNfsFile(new NfsTarget("beans.jpg", 10024L));
    assertEquals("beans.jpg", fileDetails.getName());
    assertEquals(10024L, fileDetails.getNfsId());
    assertEquals(1000L, fileDetails.getSize());
    assertEquals(IRODS_HOME_DIR + "/training_jpgs/beans.jpg", fileDetails.getFileSystemFullPath());
    assertNull(fileDetails.getRemoteInputStream());
    verify(jargonFacade).getIRODSDataObjectById(10024L, irodsAccount);
  }

  @Test
  void testQueryForNfsFileByPathWhenIdIsNull() throws JargonException {
    String testFilePath = IRODS_HOME_DIR + "/training_jpgs/beans.jpg";
    when(jargonFacade.getIRODSDataObjectByPath(testFilePath, irodsAccount))
        .thenReturn(getTestIRODSDataObject());
    NfsFileDetails fileDetails =
        subjectUnderTest.queryForNfsFile(new NfsTarget(testFilePath, null));
    assertEquals("beans.jpg", fileDetails.getName());
    assertEquals(10024L, fileDetails.getNfsId());
    assertEquals(1000L, fileDetails.getSize());
    assertEquals(testFilePath, fileDetails.getFileSystemFullPath());
    assertNull(fileDetails.getRemoteInputStream());
    verify(jargonFacade).getIRODSDataObjectByPath(testFilePath, irodsAccount);
  }

  @Test
  void testQueryNfsFileForDownloadByIdWhenNfsIdPassedIn() throws JargonException, IOException {
    when(jargonFacade.getIRODSDataObjectById(10024L, irodsAccount))
        .thenReturn(getTestIRODSDataObject());
    when(jargonFacade.getIRODSFileInputStreamById(10024L, irodsAccount))
        .thenReturn(mockInputStream);
    NfsFileDetails fileDetails =
        subjectUnderTest.queryNfsFileForDownload(new NfsTarget("beans.jpg", 10024L));
    assertEquals("beans.jpg", fileDetails.getName());
    assertEquals(10024L, fileDetails.getNfsId());
    assertEquals(1000L, fileDetails.getSize());
    assertEquals(IRODS_HOME_DIR + "/training_jpgs/beans.jpg", fileDetails.getFileSystemFullPath());
    assertEquals(mockInputStream, fileDetails.getRemoteInputStream());
    verify(jargonFacade).getIRODSDataObjectById(10024L, irodsAccount);
  }

  @Test
  void testQueryNfsFileForDownloadByPathWhenNfsIdIsNull() throws JargonException, IOException {
    String testFilePath = IRODS_HOME_DIR + "/training_jpgs/beans.jpg";
    when(jargonFacade.getIRODSDataObjectByPath(testFilePath, irodsAccount))
        .thenReturn(getTestIRODSDataObject());
    when(jargonFacade.getIRODSFileInputStreamById(10024L, irodsAccount))
        .thenReturn(mockInputStream);
    NfsFileDetails fileDetails =
        subjectUnderTest.queryNfsFileForDownload(new NfsTarget(testFilePath, null));
    assertEquals("beans.jpg", fileDetails.getName());
    assertEquals(10024L, fileDetails.getNfsId());
    assertEquals(1000L, fileDetails.getSize());
    assertEquals(IRODS_HOME_DIR + "/training_jpgs/beans.jpg", fileDetails.getFileSystemFullPath());
    assertEquals(mockInputStream, fileDetails.getRemoteInputStream());
    verify(jargonFacade).getIRODSDataObjectByPath(testFilePath, irodsAccount);
    verify(jargonFacade).getIRODSFileInputStreamById(10024L, irodsAccount);
  }

  @Test
  void testQueryForNfsFolderById() throws JargonException, IOException {
    when(jargonFacade.getIRODSCollectionById(10045L, irodsAccount))
        .thenReturn(getTestIRODSCollection());
    when(jargonFacade.getListAllDataObjectsAndCollectionsUnderPath(
            IRODS_HOME_DIR + "/test", irodsAccount))
        .thenReturn(getTestListingForFolderWithFileAndCollection());
    NfsFolderDetails folderDetails = subjectUnderTest.queryForNfsFolder(new NfsTarget("", 10045L));
    assertEquals(IRODS_HOME_DIR + "/test", folderDetails.getFileSystemFullPath());
    assertEquals(IRODS_HOME_DIR, folderDetails.getFileSystemParentPath());
    assertEquals(10045L, folderDetails.getNfsId());
    assertEquals(2, folderDetails.getContent().size());
  }

  private List<CollectionAndDataObjectListingEntry> getTestListingForUserHomeDirectory() {
    List<CollectionAndDataObjectListingEntry> testEntries = new ArrayList<>();
    CollectionAndDataObjectListingEntry entry1 = new CollectionAndDataObjectListingEntry();
    entry1.setParentPath(IRODS_HOME_DIR);
    entry1.setPathOrName("/tempZone/home/alice/test");
    entry1.setObjectType(CollectionAndDataObjectListingEntry.ObjectType.COLLECTION);
    entry1.setCreatedAt(new Date());
    entry1.setModifiedAt(new Date());
    entry1.setDataSize(0);
    entry1.setOwnerName("alice");
    entry1.setOwnerZone("tempZone");
    entry1.setId(10045);

    CollectionAndDataObjectListingEntry entry2 = new CollectionAndDataObjectListingEntry();
    entry2.setParentPath(IRODS_HOME_DIR);
    entry2.setPathOrName("/tempZone/home/alice/training_jpgs");
    entry2.setObjectType(CollectionAndDataObjectListingEntry.ObjectType.COLLECTION);
    entry2.setCreatedAt(new Date());
    entry2.setModifiedAt(new Date());
    entry2.setDataSize(0);
    entry2.setOwnerName("alice");
    entry2.setOwnerZone("tempZone");
    entry2.setId(10024);

    testEntries.add(entry1);
    testEntries.add(entry2);
    return testEntries;
  }

  private List<CollectionAndDataObjectListingEntry> getTestListingForFolderWithFiles() {
    List<CollectionAndDataObjectListingEntry> testEntries = new ArrayList<>();
    CollectionAndDataObjectListingEntry entry1 = new CollectionAndDataObjectListingEntry();
    entry1.setParentPath("/tempZone/home/alice/training_jpgs");
    entry1.setPathOrName("coffee.jpg");
    entry1.setObjectType(CollectionAndDataObjectListingEntry.ObjectType.DATA_OBJECT);
    entry1.setCreatedAt(new Date());
    entry1.setModifiedAt(new Date());
    entry1.setDataSize(479299);
    entry1.setOwnerName("alice");
    entry1.setOwnerZone("tempZone");
    entry1.setId(10030);

    CollectionAndDataObjectListingEntry entry2 = new CollectionAndDataObjectListingEntry();
    entry2.setParentPath("/tempZone/home/alice/training_jpgs");
    entry2.setPathOrName("eggs.jpg");
    entry2.setObjectType(CollectionAndDataObjectListingEntry.ObjectType.DATA_OBJECT);
    entry2.setCreatedAt(new Date());
    entry2.setModifiedAt(new Date());
    entry2.setDataSize(912548);
    entry2.setOwnerName("alice");
    entry2.setOwnerZone("tempZone");
    entry2.setId(10028);

    testEntries.add(entry1);
    testEntries.add(entry2);
    return testEntries;
  }

  private List<CollectionAndDataObjectListingEntry> getTestListingForFolderWithFileAndCollection() {
    List<CollectionAndDataObjectListingEntry> testEntries = new ArrayList<>();
    CollectionAndDataObjectListingEntry entry1 = new CollectionAndDataObjectListingEntry();
    entry1.setParentPath("/tempZone/home/alice/test");
    entry1.setPathOrName("coffee.jpg");
    entry1.setObjectType(CollectionAndDataObjectListingEntry.ObjectType.DATA_OBJECT);
    entry1.setCreatedAt(new Date());
    entry1.setModifiedAt(new Date());
    entry1.setDataSize(479299);
    entry1.setOwnerName("alice");
    entry1.setOwnerZone("tempZone");
    entry1.setId(10030);

    CollectionAndDataObjectListingEntry entry2 = new CollectionAndDataObjectListingEntry();
    entry2.setParentPath("/tempZone/home/alice/test");
    entry2.setPathOrName("/tempZone/home/alice/test/anotherFolder");
    entry2.setObjectType(CollectionAndDataObjectListingEntry.ObjectType.COLLECTION);
    entry2.setCreatedAt(new Date());
    entry2.setModifiedAt(new Date());
    entry2.setDataSize(0);
    entry2.setOwnerName("alice");
    entry2.setOwnerZone("tempZone");
    entry2.setId(10049);

    testEntries.add(entry1);
    testEntries.add(entry2);
    return testEntries;
  }

  private DataObject getTestIRODSDataObject() {
    DataObject testDataObject = new DataObject();
    testDataObject.setDataName("beans.jpg");
    testDataObject.setCollectionName(IRODS_HOME_DIR + "/training_jpgs");
    testDataObject.setId(10024);
    testDataObject.setDataSize(1000L);
    testDataObject.setDataPath(IRODS_ABS_PATH + "/home/alice/training_jpgs/beans.jpg");
    return testDataObject;
  }

  private Collection getTestIRODSCollection() {
    Collection testCollection = new Collection();
    testCollection.setCollectionName("/tempZone/home/alice/test");
    testCollection.setCollectionId(10045);
    testCollection.setCollectionParentName(IRODS_HOME_DIR);

    return testCollection;
  }
}

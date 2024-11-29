package com.researchspace.webapp.controller;

import static com.researchspace.model.netfiles.NfsFileSystemOption.USER_DIRS_REQUIRED;
import static com.researchspace.webapp.controller.NfsController.MODEL_SHOW_EXTRA_DIRS_FLAG;
import static com.researchspace.webapp.controller.NfsController.NEED_LOG_IN_MSG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.netfiles.NfsViewProperty;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserKeyManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringEscapeUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

public class NfsControllerTest extends SpringTransactionalTest {

  @Autowired private NfsController controller;

  @Autowired private UserKeyManager userKeyManager;

  private User testUser;
  private String testUsername;
  private Principal principalStub;

  private Model model;
  private HttpServletRequest request;
  private MockHttpServletResponse response;

  private static final long TEST_FILE_STORE_ID = 10L;
  private static final String TEST_FILE_STORE_NAME = "testFileStoreName";
  private static final String TEST_FILE_STORE_PATH = "testFileStorePath";

  private static final long TEST_FILE_SYSTEM_ID = 11L;
  private static final String TEST_FILE_SYSTEM_NAME = "testFileSystem";
  private static final String TEST_FILE_SYSTEM_URL = "smb://test.com";

  private NfsFileStore testNfsFileStore;
  private ArrayList<NfsFileStoreInfo> testNfsFileStoreInfoList;

  private NfsFileSystem testNfsFileSystem;
  private ArrayList<NfsFileSystemInfo> testNfsFileSystemInfoList;

  private NfsManager nfsManagerMock;
  private NfsClient nfsClientMock;
  private NfsAuthentication nfsAuthenticationMock;

  @Before
  public void setUp() throws IllegalAddChildOperation {

    model = new ExtendedModelMap();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();

    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("nfs"));
    testUsername = testUser.getUsername();

    initialiseContentWithExampleContent(testUser);
    assertTrue(testUser.isContentInitialized());
    principalStub = new MockPrincipal(testUsername);

    testNfsFileSystem = new NfsFileSystem();
    testNfsFileSystem.setId(TEST_FILE_SYSTEM_ID);
    testNfsFileSystem.setName(TEST_FILE_SYSTEM_NAME);
    testNfsFileSystem.setClientType(NfsClientType.SAMBA);
    testNfsFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
    testNfsFileSystem.setUrl(TEST_FILE_SYSTEM_URL);

    testNfsFileSystemInfoList = new ArrayList<>();
    testNfsFileSystemInfoList.add(testNfsFileSystem.toFileSystemInfo());

    testNfsFileStore = new NfsFileStore();
    testNfsFileStore.setId(TEST_FILE_STORE_ID);
    testNfsFileStore.setName(TEST_FILE_STORE_NAME);
    testNfsFileStore.setPath(TEST_FILE_STORE_PATH);
    testNfsFileStore.setUser(testUser);
    testNfsFileStore.setFileSystem(testNfsFileSystem);

    testNfsFileStoreInfoList = new ArrayList<>();
    testNfsFileStoreInfoList.add(testNfsFileStore.toFileStoreInfo());

    nfsClientMock = mock(NfsClient.class);
    when(nfsClientMock.isUserLoggedIn()).thenReturn(true);
    when(nfsClientMock.getUsername()).thenReturn(testUsername);

    nfsAuthenticationMock = mock(NfsAuthentication.class);
    when(nfsAuthenticationMock.validateCredentials(null, null, testUser))
        .thenReturn("testUsername.required");
    when(nfsAuthenticationMock.login(
            eq(testUsername), anyString(), eq(testNfsFileSystem), eq(testUser)))
        .thenReturn(nfsClientMock);

    nfsManagerMock = mock(NfsManager.class);
    when(nfsManagerMock.getNfsFileStore(TEST_FILE_STORE_ID)).thenReturn(testNfsFileStore);
    when(nfsManagerMock.getFileSystem(TEST_FILE_SYSTEM_ID)).thenReturn(testNfsFileSystem);

    controller.setNfsManager(nfsManagerMock);
  }

  @Test
  public void getNetFilesViewTest() {

    controller.getNetFilesGalleryView(model, principalStub);
    assertEquals("[]", model.asMap().get(NfsViewProperty.FILE_SYSTEMS_JSON.toString()));
    assertEquals("[]", model.asMap().get(NfsViewProperty.FILE_STORES_JSON.toString()));
    assertEquals(null, model.asMap().get(NfsViewProperty.PUBLIC_KEY.toString()));

    when(nfsManagerMock.getFileStoreInfosForUser(testUser)).thenReturn(testNfsFileStoreInfoList);
    when(nfsManagerMock.getActiveFileSystemInfos()).thenReturn(testNfsFileSystemInfoList);
    controller.getNetFilesGalleryView(model, principalStub);

    // filesystem json double-escaped (RSPAC-2360)
    String filesystemJson =
        "{\"id\":11,\"name\":\"testFileSystem\",\"url\":\"smb://test.com\","
            + "\"clientType\":\"SAMBA\",\"authType\":\"PASSWORD\",\"options\":{},\"loggedAs\":null}";
    String expectedFilesystemJson = StringEscapeUtils.escapeJavaScript(filesystemJson);
    assertEquals(
        "[" + expectedFilesystemJson + "]",
        model.asMap().get(NfsViewProperty.FILE_SYSTEMS_JSON.toString()));

    // filestores json double-escaped (RSPAC-2090)
    String filestoreJson =
        "{\"id\":10,\"name\":\"testFileStoreName\",\"path\":\"testFileStorePath\","
            + "\"fileSystem\":"
            + filesystemJson
            + "}";
    String expectedFilestoreJson = StringEscapeUtils.escapeJavaScript(filestoreJson);
    assertEquals(
        "[" + expectedFilestoreJson + "]",
        model.asMap().get(NfsViewProperty.FILE_STORES_JSON.toString()));
    assertEquals(null, model.asMap().get(NfsViewProperty.PUBLIC_KEY.toString()));
  }

  @Test
  public void getNetFilesViewUserDirsRequiredTest() {
    testNfsFileSystem.setClientOptions(USER_DIRS_REQUIRED + "=true");
    testNfsFileSystemInfoList.clear();
    testNfsFileSystemInfoList.add(testNfsFileSystem.toFileSystemInfo());

    when(nfsManagerMock.getFileStoreInfosForUser(testUser)).thenReturn(testNfsFileStoreInfoList);
    when(nfsManagerMock.getActiveFileSystemInfos()).thenReturn(testNfsFileSystemInfoList);
    controller.getNetFilesGalleryView(model, principalStub);

    // filesystem json double-escaped (RSPAC-2360)
    String filesystemJson =
        "{\"id\":11,\"name\":\"testFileSystem\",\"url\":\"smb://test.com\","
            + "\"clientType\":\"SAMBA\",\"authType\":\"PASSWORD\",\"options\":{\"USER_DIRS_REQUIRED\":\"true\"},\"loggedAs\":null}";
    String expectedFilesystemJson = StringEscapeUtils.escapeJavaScript(filesystemJson);
    assertEquals(
        "[" + expectedFilesystemJson + "]",
        model.asMap().get(NfsViewProperty.FILE_SYSTEMS_JSON.toString()));
  }

  @Test
  public void getNetFilesLoginViewUserDirsRequiredTest() {
    testNfsFileSystem.setClientOptions(USER_DIRS_REQUIRED + "=true");
    testNfsFileSystemInfoList.clear();
    testNfsFileSystemInfoList.add(testNfsFileSystem.toFileSystemInfo());

    when(nfsManagerMock.getFileStoreInfosForUser(testUser)).thenReturn(testNfsFileStoreInfoList);
    when(nfsManagerMock.getActiveFileSystemInfos()).thenReturn(testNfsFileSystemInfoList);
    controller.getNetFilesLoginView(model, principalStub);

    // filesystem json double-escaped (RSPAC-2360)
    String filesystemJson =
        "{\"id\":11,\"name\":\"testFileSystem\",\"url\":\"smb://test.com\","
            + "\"clientType\":\"SAMBA\",\"authType\":\"PASSWORD\",\"options\":{\"USER_DIRS_REQUIRED\":\"true\"},\"loggedAs\":null}";
    String expectedFilesystemJson = StringEscapeUtils.escapeJavaScript(filesystemJson);
    assertEquals(
        "[" + expectedFilesystemJson + "]",
        model.asMap().get(NfsViewProperty.FILE_SYSTEMS_JSON.toString()));
  }

  @Test
  public void testLoginToFileSystem() {

    String unloggedMsg =
        controller.tryConnectToFileSystemRoot(TEST_FILE_SYSTEM_ID, null, request, principalStub);
    assertEquals(NEED_LOG_IN_MSG, unloggedMsg);

    loginTestUserToTestFileSystem();

    String msg =
        controller.tryConnectToFileSystemRoot(TEST_FILE_SYSTEM_ID, null, request, principalStub);
    assertEquals(NfsManager.LOGGED_AS_MSG + testUsername, msg);
  }

  @Test
  public void testLoginToFileSystemUserDirSpecified() {
    loginTestUserToTestFileSystem("USER_NAME");

    String msg =
        controller.tryConnectToFileSystemRoot(
            TEST_FILE_SYSTEM_ID, "USER_NAME", request, principalStub);
    assertEquals(NfsManager.LOGGED_AS_MSG + testUsername, msg);
  }

  @Test
  public void testConnectToUserFileStore() {

    String unloggedMsg =
        controller.tryConnectToFileStore(TEST_FILE_STORE_ID, request, principalStub);
    assertEquals(NEED_LOG_IN_MSG, unloggedMsg);

    loginTestUserToTestFileSystem(testNfsFileStore.getPath());

    String msg = controller.tryConnectToFileStore(TEST_FILE_STORE_ID, request, principalStub);
    assertEquals(NfsManager.LOGGED_AS_MSG + testUsername, msg);
  }

  @Test
  public void testDeleteFileStore() {

    loginTestUserToTestFileSystem();

    String result = controller.deleteFileStore(TEST_FILE_STORE_ID, principalStub);
    assertEquals(NfsController.SUCCESS_MSG, result);
    verify(nfsManagerMock).markFileStoreAsDeleted(testNfsFileStore);
  }

  @Test
  public void testGetSimpleNfsFileTree() throws IOException {

    loginTestUserToTestFileSystem();

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.calculateFileName("testTreeNodeName");
    when(nfsClientMock.createFileTree(anyString(), anyString(), eq(testNfsFileStore)))
        .thenReturn(node);
    when(nfsClientMock.supportsExtraDirs()).thenReturn(true);
    controller.retrieveNfsFileTree(
        null, TEST_FILE_STORE_ID, "", "byname", request, model, principalStub);
    verify(nfsClientMock).createFileTree("" + TEST_FILE_STORE_PATH, "byname", testNfsFileStore);

    NfsFileTreeNode retrievedRootNode =
        (NfsFileTreeNode) model.asMap().get(NfsController.MODEL_TREE_NODE);
    assertNotNull(retrievedRootNode);
    assertEquals(node.getFileName(), retrievedRootNode.getFileName());

    assertTrue((Boolean) model.getAttribute(MODEL_SHOW_EXTRA_DIRS_FLAG));

    assertEquals(0, retrievedRootNode.getNodes().size());
  }

  @Test
  public void testGetSimpleNfsFileTreeUserDirsRequired() throws IOException {

    loginTestUserToTestFileSystem();
    testNfsFileSystem.setClientOptions(USER_DIRS_REQUIRED.toString() + "=true");
    NfsFileTreeNode node = new NfsFileTreeNode();
    node.calculateFileName("testTreeNodeName");
    when(nfsClientMock.createFileTree(anyString(), anyString(), eq(testNfsFileStore)))
        .thenReturn(node);
    when(nfsClientMock.supportsExtraDirs()).thenReturn(true);
    controller.retrieveNfsFileTree(
        null, TEST_FILE_STORE_ID, "", "byname", request, model, principalStub);
    assertFalse((Boolean) model.getAttribute(MODEL_SHOW_EXTRA_DIRS_FLAG));
  }

  @Test
  public void testLoginToNfs() {
    String loginResult =
        controller.loginToNfs(1L, "username", "password", "target_dir", request, principalStub);
    assertEquals(NEED_LOG_IN_MSG, loginResult);
    when(nfsManagerMock.loginToNfs(
            eq(TEST_FILE_SYSTEM_ID),
            eq("username"),
            eq("password"),
            anyMap(),
            anyObject(),
            eq("target_dir")))
        .thenReturn("logged.as.username");
    loginResult =
        controller.loginToNfs(
            TEST_FILE_SYSTEM_ID, "username", "password", "target_dir", request, principalStub);
    assertEquals("logged.as.username", loginResult);
    loginResult =
        controller.loginToNfs(
            TEST_FILE_SYSTEM_ID, "username", "password", "/target_dir", request, principalStub);
    assertEquals("No paths in directory name", loginResult);
    loginResult =
        controller.loginToNfs(
            TEST_FILE_SYSTEM_ID, "username", "password", "\\target_dir", request, principalStub);
    assertEquals("No paths in directory name", loginResult);
  }

  @Test
  public void testTreeListingWithNonAsciiCharsEncodedInDirParam() throws IOException {

    loginTestUserToTestFileSystem();

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.calculateFileName("testTreeNodeName");
    when(nfsClientMock.createFileTree(anyString(), anyString(), eq(testNfsFileStore)))
        .thenReturn(node);

    String dirParam = "samba-folder%2F%2FBio%20Multi%20Me%C3%9Fsystem%2F";
    String expectedDir = "samba-folder/Bio Multi Meßsystem/";

    controller.retrieveNfsFileTree(
        null, TEST_FILE_STORE_ID, dirParam, "byname", request, model, principalStub);
    verify(nfsClientMock).createFileTree(eq(expectedDir), anyString(), eq(testNfsFileStore));
  }

  @Test
  public void testHappyNfsFileDownload() throws IOException {

    final String testDownlodaFileName = "testDownloadFileName";
    final String testDownloadFileContent = "testDownloadFileContent";
    final String testFilePath = TEST_FILE_STORE_ID + ":" + testDownlodaFileName;
    final NfsTarget testTarget = new NfsTarget(testDownlodaFileName, null);

    String unloggedResult =
        controller.prepareNfsFileForDownload(
            testFilePath, testTarget.getNfsId(), request, principalStub);
    assertEquals(getMsgFromResourceBundler("need.log.in", TEST_FILE_SYSTEM_NAME), unloggedResult);

    loginTestUserToTestFileSystem();

    NfsFileDetails testFileDetails = new NfsFileDetails(testDownlodaFileName);
    testFileDetails.setRemoteInputStream(
        new ByteArrayInputStream(testDownloadFileContent.getBytes()));
    when(nfsClientMock.queryNfsFileForDownload(any(NfsTarget.class))).thenReturn(testFileDetails);

    String loggedResult =
        controller.prepareNfsFileForDownload(
            testFilePath, testTarget.getNfsId(), request, principalStub);
    assertEquals(NfsController.SUCCESS_MSG, loggedResult);

    String downloadPath =
        (String) request.getSession().getAttribute(NfsController.SESSION_NFS_DOWNLOAD_PATH);
    assertNotNull(downloadPath);

    File tempFile = new File(downloadPath);
    File tempParentFolder = tempFile.getParentFile();
    assertTrue("download path should point to temp file", tempFile.exists());
    assertEquals(testDownlodaFileName, tempFile.getName());

    controller.downloadNfsFile(request, response);
    assertEquals("application/octet-stream", response.getContentType());
    assertEquals(testDownloadFileContent, response.getContentAsString());

    assertFalse("after download file should be deleted", tempFile.exists());
    assertFalse("temp parent folder should be deleted", tempParentFolder.exists());
  }

  @Test
  public void registerSshKeyForNewUserIT() {

    /* this test uses real (autowired) userKeyManager */

    controller.getNetFilesGalleryView(model, principalStub);
    assertEquals(null, model.asMap().get(NfsViewProperty.PUBLIC_KEY.toString()));

    // generating the key
    String returnedKey = controller.registerNewKeyForNfs(principalStub);

    // checking that key is in the db
    UserKeyPair registeredKeyPair = userKeyManager.getUserKeyPair(testUser);
    assertNotNull(
        "controller should register new key for testUser, but is null", registeredKeyPair);
    assertEquals(returnedKey, registeredKeyPair.getPublicKey());

    // reloaded view should contain new public key
    controller.getNetFilesGalleryView(model, principalStub);
    assertEquals(
        StringEscapeUtils.escapeJavaScript(returnedKey),
        model.asMap().get(NfsViewProperty.PUBLIC_KEY.toString()));
  }

  @Test
  public void getNfsFileStoreInfo() {
    AjaxReturnObject<NfsFileStoreInfo> fileStoreResp =
        controller.getNfsFileStoreInfo(TEST_FILE_STORE_ID);
    NfsFileStoreInfo fileStoreInfo = fileStoreResp.getData();

    assertEquals(TEST_FILE_STORE_ID, fileStoreInfo.getId().longValue());
    assertEquals(TEST_FILE_STORE_NAME, fileStoreInfo.getName());
    assertEquals(TEST_FILE_STORE_PATH, fileStoreInfo.getPath());
    assertEquals(TEST_FILE_SYSTEM_ID, fileStoreInfo.getFileSystem().getId().longValue());
    assertEquals(TEST_FILE_SYSTEM_NAME, fileStoreInfo.getFileSystem().getName());
    assertEquals(TEST_FILE_SYSTEM_URL, fileStoreInfo.getFileSystem().getUrl());
  }

  private void loginTestUserToTestFileSystem(String target) {
    // mock session and manager as if user logged in
    Map<Long, NfsClient> nfsClients = new HashMap<>();
    nfsClients.put(TEST_FILE_SYSTEM_ID, nfsClientMock);
    request.getSession().setAttribute(NfsController.SESSION_NFS_CLIENTS, nfsClients);

    when(nfsManagerMock.checkIfUserLoggedIn(TEST_FILE_SYSTEM_ID, nfsClients, testUser))
        .thenReturn(true);
    when(nfsManagerMock.testConnectionToTarget(
            eq(target), eq(TEST_FILE_SYSTEM_ID), eq(nfsClientMock)))
        .thenReturn("logged.as." + testUsername);
  }

  private void loginTestUserToTestFileSystem() {
    loginTestUserToTestFileSystem("");
  }
}

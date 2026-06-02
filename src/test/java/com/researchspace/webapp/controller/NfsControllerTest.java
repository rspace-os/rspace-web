package com.researchspace.webapp.controller;

import static com.researchspace.model.netfiles.NfsFileSystemOption.USER_DIRS_REQUIRED;
import static com.researchspace.webapp.controller.NfsController.NEED_LOG_IN_MSG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.netfiles.NfsViewProperty;
import com.researchspace.service.NfsManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

public class NfsControllerTest extends SpringTransactionalTest {

  @Autowired private NfsController controller;

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

    nfsManagerMock = mock(NfsManager.class);
    when(nfsManagerMock.getNfsFileStore(TEST_FILE_STORE_ID)).thenReturn(testNfsFileStore);
    when(nfsManagerMock.getFileSystem(TEST_FILE_SYSTEM_ID)).thenReturn(testNfsFileSystem);

    controller.setNfsManager(nfsManagerMock);
  }

  @Test
  public void getNetFilesLoginViewUserDirsRequiredTest() {
    testNfsFileSystem.setClientOptions(USER_DIRS_REQUIRED + "=true");
    testNfsFileSystemInfoList.clear();
    testNfsFileSystemInfoList.add(testNfsFileSystem.toFileSystemInfo());

    when(nfsManagerMock.getFileStoreInfosForUser(testUser)).thenReturn(testNfsFileStoreInfoList);
    when(nfsManagerMock.getActiveFileSystemInfos(testUser)).thenReturn(testNfsFileSystemInfoList);
    controller.getNetFilesLoginView(model, principalStub);

    // filesystem json double-escaped (RSPAC-2360)
    String filesystemJson =
        "{\"id\":11,\"name\":\"testFileSystem\",\"url\":\"smb://test.com\","
            + "\"clientType\":\"SAMBA\",\"authType\":\"PASSWORD\",\"options\":{\"USER_DIRS_REQUIRED\":\"true\"},\"loggedAs\":null}";
    String expectedFilesystemJson = StringEscapeUtils.escapeEcmaScript(filesystemJson);
    assertEquals(
        "[" + expectedFilesystemJson + "]",
        model.asMap().get(NfsViewProperty.FILE_SYSTEMS_JSON.toString()));
  }

  @Test
  public void testLoginToNfs() {
    // before stubbing manager.loginToNfs the call returns NEED_LOG_IN_MSG
    String loginResult =
        controller.loginToNfs(
            TEST_FILE_SYSTEM_ID, "username", "password", "target_dir", request, principalStub);
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
  public void loginToNfsWithJson_userNotOnReadAllowlistForNoneAuth_throwsAuthorizationException() {
    // ACL check should fire before nfsManager.loginToNfs for NONE-auth filesystems
    testNfsFileSystem.setAuthType(NfsAuthenticationType.NONE);
    testNfsFileSystem.setReadAllowlist("someoneElse");
    testNfsFileSystem.setWriteAllowlist(null);
    controller.setAclChecker(GalleryFilestoreTestUtils.filestoreAclCheckerForTest());

    NfsController.NfsLoginData data =
        new NfsController.NfsLoginData(TEST_FILE_SYSTEM_ID, "u", "p", null);
    try {
      controller.loginToNfsWithJson(data, request, principalStub);
      org.junit.Assert.fail("expected AuthorizationException");
    } catch (AuthorizationException expected) {
      // expected
    }
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

  private void loginTestUserToTestFileSystem() {
    // mock session and manager as if user logged in
    Map<Long, NfsClient> nfsClients = new HashMap<>();
    nfsClients.put(TEST_FILE_SYSTEM_ID, nfsClientMock);
    request.getSession().setAttribute(NfsController.SESSION_NFS_CLIENTS, nfsClients);

    when(nfsManagerMock.checkIfUserLoggedIn(TEST_FILE_SYSTEM_ID, nfsClients, testUser))
        .thenReturn(true);
  }
}

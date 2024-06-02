package com.researchspace.webapp.integrations.box;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFile.Info;
import com.box.sdk.BoxFileVersion;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxSharedLink;
import com.researchspace.model.field.ErrorList;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.io.OutputStream;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class BoxControllerMVCIT extends MVCTestBase {

  private @Autowired BoxController boxController;

  private BoxConnector mockBoxConnector;
  private BoxAPIConnection mockConnection;

  private MockMvc mockMvc;
  private MockHttpSession mockSession;

  private String mockBoxFileId = "1234";
  private String mockBoxFileVersionId = "12345678";
  private String mockBoxFolderId = "2345";
  private String mockBoxUnknownId = "1111";

  @Before
  public void setUp() {

    mockBoxConnector = mock(BoxConnector.class);
    boxController.setBoxConnector(mockBoxConnector);

    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    mockSession = new MockHttpSession();

    mockConnection = mock(BoxAPIConnection.class);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  @Ignore // ignore for open-source, as requires valid box client id to run
  public void testAuthorizationFlow() throws Exception {

    when(mockBoxConnector.createBoxAPIConnection(anyString(), anyString(), anyString()))
        .thenReturn(mockConnection);
    when(mockBoxConnector.restoreBoxAPIConnection(anyString(), anyString(), anyString()))
        .thenReturn(mockConnection);
    when(mockConnection.save()).thenReturn("savedState");

    /* OAuth authorization error flow */
    MvcResult authErrorResult =
        this.mockMvc
            .perform(get("/box/redirect_uri").param("error", "access_denied").session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/authorizationError"))
            .andReturn();

    assertEquals("access_denied", getAuthError(authErrorResult).getErrorMsg());
    assertNull(boxController.restoreBoxApiConnection(mockSession));

    /* OAuth authorization without security token in session */
    MvcResult noTokenInSessionResult =
        this.mockMvc
            .perform(
                get("/box/redirect_uri")
                    .param("code", "code1234")
                    .param("state", "state1234")
                    .session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/authorizationError"))
            .andReturn();

    assertEquals("invalid_token", getAuthError(noTokenInSessionResult).getErrorMsg());
    assertNull(boxController.restoreBoxApiConnection(mockSession));

    /* requesting security token for session */
    MvcResult unauthorizedResult =
        this.mockMvc
            .perform(get("/box/boxApiAvailabilityCheck").session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    Map json = parseJSONObjectFromResponseStream(unauthorizedResult);
    String securityToken = json.get("data").toString().substring("USER_NOT_AUTHORIZED:".length());

    /* OAuth authorization with incorrect security token */
    MvcResult tokenErrorResult =
        this.mockMvc
            .perform(
                get("/box/redirect_uri")
                    .param("code", "code1234")
                    .param("state", "state1234")
                    .session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/authorizationError"))
            .andReturn();

    assertEquals("invalid_token", getAuthError(tokenErrorResult).getErrorMsg());
    assertNull(boxController.restoreBoxApiConnection(mockSession));

    // OAuth success flow */
    this.mockMvc
        .perform(
            get("/box/redirect_uri")
                .param("code", "code1234")
                .param("state", securityToken)
                .session(mockSession))
        .andExpect(status().isOk())
        .andExpect(view().name("connect/box/connected"))
        .andReturn();
    assertNotNull(boxController.restoreBoxApiConnection(mockSession));

    /* exception in authorization process when initialising/testing user connection with Box */
    int exceptionCode = 400;
    BoxAPIException someException = new BoxAPIException("Authorization problem", exceptionCode, "");
    when(mockBoxConnector.testBoxConnection(any(BoxAPIConnection.class))).thenThrow(someException);

    MvcResult otherErrorResult =
        this.mockMvc
            .perform(
                get("/box/redirect_uri")
                    .param("code", "code1234")
                    .param("state", securityToken)
                    .session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/authorizationError"))
            .andReturn();

    assertTrue(getAuthError(otherErrorResult).getErrorMsg().contains(exceptionCode + ""));
    assertTrue(
        getAuthError(otherErrorResult)
            .getErrorDetails()
            .contains(BoxController.AUTHORIZATION_ERROR_MSG));
  }

  private OauthAuthorizationError getAuthError(MvcResult otherErrorResult) {
    return (OauthAuthorizationError) otherErrorResult.getModelAndView().getModel().get("error");
  }

  @Test
  public void testGettingFileDetails() throws Exception {

    setUpMockBoxFile();
    setUpMockBoxFolder();

    // unauthorized user
    MvcResult unauthorizedResult =
        this.mockMvc
            .perform(
                get("/box/boxResourceDetails").param("boxId", mockBoxFileId).session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    ErrorList errorList = getErrorListFromAjaxReturnObject(unauthorizedResult);
    assertEquals(1, errorList.getErrorMessages().size());
    assertEquals(BoxController.USER_NOT_AUTHORIZED, errorList.getErrorMessages().get(0));

    setUserAsAuthorizedForBoxAPI();

    // retrieving file details
    MvcResult fileResult =
        this.mockMvc
            .perform(
                get("/box/boxResourceDetails").param("boxId", mockBoxFileId).session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    errorList = getErrorListFromAjaxReturnObject(fileResult);
    assertNull(errorList);

    BoxResourceInfo retrievedFileInfo =
        getFromJsonAjaxReturnObject(fileResult, BoxResourceInfo.class);
    assertNotNull(retrievedFileInfo);
    assertEquals(mockBoxFileId, retrievedFileInfo.getId());
    assertFalse(retrievedFileInfo.isFolder());

    // retrieving folder details
    MvcResult folderResult =
        this.mockMvc
            .perform(
                get("/box/boxResourceDetails").param("boxId", mockBoxFolderId).session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    errorList = getErrorListFromAjaxReturnObject(fileResult);
    assertNull(errorList);

    BoxResourceInfo retrievedFolderInfo =
        getFromJsonAjaxReturnObject(folderResult, BoxResourceInfo.class);
    assertNotNull(retrievedFolderInfo);
    assertEquals(mockBoxFolderId, retrievedFolderInfo.getId());
    assertTrue(retrievedFolderInfo.isFolder());

    // other error & connection reset on unknown resource id
    assertNotNull(mockSession.getAttribute(BoxController.SESSION_BOX_API_CONNECTION));
    MvcResult unknownResourceResult =
        this.mockMvc
            .perform(
                get("/box/boxResourceDetails")
                    .param("boxId", mockBoxUnknownId)
                    .session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    errorList = getErrorListFromAjaxReturnObject(unknownResourceResult);
    assertEquals(1, errorList.getErrorMessages().size());
    assertEquals(BoxController.API_OTHER_ERROR, errorList.getErrorMessages().get(0));
    assertNull(mockSession.getAttribute(BoxController.SESSION_BOX_API_CONNECTION));
  }

  @Test
  public void testFileVersionDownload() throws Exception {

    setUpMockBoxFile();

    // unauthorized user
    MvcResult unauthorizedResult =
        this.mockMvc
            .perform(
                get("/box/downloadBoxFile")
                    .session(mockSession)
                    .param("boxId", mockBoxFileId)
                    .param("boxVersionID", mockBoxFileVersionId)
                    .param("boxName", "testName"))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        BoxController.USER_NOT_AUTHORIZED, unauthorizedResult.getResponse().getContentAsString());

    setUserAsAuthorizedForBoxAPI();

    // happy case
    MvcResult fileDownloadResult =
        this.mockMvc
            .perform(
                get("/box/downloadBoxFile")
                    .session(mockSession)
                    .param("boxId", mockBoxFileId)
                    .param("boxVersionID", mockBoxFileVersionId)
                    .param("boxName", "testName"))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals("testMockFileContent", fileDownloadResult.getResponse().getContentAsString());

    // trying download unknown resource id
    assertNotNull(mockSession.getAttribute(BoxController.SESSION_BOX_API_CONNECTION));
    MvcResult unexistingDownloadResult =
        this.mockMvc
            .perform(
                get("/box/downloadBoxFile")
                    .session(mockSession)
                    .param("boxId", mockBoxUnknownId)
                    .param("boxVersionID", mockBoxFileVersionId)
                    .param("boxName", "testName"))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        BoxController.DOWNLOAD_ERROR_MSG,
        unexistingDownloadResult.getResponse().getContentAsString());
    assertNull(mockSession.getAttribute(BoxController.SESSION_BOX_API_CONNECTION));
  }

  private void setUpMockBoxFile() {
    BoxFile testBoxFile = mock(BoxFile.class);
    Info testBoxFileInfo = mock(Info.class);
    BoxSharedLink testBoxSharedLink = mock(BoxSharedLink.class);
    com.box.sdk.BoxUser.Info testBoxOwner = mock(com.box.sdk.BoxUser.Info.class);
    BoxFileVersion testBoxVersion = mock(BoxFileVersion.class);

    when(testBoxFileInfo.getID()).thenReturn(mockBoxFileId);
    when(testBoxFileInfo.getResource()).thenReturn(testBoxFile);
    when(testBoxFileInfo.getSharedLink()).thenReturn(testBoxSharedLink);
    when(testBoxFileInfo.getOwnedBy()).thenReturn(testBoxOwner);
    when(testBoxFileInfo.getVersion()).thenReturn(testBoxVersion);
    when(testBoxVersion.getID()).thenReturn(mockBoxFileVersionId);

    doAnswer(
            new Answer<Void>() {
              @Override
              public Void answer(InvocationOnMock invocation) throws Throwable {
                OutputStream stream = (OutputStream) invocation.getArguments()[0];
                stream.write("testMockFileContent".getBytes());
                return null;
              }
            })
        .when(testBoxFile)
        .download(any(OutputStream.class));

    when(mockBoxConnector.getBoxFileInfo(any(BoxAPIConnection.class), eq(mockBoxFileId)))
        .thenReturn(testBoxFileInfo);

    BoxAPIException notFoundException = new BoxAPIException("", 404, "");
    when(mockBoxConnector.getBoxFileInfo(any(BoxAPIConnection.class), eq(mockBoxFolderId)))
        .thenThrow(notFoundException);
    when(mockBoxConnector.getBoxFileInfo(any(BoxAPIConnection.class), eq(mockBoxUnknownId)))
        .thenThrow(notFoundException);
  }

  private void setUpMockBoxFolder() {
    BoxFolder testBoxFolder = mock(BoxFolder.class);
    com.box.sdk.BoxFolder.Info testBoxFolderInfo = mock(com.box.sdk.BoxFolder.Info.class);
    BoxSharedLink testBoxSharedLink = mock(BoxSharedLink.class);
    com.box.sdk.BoxUser.Info testBoxOwner = mock(com.box.sdk.BoxUser.Info.class);

    when(testBoxFolderInfo.getID()).thenReturn(mockBoxFolderId);
    when(testBoxFolderInfo.getResource()).thenReturn(testBoxFolder);
    when(testBoxFolderInfo.getSharedLink()).thenReturn(testBoxSharedLink);
    when(testBoxFolderInfo.getOwnedBy()).thenReturn(testBoxOwner);

    when(mockBoxConnector.getBoxFolderInfo(any(BoxAPIConnection.class), eq(mockBoxFolderId)))
        .thenReturn(testBoxFolderInfo);

    BoxAPIException notFoundException = new BoxAPIException("", 404, "");
    when(mockBoxConnector.getBoxFolderInfo(any(BoxAPIConnection.class), eq(mockBoxFileId)))
        .thenThrow(notFoundException);
    when(mockBoxConnector.getBoxFolderInfo(any(BoxAPIConnection.class), eq(mockBoxUnknownId)))
        .thenThrow(notFoundException);
  }

  private void setUserAsAuthorizedForBoxAPI() {
    mockSession.setAttribute(BoxController.SESSION_BOX_API_CONNECTION, "test");
    when(mockBoxConnector.restoreBoxAPIConnection(anyString(), anyString(), anyString()))
        .thenReturn(mockConnection);
  }
}

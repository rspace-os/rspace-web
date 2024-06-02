package com.researchspace.webapp.integrations.wopi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.MediaFileLockHandler;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.wopi.WopiAccessTokenHandler.WopiAccessToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class WopiControllerTest extends SpringTransactionalTest {

  @Autowired private WopiController wopiController;

  @Autowired private WopiAccessTokenHandler accessTokenHandler;

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @Autowired private WopiDiscoveryProcessor discoveryProcessor;

  private MediaFileLockHandler testLockHandler;

  private User testUser;
  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    WopiTestUtilities.setWopiDiscoveryFromExampleFile(
        discoveryServiceHandler, discoveryProcessor, WopiTestUtilities.MSOFFICE_DISCOVERY_XML_FILE);
    testUser = doCreateAndInitUser(getRandomAlphabeticString("wopi"));

    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();

    // spying on lock handler, so we can get put additional assertion checks
    testLockHandler = spy(MediaFileLockHandler.class);
    wopiController.setLockHandler(testLockHandler);
  }

  @Test
  public void testCheckFileInfoOperation() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msDoc = addDocumentFromTestResourcesToGallery("MSattachment.doc", testUser);
    String fileId = msDoc.getGlobalIdentifier();

    // verify that file details are retrieved
    Map<String, Object> response = wopiController.checkFileInfo(fileId, testUser);
    assertEquals(200, resp.getStatus());
    assertNotNull(response);

    // required properties
    assertEquals("MSattachment.doc", response.get("BaseFileName"));
    assertEquals("" + msDoc.getOwner().getId(), response.get("OwnerId"));
    assertEquals(msDoc.getSize(), response.get("Size"));
    assertEquals("" + testUser.getId(), response.get("UserId"));
    assertEquals("1", response.get("Version"));

    // other properties
    assertEquals("" + testUser.getDisplayName(), response.get("UserFriendlyName"));
    assertEquals(true, response.get("UserCanWrite"));
    assertEquals(true, response.get("UserCanRename"));
    assertEquals(false, response.get("UserCanNotWriteRelative")); // doc extension is convertible
    assertEquals(true, response.get("LicenseCheckForEditIsEnabled"));
    assertEquals(
        propertyHolder.getServerUrl() + "/globalId/" + fileId, response.get("DownloadUrl"));
    assertEquals(propertyHolder.getServerUrl() + "/logout", response.get("SignoutUrl"));
  }

  @Test
  public void testCheckFileInfoReturningProperPermissionsForSharedFile()
      throws IOException, URISyntaxException {

    // create a group
    User owner = createAndSaveAPi();
    User otherUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(owner, otherUser);
    Group group = createGroup("wopiGroup", owner);
    addUsersToGroup(owner, group, otherUser);

    // create a document with attachment
    logoutAndLoginAs(owner);
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(owner, "any");
    Field field = basicDoc.getFields().iterator().next();
    File txtFile = RSpaceTestUtils.getAnyAttachment();
    EcatDocumentFile docFile = addAttachmentDocumentToField(txtFile, field, owner);
    RSpaceTestUtils.logout();

    // generate access tokens for users
    String fileId = docFile.getGlobalIdentifier();

    // verify owner has edit permission
    Map<String, Object> ownerResponse = wopiController.checkFileInfo(fileId, owner);
    assertEquals(200, resp.getStatus());
    assertNotNull(ownerResponse);
    assertEquals(true, ownerResponse.get("UserCanWrite"));
    assertEquals(true, ownerResponse.get("UserCanRename"));

    // verify other user can't view the attachment
    resp = new MockHttpServletResponse();
    try {
      wopiController.checkFileInfo(fileId, otherUser);
      fail("authorization error expected");
    } catch (AuthorizationException ae) {
      // expected
    }

    // share the doc for 'view'
    logoutAndLoginAs(owner);
    shareRecordWithGroup(owner, group, basicDoc);
    RSpaceTestUtils.logout();

    // verify sharee can now view, but not edit, the attachment
    resp = new MockHttpServletResponse();
    Map<String, Object> otherUserViewResponse = wopiController.checkFileInfo(fileId, otherUser);
    assertEquals(200, resp.getStatus());
    assertNotNull(otherUserViewResponse);
    assertEquals(false, otherUserViewResponse.get("UserCanWrite"));
    assertEquals(false, otherUserViewResponse.get("UserCanRename"));

    // re-share the doc for 'edit'
    logoutAndLoginAs(owner);
    List<RecordGroupSharing> sharingInfos = sharingMgr.getRecordSharingInfo(basicDoc.getId());
    RecordGroupSharing basicDocSharingInfo = sharingInfos.get(0);
    sharingMgr.updatePermissionForRecord(basicDocSharingInfo.getId(), "write", owner.getUsername());
    RSpaceTestUtils.logout();

    // verify sharee can now edit the attachment
    resp = new MockHttpServletResponse();
    Map<String, Object> otherUserEditResponse = wopiController.checkFileInfo(fileId, otherUser);
    assertEquals(200, resp.getStatus());
    assertNotNull(otherUserEditResponse);
    assertEquals(true, otherUserEditResponse.get("UserCanWrite"));
    assertEquals(true, otherUserEditResponse.get("UserCanRename"));
  }

  @Test
  public void testGetFileOperation() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();

    wopiController.getFile(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("1", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));

    byte[] responseBytes = resp.getContentAsByteArray();
    assertNotNull(responseBytes);
    assertEquals(msExcel.getSize(), responseBytes.length);
  }

  @Test
  public void testLockOperation() throws IOException, URISyntaxException {

    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String lockId = "wopiClientLockId";
    String anotherLockId = "wopiClientAnotherLockId";

    /* "If the file is currently unlocked, the host should lock the file and return 200 OK." */
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    verify(testLockHandler).lock(fileId, lockId);

    /* "If the file is currently locked and the X-WOPI-Lock value matches the lock currently
     * on the file, the host should treat the request as if it is a RefreshLock request.
     * That is, the host should refresh the lock timer and return 200 OK." */
    resp = new MockHttpServletResponse();
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("1", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));
    verify(testLockHandler, times(2)).lock(fileId, lockId);

    /* "In all other cases, the host must return a “lock mismatch” response (409 Conflict) and include
     * an X-WOPI-Lock response header containing the value of the current lock on the file." */
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, anotherLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals(lockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler, times(1)).lock(fileId, anotherLockId);
  }

  @Test
  public void testGetLockOperation() throws IOException, URISyntaxException {

    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String lockId = "wopiClientLockId";

    /* "If the file is currently not locked, the host must return a 200 OK and include
     * an X-WOPI-Lock response header set to the empty string." */
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_GET_LOCK);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("", resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler).getLock(fileId);

    // lock the file
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("1", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));

    // check the lock again
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_GET_LOCK);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals(lockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler, times(2)).getLock(fileId);
  }

  @Test
  public void testRefreshLockOperation() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String lockId = "lockId";
    String anotherLockId = "anotherLockId";

    // correct headers, but no lock
    /* "If the file is unlocked, the host must return a “lock mismatch” response (409 Conflict)" */
    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_REFRESH_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals("", resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler).refreshLock(fileId, lockId);

    // lock the file
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("1", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));

    // correct headers
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_REFRESH_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    verify(testLockHandler, times(2)).refreshLock(fileId, lockId);

    // refresh with wrong lockId
    /* "If the file is currently locked and the X-WOPI-Lock value does not match the lock
     * currently on the file, the host must return a “lock mismatch” response (409 Conflict)" */
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_REFRESH_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, anotherLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals(lockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler, times(1)).refreshLock(fileId, anotherLockId);
  }

  @Test
  public void testUnlockOperation() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String lockId = "lockId";
    String anotherLockId = "anotherId";

    // correct headers, but no lock
    /* "In the case where the file is unlocked, the host must return a “lock mismatch” response
     * (409 Conflict)  and set X-WOPI-Lock to the empty string."  */
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_UNLOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals("", resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler).unlock(fileId, lockId);

    // lock the file
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("1", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));

    // unlock but wrong lockId
    /* "If the file is currently locked and the X-WOPI-Lock value does not match the lock
     * currently on the file, the host must return a “lock mismatch” response (409 Conflict)
     * and include an X-WOPI-Lock response header containing the value of the current lock on the file. */
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_UNLOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, anotherLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals(lockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler).unlock(fileId, anotherLockId);

    // correct unlock
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_UNLOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("1", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));
    verify(testLockHandler, times(2)).unlock(fileId, lockId);
  }

  @Test
  public void testUnlockAndRelockOperation() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String oldLockId = "dummyOldLockId";
    String newLockId = "dummyNewLockId";

    /* try unlock and relock with correct headers, but unlocked file.
     * "In the case where the file is unlocked, the host must return a “lock mismatch”
     * response (409 Conflict) and set X-WOPI-Lock to the empty string."
     */
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, newLockId);
    req.addHeader(WopiController.X_WOPI_OLD_LOCK_HEADER, oldLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals("", resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler).unlockAndRelock(fileId, oldLockId, newLockId);

    // lock the file
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, oldLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());

    // unlock and relock
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, newLockId);
    req.addHeader(WopiController.X_WOPI_OLD_LOCK_HEADER, oldLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    verify(testLockHandler, times(2)).unlockAndRelock(fileId, oldLockId, newLockId);

    /* "If the file is currently locked and the X-WOPI-OldLock value does not match the lock currently
     * on the file, the host must return a “lock mismatch” response (409 Conflict) and include
     * an X-WOPI-Lock response header containing the value of the current lock on the file. */
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, newLockId);
    req.addHeader(WopiController.X_WOPI_OLD_LOCK_HEADER, oldLockId);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals(newLockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));
    verify(testLockHandler, times(3)).unlockAndRelock(fileId, oldLockId, newLockId);
  }

  @Test
  public void testLocksEndpointGeneralErrors() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String lockId = "dummyLockId";

    // no override header
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(400, resp.getStatus());
    verify(testLockHandler, never()).lock(fileId, lockId);

    // unknown override header
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, "dummy");
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(400, resp.getStatus());
    verify(testLockHandler, never()).lock(fileId, lockId);
  }

  @Test
  public void testPutFileOperation() throws IOException, URISyntaxException {

    EcatDocumentFile emptyFile = addDocumentFromTestResourcesToGallery("empty.txt", testUser);
    String emptyFileId = emptyFile.getGlobalIdentifier();

    EcatDocumentFile csvFile = addDocumentFromTestResourcesToGallery("csv.csv", testUser);
    String csvFileId = csvFile.getGlobalIdentifier();

    String lockId = "wopiClientLockId";
    String anotherLockId = "wopiClientAnotherLockId";
    final String updatedContent = "1,2,3,updatedContent";

    /* "If the file is currently unlocked, the host must check the current size of the file.
     *  If it is 0 bytes, the PutFile request should be considered valid and should proceed." */
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, anotherLockId);
    req.setContent(updatedContent.getBytes());

    wopiController.putFileOperations(emptyFileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("2", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));

    /* If the file size is other than 0 bytes, or is missing altogether, the host should respond
     * with a 409 Conflict and set X-WOPI-Lock to the empty string. " */
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, anotherLockId);
    req.setContent(updatedContent.getBytes());

    wopiController.putFileOperations(csvFileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals("", resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));

    // lock the file now, with lockId string
    testLockHandler.lock(csvFileId, lockId);

    /* "If the file is currently locked and the X-WOPI-Lock value does not match the lock currently
     *  on the file the host must return a “lock mismatch” response (409 Conflict) and include
     *  an X-WOPI-Lock response header containing the value of the current lock on the file." */
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, anotherLockId);
    req.setContent(updatedContent.getBytes());

    wopiController.putFileOperations(csvFileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals(lockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));

    // happy path
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT);
    req.addHeader(WopiController.X_WOPI_LOCK_HEADER, lockId);
    req.setContent(updatedContent.getBytes());

    wopiController.putFileOperations(csvFileId, testUser, req, resp);
    assertEquals(200, resp.getStatus());
    assertEquals("2", resp.getHeader(WopiController.X_WOPI_ITEMVERSION_HEADER));

    EcatDocumentFile updatedMediaFile =
        recordMgr.getAsSubclass(csvFile.getId(), EcatDocumentFile.class);
    assertNotNull(updatedMediaFile);
    assertEquals(2, updatedMediaFile.getVersion());

    Optional<FileInputStream> fis = fileStore.retrieve(updatedMediaFile.getFileProperty());
    assertTrue(fis.isPresent());
    String updatedMediaFileContent = IOUtils.toString(fis.get(), "UTF-8");
    assertEquals(updatedContent, updatedMediaFileContent);
  }

  @Test
  public void testPutRelativeErrors() throws IOException, URISyntaxException {

    EcatDocumentFile csvFile = addDocumentFromTestResourcesToGallery("csv.csv", testUser);
    WopiAccessToken requestToken =
        accessTokenHandler.createAccessToken(testUser, csvFile.getGlobalIdentifier());
    req.setParameter(WopiController.ACCESS_TOKEN_PARAM_NAME, requestToken.getAccessToken());

    //  if both RelativeTarget and SuggestedTarget headers are present the host should respond with
    // a 501 Bad Request
    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT_RELATIVE);
    req.addHeader(WopiController.X_WOPI_RELATIVETARGET_HEADER, "test");
    req.addHeader(WopiController.X_WOPI_SUGGESTEDTARGET_HEADER, "test");
    wopiController.postFileOperations(csvFile.getGlobalIdentifier(), testUser, req, resp);
    assertEquals(501, resp.getStatus());

    // if all fine but not a conversion flow we return 501 for now
    String justFineName = StringUtils.repeat("x", 251) + ".csv";
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.setParameter(WopiController.ACCESS_TOKEN_PARAM_NAME, requestToken.getAccessToken());
    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT_RELATIVE);
    req.addHeader(WopiController.X_WOPI_RELATIVETARGET_HEADER, justFineName);
    wopiController.postFileOperations(csvFile.getGlobalIdentifier(), testUser, req, resp);
    assertEquals(501, resp.getStatus());

    // if all fine and in conversion flow 200 response is expected
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_FILECONVERSION_HEADER, true);
    wopiController.postFileOperations(csvFile.getGlobalIdentifier(), testUser, req, resp);
    assertEquals(200, resp.getStatus());
  }

  @Test
  public void testPutRelativeSuggestedModeNameGeneration() throws IOException, URISyntaxException {
    EcatDocumentFile docFile = addDocumentFromTestResourcesToGallery("letterlegal5.doc", testUser);

    String nameFromCorrectSuggestion =
        wopiController.generateNameForPutRelativeSuggestedMode("letter_updated.docx", docFile);
    assertEquals("letter_updated.docx", nameFromCorrectSuggestion);

    String nameFromCorrectExtensionSuggestion =
        wopiController.generateNameForPutRelativeSuggestedMode(".docx", docFile);
    assertEquals("letterlegal5.docx", nameFromCorrectExtensionSuggestion);

    String tooLongName = StringUtils.repeat("x", 252) + ".txt";
    String nameFromTooLongSuggestion =
        wopiController.generateNameForPutRelativeSuggestedMode(tooLongName, docFile);
    String expectedAbbreviatedName = StringUtils.repeat("x", 248) + "....txt";
    assertEquals(expectedAbbreviatedName, nameFromTooLongSuggestion);
  }

  @Test
  public void testPutRelativeForConversionFlow() throws IOException, URISyntaxException {

    EcatDocumentFile docFile = addDocumentFromTestResourcesToGallery("letterlegal5.doc", testUser);
    String fileId = docFile.getGlobalIdentifier();

    Map<String, Object> fileInfo = wopiController.checkFileInfo(fileId, testUser);
    assertEquals(200, resp.getStatus());
    assertEquals("1", fileInfo.get("Version"));

    WopiAccessToken requestToken = accessTokenHandler.createAccessToken(testUser, fileId);
    req.setParameter(WopiController.ACCESS_TOKEN_PARAM_NAME, requestToken.getAccessToken());

    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT_RELATIVE);
    req.addHeader(
        WopiController.X_WOPI_RELATIVETARGET_HEADER,
        "updated+AF8-doc.docx"); // utf-7 encoded string
    req.addHeader(WopiController.X_WOPI_FILECONVERSION_HEADER, true);

    final String updatedContent = "1,2,3,updatedContent";
    req.setContent(updatedContent.getBytes());
    String expectedName = "updated_doc.docx";

    Map<String, Object> response = wopiController.postFileOperations(fileId, testUser, req, resp);

    // check response
    assertEquals(200, resp.getStatus());
    assertEquals(expectedName, response.get("Name")); // saved and returned as utf-8
    String responseUrl = response.get("Url").toString();
    assertTrue(
        responseUrl.contains("/wopi/files/" + fileId + "?access_token="),
        "unexpected url: " + responseUrl);
    String responseHostViewUrl = response.get("HostViewUrl").toString();
    assertTrue(
        responseHostViewUrl.contains("/officeOnline/GL"),
        "unexpected view url: " + responseHostViewUrl);
    assertTrue(
        responseHostViewUrl.endsWith("/view"), "unexpected view url: " + responseHostViewUrl);
    String responseHostEditUrl = response.get("HostEditUrl").toString();
    assertTrue(
        responseHostEditUrl.contains("/officeOnline/GL"),
        "unexpected edit url: " + responseHostEditUrl);
    assertTrue(
        responseHostEditUrl.endsWith("/edit"), "unexpected view url: " + responseHostEditUrl);

    // check access token in response
    String responseUrlAccessToken =
        responseUrl.substring(responseUrl.indexOf("access_token=") + "access_token=".length());
    WopiAccessToken responseToken = accessTokenHandler.getTokens().get(responseUrlAccessToken);
    assertNotNull(responseToken);
    assertEquals(fileId, responseToken.getFileId());
    // ensure expiry date on a new token is the same as on old one, to prevent token trading
    assertEquals(requestToken.getExpiryDate(), responseToken.getExpiryDate());

    // check subsequent file info response
    Map<String, Object> fileInfoAfterConversion = wopiController.checkFileInfo(fileId, testUser);
    assertEquals(200, resp.getStatus());
    assertEquals("2", fileInfoAfterConversion.get("Version"));
    assertEquals(expectedName, fileInfoAfterConversion.get("BaseFileName"));

    // ensure gallery file properties
    GlobalIdentifier globalId = new GlobalIdentifier(fileId);
    EcatMediaFile updatedFile = (EcatMediaFile) recordMgr.get(globalId.getDbId());
    assertNotNull(updatedFile);
    assertEquals(expectedName, updatedFile.getName());
    assertEquals("docx", updatedFile.getExtension());
  }

  @Test
  public void testPutRelativeForSaveAsFlow() throws IOException, URISyntaxException {

    EcatDocumentFile docFile = addDocumentFromTestResourcesToGallery("letterlegal5.doc", testUser);
    String fileId = docFile.getGlobalIdentifier();

    Map<String, Object> fileInfo = wopiController.checkFileInfo(fileId, testUser);
    assertEquals(200, resp.getStatus());
    assertEquals("1", fileInfo.get("Version"));

    WopiAccessToken requestToken = accessTokenHandler.createAccessToken(testUser, fileId);
    req.setParameter(WopiController.ACCESS_TOKEN_PARAM_NAME, requestToken.getAccessToken());

    req.addHeader(
        WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_PUT_RELATIVE);
    req.addHeader(WopiController.X_WOPI_RELATIVETARGET_HEADER, "letterlegal5.pdf");

    final String updatedContent = "1,2,3,updatedContent";
    req.setContent(updatedContent.getBytes());

    Map<String, Object> response = wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(501, resp.getStatus());
    assertEquals(Collections.emptyMap(), response);

    /* 'save as' flow won't be supported for now
    assertEquals(200, resp.getStatus());
    assertEquals("letterlegal5.pdf", response.get("Name"));

    String responseUrl = response.get("Url").toString();
    String responseFileId = responseUrl.substring(responseUrl.indexOf("GL"), responseUrl.indexOf("?access_token"));
    assertTrue(responseFileId.startsWith("GL"), "unexpected file id in response Url: " + responseFileId);

    // check access token in response
    String responseUrlAccessToken = responseUrl.substring(responseUrl.indexOf("access_token=") + "access_token=".length());
    WopiAccessToken responseToken = accessTokenHandler.getTokens().get(responseUrlAccessToken);
    assertNotNull(responseToken);
    assertEquals(responseFileId, responseToken.getFileId());
    // ensure expiry date on a new token is the same as on old one, to prevent token trading
    assertEquals(requestToken.getExpiryDate(), responseToken.getExpiryDate());

    // check docs exists and is in docs gallery
    Record record = recordMgr.get(Long.valueOf(responseFileId.substring(2)));
    assertNotNull(record);
    assertTrue(record.isMediaRecord());
    assertEquals("letterlegal5.pdf", record.getName());
    assertEquals(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME, record.getParent().getName()); */
  }

  @Test
  public void testDeleteFile() throws IOException, URISyntaxException {

    // upload doc
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier();
    String lockId = "dummyLockId";

    /* "If the file is currently locked, the host should return a 409 Conflict and include
     * an X-WOPI-Lock response header containing the value of the current lock on the file." */

    testLockHandler.lock(fileId, lockId);
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_DELETE);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(409, resp.getStatus());
    assertEquals(lockId, resp.getHeader(WopiController.X_WOPI_LOCK_HEADER));

    /* Otherwise respond with a 501 status (Not Implemented) */

    testLockHandler.unlock(fileId, lockId);
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    req.addHeader(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_DELETE);
    wopiController.postFileOperations(fileId, testUser, req, resp);
    assertEquals(501, resp.getStatus());

    EcatDocumentFile updatedMediaFile =
        recordMgr.getAsSubclass(msExcel.getId(), EcatDocumentFile.class);
    assertNotNull(updatedMediaFile);
    assertFalse(updatedMediaFile.isDeleted());
  }
}

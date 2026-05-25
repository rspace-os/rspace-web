package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.api.v1.model.ApiGalleryFilestoreTransferRequest;
import com.researchspace.api.v1.model.EcatAudioFileStub;
import com.researchspace.api.v1.model.NfsClientStub;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.netfiles.WritableNfsClient;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalStorageManager;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.impl.FilestoreWriteManagerImpl;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.io.IOException;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

class GalleryFilestoresApiControllerWriteOpsTest {

  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";

  @Mock private NfsManager nfsManager;
  @Mock private RecordDeletionManager deletionManager;
  @Mock private BaseRecordManager baseRecordManager;
  @Mock private NfsAuthentication nfsAuthentication;
  @Mock private ExternalStorageManager externalStorageManager;
  @Mock private NfsFileHandler nfsFileHandler;
  @Mock private NfsFactory nfsFactory;
  @Mock private IPropertyHolder propertyHolder;
  @Mock private User user;

  private final Long validFilestorePathId = 1L;
  private final NfsClient nfsClientLoggedIn = new NfsClientStub(USERNAME, true);
  private final Set<Long> validRecordIds = Set.of(123L, 456L);

  private GalleryFilestoresApiController controller;
  private GalleryFilestoresCredentialsStore credentialsStore;

  @BeforeEach
  void setup() throws IOException {
    MockitoAnnotations.openMocks(this);

    credentialsStore = new GalleryFilestoresCredentialsStore(nfsAuthentication);

    FilestoreWriteManagerImpl filestoreWriteManager = new FilestoreWriteManagerImpl();
    filestoreWriteManager.setNfsManager(nfsManager);
    filestoreWriteManager.setNfsFactory(nfsFactory);
    filestoreWriteManager.setBaseRecordManager(baseRecordManager);
    filestoreWriteManager.setExternalStorageManager(externalStorageManager);
    filestoreWriteManager.setCredentialsStore(credentialsStore);

    controller = new GalleryFilestoresApiController();
    controller.nfsManager = nfsManager;
    controller.credentialsStore = credentialsStore;
    controller.deletionManager = deletionManager;
    controller.filestoreWriteManager = filestoreWriteManager;
    controller.setNfsFileHandler(nfsFileHandler);
    controller.setNfsFactory(nfsFactory);
    controller.properties = propertyHolder;

    when(propertyHolder.isNetFileStoresEnabled()).thenReturn(true);
    when(nfsManager.getNfsFileStore(validFilestorePathId))
        .thenReturn(
            GalleryFilestoreTestUtils.createIrodsFileSystemAndFileStore(
                validFilestorePathId, "Filestore_1", user));
    when(nfsAuthentication.validateCredentials(any(), any(), any())).thenReturn(null);
    when(nfsAuthentication.login(eq(USERNAME), eq(PASSWORD), any(), any()))
        .thenReturn(nfsClientLoggedIn);

    when(baseRecordManager.get(eq(123L), any())).thenReturn(new EcatAudioFileStub(1L, "file1.wav"));
    when(baseRecordManager.get(eq(456L), any())).thenReturn(new EcatAudioFileStub(2L, "file2.wav"));
    when(baseRecordManager.retrieveMediaFile(any(User.class), eq(123L)))
        .thenReturn(new EcatAudioFileStub(1L, "file1.wav"));
    when(baseRecordManager.retrieveMediaFile(any(User.class), eq(456L)))
        .thenReturn(new EcatAudioFileStub(2L, "file2.wav"));

    when(nfsManager.uploadFilesToNfs(
            anyCollection(), anyString(), any(WritableNfsClient.class), any()))
        .thenReturn(new ApiExternalStorageOperationResult());
    when(deletionManager.deleteMediaFileSet(anySet(), any()))
        .thenReturn(new CompositeRecordOperationResult<>());
  }

  @Test
  void moveToFilestore_validRequest_invokesUploadAndDelete() throws BindException, IOException {
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(
            validRecordIds, new ApiNfsCredentials(null, USERNAME, PASSWORD));

    controller.moveToFilestore(
        validFilestorePathId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(nfsManager)
        .uploadFilesToNfs(anyCollection(), anyString(), any(WritableNfsClient.class), any());
    verify(deletionManager).deleteMediaFileSet(anySet(), any(User.class));
  }

  @Test
  void copyToFilestore_validRequest_invokesUploadButNotDelete() throws BindException, IOException {
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(
            validRecordIds, new ApiNfsCredentials(null, USERNAME, PASSWORD));

    controller.copyToFilestore(
        validFilestorePathId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(nfsManager)
        .uploadFilesToNfs(anyCollection(), anyString(), any(WritableNfsClient.class), any());
    verify(deletionManager, org.mockito.Mockito.never()).deleteMediaFileSet(anySet(), any());
  }

  @Test
  void copyToFilestore_multipleInvalidRecordIds_allErrorsReported() {
    when(baseRecordManager.get(eq(789L), any()))
        .thenThrow(new ObjectRetrievalFailureException("EcatMediaFile", "789"));
    when(baseRecordManager.get(eq(987L), any()))
        .thenThrow(new ObjectRetrievalFailureException("EcatMediaFile", "987"));
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(
            Set.of(789L, 987L), new ApiNfsCredentials(null, USERNAME, PASSWORD));

    BindException ex =
        assertThrows(
            BindException.class,
            () ->
                controller.copyToFilestore(
                    validFilestorePathId,
                    request,
                    new BeanPropertyBindingResult(request, "request"),
                    user));

    assertEquals(2, ex.getAllErrors().size());
  }

  @Test
  void transferBetweenFilestores_sameSourceAndDestFilestoreId_throwsUnsupportedOperation() {
    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest(
            "src/file.txt", validFilestorePathId, "dst/file.txt", false);

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            controller.transferBetweenFilestores(
                validFilestorePathId,
                request,
                new BeanPropertyBindingResult(request, "request"),
                user));
  }

  @Test
  void transferBetweenFilestores_sameAbsoluteKeyAcrossTwoFilestores_throwsUnsupportedOperation() {
    // Two distinct filestores sharing the same root path (same underlying bucket) with the same
    // relative path resolve to an identical S3 key — a self-copy that must be rejected.
    Long srcId = 10L;
    Long dstId = 20L;
    NfsFileStore srcFilestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user);
    srcFilestore.setPath("shared-root");
    NfsFileStore dstFilestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user);
    dstFilestore.setPath("shared-root");
    when(nfsManager.getNfsFileStore(srcId)).thenReturn(srcFilestore);
    when(nfsManager.getNfsFileStore(dstId)).thenReturn(dstFilestore);
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("file.png", dstId, "file.png", false);

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            controller.transferBetweenFilestores(
                srcId, request, new BeanPropertyBindingResult(request, "request"), user));
  }

  @Test
  void transferBetweenFilestores_transferCapableFilestores_returnsSuccessResult()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    ApiExternalStorageOperationResult result =
        controller.transferBetweenFilestores(
            srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    assertEquals(1, result.getFileInfoDetails().size());
    assertTrue(result.getFileInfoDetails().iterator().next().getSucceeded());
  }

  @Test
  void transferBetweenFilestores_deleteSourceTrue_invokesDeleteOnSource()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", true);

    ApiExternalStorageOperationResult result =
        controller.transferBetweenFilestores(
            srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject(any(), any(), any(), any());
    verify(srcClient).deleteFile("src/file.txt");
    assertTrue(result.getFileInfoDetails().iterator().next().getSucceeded());
  }

  @Test
  void transferBetweenFilestores_deleteSourceFalse_doesNotInvokeDelete()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject(any(), any(), any(), any());
    verify(srcClient, never()).deleteFile(anyString());
  }

  @Test
  void transferBetweenFilestores_copyObjectThrowsIOException_returnsFailedResult()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    doThrow(new IOException("boom")).when(srcClient).copyObject(any(), any(), any(), any());
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", true);

    ApiExternalStorageOperationResult result =
        controller.transferBetweenFilestores(
            srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    assertEquals(1, result.getFileInfoDetails().size());
    assertFalse(result.getFileInfoDetails().iterator().next().getSucceeded());
    assertNotNull(result.getFileInfoDetails().iterator().next().getReason());
    verify(srcClient, never()).deleteFile(anyString());
  }

  @Test
  void transferBetweenFilestores_passesTransferAttributionToCopyObject()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn("alice");

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    WriteAttribution expected = new WriteAttribution("alice", null);
    verify(srcClient)
        .copyObject("src/file.txt", destClient, "dst/file.txt", expected.metadataForRecord(null));
  }

  @Test
  void transferBetweenFilestores_backendNotSupportingTransfer_throwsUnsupportedOperation() {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    // writable but supportsServerSideTransfer() == false — must be rejected
    WritableNfsClient nonTransferCapable = mock(WritableNfsClient.class);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(nonTransferCapable);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            controller.transferBetweenFilestores(
                srcId, request, new BeanPropertyBindingResult(request, "request"), user));
  }

  @Test
  void transferBetweenFilestores_nonWritableBackend_throwsUnsupportedOperation() {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    // backend returns a plain NfsClient that is NOT a WritableNfsClient → must produce UOE (501)
    NfsClient nonWritable = mock(NfsClient.class);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(nonWritable);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            controller.transferBetweenFilestores(
                srcId, request, new BeanPropertyBindingResult(request, "request"), user));
  }

  @Test
  void transferBetweenFilestores_sourceFilestoreHasConfiguredRootPath_prependsRootToSourceKey()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    NfsFileStore srcFilestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user);
    srcFilestore.setPath("src-root");
    when(nfsManager.getNfsFileStore(srcId)).thenReturn(srcFilestore);
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    // sourcePath may have a leading '/' — this is what RemoteFile.remotePath returns (relative to
    // filestore root, not bucket root)
    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("/file.png", dstId, "file.png", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject(eq("src-root/file.png"), eq(destClient), any(), any());
  }

  @Test
  void transferBetweenFilestores_destFilestoreHasConfiguredRootPath_prependsRootToDestKey()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    NfsFileStore dstFilestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user);
    dstFilestore.setPath("dst-root");
    when(nfsManager.getNfsFileStore(dstId)).thenReturn(dstFilestore);
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("/file.png", dstId, "file.png", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject(any(), eq(destClient), eq("dst-root/file.png"), any());
  }

  @Test
  void transferBetweenFilestores_destPathWithLeadingSlash_doesNotProduceDoubleSlashInKey()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    NfsFileStore dstFilestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user);
    dstFilestore.setPath("dst-root");
    when(nfsManager.getNfsFileStore(dstId)).thenReturn(dstFilestore);
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("/file.png", dstId, "/file.png", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject(any(), eq(destClient), eq("dst-root/file.png"), any());
  }

  @Test
  void transferBetweenFilestores_sourceHasRootPathAndDeleteTrue_deleteFileUsesAbsoluteKey()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    NfsFileStore srcFilestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user);
    srcFilestore.setPath("src-root");
    when(nfsManager.getNfsFileStore(srcId)).thenReturn(srcFilestore);
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    WritableNfsClient srcClient = mock(WritableNfsClient.class);
    WritableNfsClient destClient = mock(WritableNfsClient.class);
    when(srcClient.supportsServerSideTransfer()).thenReturn(true);
    when(destClient.supportsServerSideTransfer()).thenReturn(true);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("/file.png", dstId, "file.png", true);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).deleteFile("src-root/file.png");
  }

  @Test
  void copyToFilestore_folderRecordId_throwsBindException() {
    Long folderId = 999L;
    BaseRecord folderMock = mock(BaseRecord.class);
    when(folderMock.isFolder()).thenReturn(true);
    when(baseRecordManager.get(eq(folderId), any())).thenReturn(folderMock);
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(
            Set.of(folderId), new ApiNfsCredentials(null, USERNAME, PASSWORD));

    BindException ex =
        assertThrows(
            BindException.class,
            () ->
                controller.copyToFilestore(
                    validFilestorePathId,
                    request,
                    new BeanPropertyBindingResult(request, "request"),
                    user));

    assertEquals(1, ex.getAllErrors().size());
  }

  @Test
  void moveToFilestore_folderRecordId_throwsBindException() {
    Long folderId = 999L;
    BaseRecord folderMock = mock(BaseRecord.class);
    when(folderMock.isFolder()).thenReturn(true);
    when(baseRecordManager.get(eq(folderId), any())).thenReturn(folderMock);
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(
            Set.of(folderId), new ApiNfsCredentials(null, USERNAME, PASSWORD));

    BindException ex =
        assertThrows(
            BindException.class,
            () ->
                controller.moveToFilestore(
                    validFilestorePathId,
                    request,
                    new BeanPropertyBindingResult(request, "request"),
                    user));

    assertEquals(1, ex.getAllErrors().size());
  }

  @Test
  void copyToFilestore_unauthorizedMediaFile_propagatesAuthorizationExceptionAndDoesNotUpload()
      throws IOException {
    // AuthorizationException must propagate (rather than be caught and translated to a
    // BindException), so the surrounding transaction rolls back cleanly.
    Long unauthorizedId = 888L;
    when(baseRecordManager.get(eq(unauthorizedId), any()))
        .thenReturn(new EcatAudioFileStub(unauthorizedId, "secret.wav"));
    when(baseRecordManager.retrieveMediaFile(any(User.class), eq(unauthorizedId)))
        .thenThrow(new AuthorizationException("no read permission"));
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(
            Set.of(unauthorizedId), new ApiNfsCredentials(null, USERNAME, PASSWORD));

    assertThrows(
        AuthorizationException.class,
        () ->
            controller.copyToFilestore(
                validFilestorePathId,
                request,
                new BeanPropertyBindingResult(request, "request"),
                user));

    verify(nfsManager, never())
        .uploadFilesToNfs(anyCollection(), anyString(), any(WritableNfsClient.class), any());
  }
}

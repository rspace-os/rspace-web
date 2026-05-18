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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

    when(baseRecordManager.retrieveMediaFile(any(), eq(123L)))
        .thenReturn(new EcatAudioFileStub(1L, "file1.wav"));
    when(baseRecordManager.retrieveMediaFile(any(), eq(456L)))
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
  void transferBetweenFilestores_distinctS3Filestores_returnsSuccessResult()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(nfsClientLoggedIn);
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
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", true);

    ApiExternalStorageOperationResult result =
        controller.transferBetweenFilestores(
            srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject("src/file.txt", destClient, "dst/file.txt");
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
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn(USERNAME);

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    verify(srcClient).copyObject(any(), any(), any());
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
    doThrow(new IOException("boom")).when(srcClient).copyObject(any(), any(), any());
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
  void transferBetweenFilestores_onS3Source_invokesCopyObjectWithTransferAttribution()
      throws BindException, IOException {
    Long srcId = 10L;
    Long dstId = 20L;
    when(nfsManager.getNfsFileStore(srcId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(srcId, "src", user));
    when(nfsManager.getNfsFileStore(dstId))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(dstId, "dst", user));
    com.researchspace.netfiles.s3.S3NfsClient srcClient =
        org.mockito.Mockito.mock(com.researchspace.netfiles.s3.S3NfsClient.class);
    com.researchspace.netfiles.s3.S3NfsClient destClient =
        org.mockito.Mockito.mock(com.researchspace.netfiles.s3.S3NfsClient.class);
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn(srcClient, destClient);
    when(user.getUsername()).thenReturn("alice");

    ApiGalleryFilestoreTransferRequest request =
        new ApiGalleryFilestoreTransferRequest("src/file.txt", dstId, "dst/file.txt", false);

    controller.transferBetweenFilestores(
        srcId, request, new BeanPropertyBindingResult(request, "request"), user);

    WriteAttribution expected = new WriteAttribution("alice", "transfer");
    verify(srcClient)
        .copyObject("src/file.txt", destClient, "dst/file.txt", expected.metadataForRecord(null));
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
}

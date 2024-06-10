package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.GalleryApi.API_GALLERY_V1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.EcatAudioFileStub;
import com.researchspace.api.v1.model.NfsClientStub;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.ExternalStorageManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RecordDeletionManager;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.util.UriComponentsBuilder;

class GalleryApiControllerTest {

  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String USERNAME_WRONG = "username_wrong";
  private static final String PASSWORD_WRONG = "password_wrong";
  @Mock private NfsManager nfsManager;
  @Mock private RecordDeletionManager deletionManager;
  @Mock private BaseRecordManager baseRecordManager;
  @Mock private NfsAuthentication nfsAuthentication;
  @Mock private ExternalStorageManager externalStorageManager;
  @Mock private User user;

  private GalleryApiController galleryApiController;
  private Long validFilestorePathId_1 = 1L;
  private Long invalidFilestorePathId_2 = 2L;
  private Long validFilestorePathId_3 = 3L;

  private NfsClient nfsClientLoggedIn = new NfsClientStub(USERNAME, true);
  private Set<Long> validRecordIds = Set.of(123L, 456L);

  @BeforeEach
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);
    galleryApiController =
        new GalleryApiController(
            nfsManager,
            deletionManager,
            baseRecordManager,
            nfsAuthentication,
            externalStorageManager);
    galleryApiController.rsBaseLink =
        UriComponentsBuilder.fromHttpUrl("http://url").path(API_GALLERY_V1);
    ;

    when(nfsManager.getNfsFileStore(validFilestorePathId_1))
        .thenReturn(createIrodsFileStore(validFilestorePathId_1, "Filestore_1"));
    when(nfsManager.getNfsFileStore(validFilestorePathId_3))
        .thenReturn(createIrodsFileStore(validFilestorePathId_3, "Filestore_3"));

    when(nfsAuthentication.validateCredentials(any(), any(), any())).thenReturn(null);
    when(nfsAuthentication.validateCredentials(eq(""), eq(PASSWORD), any()))
        .thenReturn("net.filestores.validation.no.username");
    when(nfsAuthentication.validateCredentials(eq(""), eq(""), any()))
        .thenReturn("net.filestores.validation.no.username");
    when(nfsAuthentication.validateCredentials(eq(USERNAME), eq(""), any()))
        .thenReturn("net.filestores.validation.no.password");

    when(nfsAuthentication.login(eq(USERNAME), eq(PASSWORD), any(), any()))
        .thenReturn(nfsClientLoggedIn);

    when(baseRecordManager.retrieveMediaFile(any(), eq(123L)))
        .thenReturn(new EcatAudioFileStub(1L, "file1.wav"));
    when(baseRecordManager.retrieveMediaFile(any(), eq(456L)))
        .thenReturn(new EcatAudioFileStub(2L, "file2.wav"));
    when(baseRecordManager.retrieveMediaFile(any(), eq(789L)))
        .thenThrow(new ObjectRetrievalFailureException("EcatMediaFile", "789"));

    when(nfsManager.uploadFilesToNfs(anySet(), anyString(), any()))
        .thenReturn(new ApiExternalStorageOperationResult());
    when(deletionManager.deleteMediaFileSet(anySet(), any()))
        .thenReturn(new CompositeRecordOperationResult<>());
  }

  @Test
  void testValidationRecordsIdsAndFilestoreIdAreEmpty() {
    Set<Long> recordIds = Set.of();
    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  recordIds,
                  null,
                  new ApiNfsCredentials(),
                  new BeanPropertyBindingResult(recordIds, "bean"),
                  user);
            });
    assertEquals(2, exception.getAllErrors().size());
    assertEquals("recordIds", exception.getAllErrors().get(0).getObjectName());
    assertEquals("recordIds is mandatory", exception.getAllErrors().get(0).getDefaultMessage());
    assertEquals("filestorePathId", exception.getAllErrors().get(1).getObjectName());
    assertEquals(
        "filestorePathId is mandatory", exception.getAllErrors().get(1).getDefaultMessage());
  }

  @Test
  void testValidationFilestoreDoesNotExist() {
    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  validRecordIds,
                  invalidFilestorePathId_2,
                  new ApiNfsCredentials(),
                  new BeanPropertyBindingResult(validRecordIds, "bean"),
                  user);
            });
    assertEquals(1, exception.getAllErrors().size());
    assertEquals("nfsFileStore", exception.getAllErrors().get(0).getObjectName());
    assertEquals(
        "Could not find file store with id: " + invalidFilestorePathId_2,
        exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testValidationUsernameEmpty() {
    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  validRecordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, "", PASSWORD),
                  new BeanPropertyBindingResult(validRecordIds, "bean"),
                  user);
            });
    assertEquals(1, exception.getAllErrors().size());
    assertEquals("credentials", exception.getAllErrors().get(0).getObjectName());
    assertEquals(
        "net.filestores.validation.no.username",
        exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testValidationPasswordEmpty() {
    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  validRecordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, USERNAME, ""),
                  new BeanPropertyBindingResult(validRecordIds, "bean"),
                  user);
            });
    assertEquals(1, exception.getAllErrors().size());
    assertEquals("credentials", exception.getAllErrors().get(0).getObjectName());
    assertEquals(
        "net.filestores.validation.no.password",
        exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testUserIsNotLoggedIn() {
    NfsClient nfsClientLoggedOut = new NfsClientStub(USERNAME, false);
    when(nfsAuthentication.login(any(), any(), any(), any())).thenReturn(nfsClientLoggedOut);

    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  validRecordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, USERNAME, PASSWORD),
                  new BeanPropertyBindingResult(validRecordIds, "bean"),
                  user);
            });
    assertEquals(1, exception.getAllErrors().size());
    assertEquals("nfsClient", exception.getAllErrors().get(0).getObjectName());
    assertEquals("User is not logged in", exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testIdsDoesNotRetrieveOneOfTheFiles() {
    Set<Long> recordIds = Set.of(123L, 456L, 789L);
    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  recordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, USERNAME, PASSWORD),
                  new BeanPropertyBindingResult(recordIds, "bean"),
                  user);
            });

    assertEquals(1, exception.getAllErrors().size());
    assertEquals("recordIds", exception.getAllErrors().get(0).getObjectName());
    assertEquals(
        "Object of class [EcatMediaFile] with identifier [789]: not found",
        exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testNfsManagerRaisesUnexpectedException() throws UnsupportedOperationException, IOException {
    doThrow(new RuntimeException("Operation Not Supported"))
        .when(nfsManager)
        .uploadFilesToNfs(anyCollection(), anyString(), any(NfsClient.class));

    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  validRecordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, USERNAME, PASSWORD),
                  new BeanPropertyBindingResult(validRecordIds, "bean"),
                  user);
            });

    assertEquals(1, exception.getAllErrors().size());
    assertEquals("nfsClient", exception.getAllErrors().get(0).getObjectName());
    assertEquals("Operation Not Supported", exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testCacheCredentialsUntilGoodOneArePassed() {
    NfsClient nfsWrongUsernameClientLoggedOut = new NfsClientStub(USERNAME_WRONG, false);
    NfsClient nfsWrongPasswordClientLoggedOut = new NfsClientStub(USERNAME, false);
    when(nfsAuthentication.login(eq(USERNAME_WRONG), any(), any(), any()))
        .thenReturn(nfsWrongUsernameClientLoggedOut);
    when(nfsAuthentication.login(any(), eq(PASSWORD_WRONG), any(), any()))
        .thenReturn(nfsWrongPasswordClientLoggedOut);

    // username and password wrong
    callAndAssertCopyEndPointWithWrongCredentials(validRecordIds, USERNAME_WRONG, PASSWORD_WRONG);
    assertEquals(1, galleryApiController.credentialsMapCache.values().size());
    ApiNfsCredentials credentials =
        galleryApiController.credentialsMapCache.values().stream().findFirst().get();
    assertEquals(PASSWORD_WRONG, credentials.getPassword());
    assertEquals(USERNAME_WRONG, credentials.getUsername());
    assertEquals(user, credentials.getUser());

    // password only wrong
    callAndAssertCopyEndPointWithWrongCredentials(validRecordIds, USERNAME, PASSWORD_WRONG);
    assertEquals(1, galleryApiController.credentialsMapCache.values().size());
    credentials = galleryApiController.credentialsMapCache.values().stream().findFirst().get();
    assertEquals(PASSWORD_WRONG, credentials.getPassword());
    assertEquals(USERNAME, credentials.getUsername());
    assertEquals(user, credentials.getUser());

    // username only wrong
    callAndAssertCopyEndPointWithWrongCredentials(validRecordIds, USERNAME_WRONG, PASSWORD);
    assertEquals(1, galleryApiController.credentialsMapCache.values().size());
    credentials = galleryApiController.credentialsMapCache.values().stream().findFirst().get();
    assertEquals(PASSWORD, credentials.getPassword());
    assertEquals(USERNAME_WRONG, credentials.getUsername());
    assertEquals(user, credentials.getUser());
  }

  @Test
  void testCacheCredentialsByUserAndFilesystem()
      throws BindException, UnsupportedOperationException {
    galleryApiController.copyToIRODS(
        validRecordIds,
        validFilestorePathId_1,
        new ApiNfsCredentials(null, USERNAME, PASSWORD),
        new BeanPropertyBindingResult(validRecordIds, "bean"),
        user);
    assertEquals(1, galleryApiController.credentialsMapCache.values().size());

    galleryApiController.copyToIRODS(
        validRecordIds,
        validFilestorePathId_3,
        new ApiNfsCredentials(null, USERNAME, PASSWORD),
        new BeanPropertyBindingResult(validRecordIds, "bean"),
        user);
    assertEquals(2, galleryApiController.credentialsMapCache.values().size());

    galleryApiController.copyToIRODS(
        validRecordIds,
        validFilestorePathId_1,
        null,
        new BeanPropertyBindingResult(validRecordIds, "bean"),
        user);
    assertEquals(2, galleryApiController.credentialsMapCache.values().size());
  }

  @Test
  void testCopySuccessfullyToIrods()
      throws BindException, UnsupportedOperationException, IOException {
    galleryApiController.copyToIRODS(
        validRecordIds,
        validFilestorePathId_1,
        new ApiNfsCredentials(null, USERNAME, PASSWORD),
        new BeanPropertyBindingResult(validRecordIds, "bean"),
        user);
    verify(nfsManager).uploadFilesToNfs(anySet(), anyString(), any());
  }

  @Test
  void testMoveToIrodsFailure()
      throws UnsupportedOperationException, IOException, DocumentAlreadyEditedException {
    doThrow(new ObjectRetrievalFailureException("EcatMediaFile", 2L))
        .when(deletionManager)
        .deleteMediaFileSet(anySet(), any(User.class));

    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.moveToIRODS(
                  validRecordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, USERNAME, PASSWORD),
                  new BeanPropertyBindingResult(validRecordIds, "bean"),
                  user);
            });

    verify(nfsManager).uploadFilesToNfs(any(), any(), any());
    assertEquals(1, exception.getAllErrors().size());
    assertEquals("mediaFile", exception.getAllErrors().get(0).getObjectName());
    assertEquals(
        "Object of class [EcatMediaFile] with identifier [2]: not found",
        exception.getAllErrors().get(0).getDefaultMessage());
  }

  @Test
  void testMoveSuccessfullyToIrods()
      throws BindException, UnsupportedOperationException, IOException {
    galleryApiController.moveToIRODS(
        validRecordIds,
        validFilestorePathId_1,
        new ApiNfsCredentials(null, USERNAME, PASSWORD),
        new BeanPropertyBindingResult(validRecordIds, "bean"),
        user);
    verify(nfsManager).uploadFilesToNfs(anyCollection(), any(), any());
    verify(deletionManager).deleteMediaFileSet(anySet(), any());
  }

  private void callAndAssertCopyEndPointWithWrongCredentials(
      Set<Long> recordIds, String username, String password) {
    BindException exception =
        assertThrows(
            BindException.class,
            () -> {
              galleryApiController.copyToIRODS(
                  recordIds,
                  validFilestorePathId_1,
                  new ApiNfsCredentials(null, username, password),
                  new BeanPropertyBindingResult(recordIds, "bean"),
                  user);
            });
    assertEquals(1, exception.getAllErrors().size());
    assertEquals("nfsClient", exception.getAllErrors().get(0).getObjectName());
    assertEquals("User is not logged in", exception.getAllErrors().get(0).getDefaultMessage());
  }

  private NfsFileStore createIrodsFileStore(Long id, String name) {
    NfsFileStore fileStore = new NfsFileStore();
    fileStore.setId(id);
    fileStore.setFileSystem(createIrodsFileSystem(id));
    fileStore.setDeleted(false);
    fileStore.setName(name);
    fileStore.setPath("");
    fileStore.setUser(user);
    return fileStore;
  }

  private NfsFileSystem createIrodsFileSystem(Long id) {
    NfsFileSystem fileSystem = new NfsFileSystem();
    fileSystem.setId(id);
    fileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
    fileSystem.setClientOptions(
        "IRODS_ZONE=tempZone\nIRODS_HOME_DIR=/tempZone/home/alice\nIRODS_PORT=1247");
    fileSystem.setClientType(NfsClientType.IRODS);
    fileSystem.setDisabled(false);
    fileSystem.setName("irods_test_instance");
    fileSystem.setUrl("irods-test.researchspace.com");
    return fileSystem;
  }
}

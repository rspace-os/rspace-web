package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.validation.BindException;

class GalleryFilestoresApiControllerReadAclTest {

  private static final String USERNAME = "username";

  @Mock private NfsManager nfsManager;
  @Mock private RecordDeletionManager deletionManager;
  @Mock private NfsFileHandler nfsFileHandler;
  @Mock private NfsFactory nfsFactory;
  @Mock private IPropertyHolder propertyHolder;
  @Mock private GalleryFilestoresCredentialsStore credentialsStore;
  @Mock private User user;
  @Mock private HttpServletResponse response;

  private GalleryFilestoresApiController controller;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);

    controller = new GalleryFilestoresApiController();
    controller.nfsManager = nfsManager;
    controller.aclChecker = GalleryFilestoreTestUtils.filestoreAclCheckerForTest();
    controller.credentialsStore = credentialsStore;
    controller.deletionManager = deletionManager;
    controller.setNfsFileHandler(nfsFileHandler);
    controller.setNfsFactory(nfsFactory);
    controller.properties = propertyHolder;

    when(propertyHolder.isNetFileStoresEnabled()).thenReturn(true);
    when(user.getUsername()).thenReturn(USERNAME);
  }

  @Test
  void browseFilesystem_userNotOnReadAllowlist_throwsAuthorizationException() {
    Long fsId = 1L;
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(fsId);
    fs.setReadAllowlist("alice");
    fs.setWriteAllowlist(null);
    when(nfsManager.getFileSystem(fsId)).thenReturn(fs);

    assertThrows(AuthorizationException.class, () -> controller.browseFilesystem(fsId, null, user));

    verify(nfsFactory, never()).getNfsClient(any(), any(), any());
  }

  @Test
  void browseFilestore_userNotOnReadAllowlist_throwsAuthorizationException() {
    Long filestoreId = 1L;
    NfsFileStore filestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(filestoreId, "fs", user);
    filestore.getFileSystem().setReadAllowlist("alice");
    filestore.getFileSystem().setWriteAllowlist(null);
    when(nfsManager.getNfsFileStore(filestoreId)).thenReturn(filestore);

    assertThrows(
        AuthorizationException.class, () -> controller.browseFilestore(filestoreId, null, user));

    verify(nfsFactory, never()).getNfsClient(any(), any(), any());
  }

  @Test
  void downloadFromFilestore_userNotOnReadAllowlist_throwsAuthorizationException() {
    Long filestoreId = 1L;
    NfsFileStore filestore =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(filestoreId, "fs", user);
    filestore.getFileSystem().setReadAllowlist("alice");
    filestore.getFileSystem().setWriteAllowlist(null);
    when(nfsManager.getNfsFileStore(filestoreId)).thenReturn(filestore);

    assertThrows(
        AuthorizationException.class,
        () -> controller.downloadFromFilestore(filestoreId, "some/path", null, user, response));

    verify(nfsFactory, never()).getNfsClient(any(), any(), any());
  }

  @Test
  void createFilestore_userNotOnReadAllowlist_throwsAuthorizationException() throws IOException {
    Long fsId = 1L;
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(fsId);
    fs.setReadAllowlist("alice");
    fs.setWriteAllowlist(null);
    when(nfsManager.getFileSystem(fsId)).thenReturn(fs);

    assertThrows(
        AuthorizationException.class,
        () -> controller.createFilestore(fsId, "myStore", "/some/path", user));

    verify(nfsManager, never()).createAndSaveNewFileStore(any(), any(), any(), any());
  }

  @Test
  void loginToFilesystem_userNotOnReadAllowlist_throwsAuthorizationException()
      throws BindException {
    Long fsId = 1L;
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(fsId);
    fs.setReadAllowlist("alice");
    fs.setWriteAllowlist(null);
    when(nfsManager.getFileSystem(fsId)).thenReturn(fs);

    assertThrows(
        AuthorizationException.class,
        () -> controller.loginToFilesystem(fsId, new ApiNfsCredentials(), null, user));

    verify(credentialsStore, never()).validateCredentialsAndLoginNfs(any(), any(), any(), any());
  }

  @Test
  void browseFilesystem_userOnWriteAllowlistOnly_isAllowed() throws IOException {
    // write implies read: user on write list but not read list can still browse
    Long fsId = 1L;
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(fsId);
    fs.setReadAllowlist(null);
    fs.setWriteAllowlist(USERNAME);
    when(nfsManager.getFileSystem(fsId)).thenReturn(fs);

    try {
      controller.browseFilesystem(fsId, null, user);
    } catch (AuthorizationException e) {
      throw new AssertionError("write should imply read; got 403 from ACL check", e);
    } catch (Exception ignored) {
      // any non-ACL failure downstream is irrelevant to this test
    }
  }
}

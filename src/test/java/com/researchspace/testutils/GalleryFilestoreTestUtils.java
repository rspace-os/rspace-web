package com.researchspace.testutils;

import static org.mockito.Mockito.mock;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.MessageSourceUtils;

public class GalleryFilestoreTestUtils {

  /**
   * Builds a real {@link FilestoreAclChecker} wired with a mock {@link MessageSourceUtils} so unit
   * tests don't NPE inside the exception-message resolution path.
   */
  public static FilestoreAclChecker filestoreAclCheckerForTest() {
    FilestoreAclChecker checker = new FilestoreAclChecker();
    checker.setMessages(mock(MessageSourceUtils.class));
    return checker;
  }

  public static NfsFileSystem createIrodsFileSystem(Long id) {
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

  public static NfsFileSystem createIrodsFileSystem() {
    return createIrodsFileSystem(null);
  }

  public static NfsFileStore createFileStore(String name, User user, NfsFileSystem filesystem) {
    NfsFileStore fileStore = new NfsFileStore();
    fileStore.setFileSystem(filesystem);
    fileStore.setDeleted(false);
    fileStore.setName(name);
    fileStore.setPath("");
    fileStore.setUser(user);
    return fileStore;
  }

  public static NfsFileStore createIrodsFileSystemAndFileStore(Long id, String name, User user) {
    NfsFileStore filestore = createFileStore(name, user, createIrodsFileSystem(id));
    filestore.setId(id);
    return filestore;
  }

  public static NfsFileSystem createS3FileSystem(Long id) {
    NfsFileSystem fileSystem = new NfsFileSystem();
    fileSystem.setId(id);
    fileSystem.setAuthType(NfsAuthenticationType.NONE);
    fileSystem.setClientType(NfsClientType.S3);
    fileSystem.setDisabled(false);
    fileSystem.setName("s3_test_instance");
    fileSystem.setUrl("https://s3.example.com");
    // default permissive ACL so existing tests don't need to opt-in;
    // ACL-specific tests override these values.
    fileSystem.setReadWhitelist("*");
    fileSystem.setWriteWhitelist("*");
    return fileSystem;
  }

  public static NfsFileStore createS3FileSystemAndFileStore(Long id, String name, User user) {
    NfsFileStore filestore = createFileStore(name, user, createS3FileSystem(id));
    filestore.setId(id);
    return filestore;
  }
}

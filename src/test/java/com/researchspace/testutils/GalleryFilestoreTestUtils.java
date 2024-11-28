package com.researchspace.testutils;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;

public class GalleryFilestoreTestUtils {

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

  public static NfsFileStore createIrodsFileStore(
      String name, User user, NfsFileSystem filesystem) {
    NfsFileStore fileStore = new NfsFileStore();
    fileStore.setFileSystem(filesystem);
    fileStore.setDeleted(false);
    fileStore.setName(name);
    fileStore.setPath("");
    fileStore.setUser(user);
    return fileStore;
  }

  public static NfsFileStore createIrodsFileSystemAndFileStore(Long id, String name, User user) {
    NfsFileStore filestore = createIrodsFileStore(name, user, createIrodsFileSystem(id));
    filestore.setId(id);
    return filestore;
  }
}

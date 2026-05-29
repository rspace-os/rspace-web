package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NfsExportManagerImplTest {

  private static final long FS_ID = 11L;
  private static final long FILESTORE_ID = 21L;

  private NfsExportManagerImpl manager;
  private NfsClient nfsClient;
  private NfsFileSystem fileSystem;
  private NfsFileStore fileStore;
  private User exporter;
  private ArchiveExportConfig exportConfig;
  private NfsExportPlan plan;
  private Map<Long, NfsClient> nfsClients;

  @BeforeEach
  void setUp() {
    manager = new NfsExportManagerImpl();
    FilestoreAclChecker aclChecker = GalleryFilestoreTestUtils.filestoreAclCheckerForTest();
    manager.setAclChecker(aclChecker);
    ReflectionTestUtils.setField(manager, "diskSpaceChecker", mock(DiskSpaceChecker.class));

    nfsClient = mock(NfsClient.class);
    when(nfsClient.isUserLoggedIn()).thenReturn(true);

    fileSystem = new NfsFileSystem();
    fileSystem.setId(FS_ID);
    fileSystem.setName("test fs");
    fileSystem.setAuthType(NfsAuthenticationType.NONE);
    fileSystem.setClientType(NfsClientType.S3);

    fileStore = new NfsFileStore();
    fileStore.setId(FILESTORE_ID);
    fileStore.setPath("");
    fileStore.setFileSystem(fileSystem);

    exporter = new User("testUser");
    exportConfig = new ArchiveExportConfig();
    exportConfig.setExporter(exporter);

    plan = new NfsExportPlan();
    plan.addFoundFileStore(fileStore);
    plan.addFoundFileSystem(fileSystem.toFileSystemInfo());
    NfsElement link = new NfsElement(FILESTORE_ID, "/test.txt");
    plan.addFoundNfsLink(FS_ID, "/test.txt", link);

    nfsClients = Collections.singletonMap(FS_ID, nfsClient);
  }

  @Test
  void userNotOnReadWhitelist_marksResourceNotAccessibleAndSkipsQuery() {
    fileSystem.setReadWhitelist("alice");
    fileSystem.setWriteWhitelist(null);

    manager.scanFileSystemsForFoundNfsLinks(plan, nfsClients, exportConfig);

    assertEquals(
        NfsExportManagerImpl.RESOURCE_NOT_ACCESSIBLE_MSG,
        plan.getCheckedNfsLinkMessages().get(FS_ID + "_/test.txt"));
    verify(nfsClient, never()).queryForNfsFile(any(NfsTarget.class));
  }

  @Test
  void userOnReadWhitelist_queriesForNfsFile() {
    fileSystem.setReadWhitelist("testUser");
    fileSystem.setWriteWhitelist(null);

    NfsFileDetails details = new NfsFileDetails("test.txt");
    details.setFileSystemFullPath("/test.txt");
    when(nfsClient.queryForNfsFile(any(NfsTarget.class))).thenReturn(details);

    manager.scanFileSystemsForFoundNfsLinks(plan, nfsClients, exportConfig);

    verify(nfsClient).queryForNfsFile(any(NfsTarget.class));
  }
}

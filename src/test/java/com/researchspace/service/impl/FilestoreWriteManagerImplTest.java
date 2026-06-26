package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.netfiles.DeletableTarget;
import com.researchspace.netfiles.FilestoreAuditMetadata;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.netfiles.WritableNfsClient;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FilestoreOperationForbiddenException;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

class FilestoreWriteManagerImplTest {

  private static final Long FS_ID = 1L;
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZoneOffset.UTC);

  @Mock private NfsManager nfsManager;
  @Mock private NfsFactory nfsFactory;
  @Mock private IPropertyHolder properties;
  @Mock private User user;

  private final WritableNfsClient client = mock(WritableNfsClient.class);
  private FilestoreWriteManagerImpl manager;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    manager = new FilestoreWriteManagerImpl();
    manager.setNfsManager(nfsManager);
    manager.setNfsFactory(nfsFactory);
    manager.setAclChecker(GalleryFilestoreTestUtils.filestoreAclCheckerForTest());
    manager.setProperties(properties);
    manager.setMessages(mock(MessageSourceUtils.class));
    manager.setClock(FIXED_CLOCK);

    when(properties.getS3DeleteWindowMinutes()).thenReturn(60);
    when(user.getUsername()).thenReturn("alice");
    when(nfsManager.getNfsFileStore(FS_ID))
        .thenReturn(GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(FS_ID, "fs", user));
    when(nfsFactory.getNfsClient(any(), any(), any())).thenReturn((NfsClient) client);
  }

  private BindingResult errors() {
    return new BeanPropertyBindingResult(new Object(), "request");
  }

  private static FilestoreAuditMetadata meta(String createdBy, String createdAt) {
    return new FilestoreAuditMetadata(createdBy, Instant.parse(createdAt));
  }

  @Test
  void deleteFromFilestore_passesGate_deletesTarget() throws Exception {
    when(client.resolveDeletableTarget("dir/file.txt"))
        .thenReturn(new DeletableTarget("dir/file.txt", meta("alice", "2026-06-18T11:30:00Z")));

    manager.deleteFromFilestore(FS_ID, "dir/file.txt", errors(), user);

    verify(client).deleteByKey("dir/file.txt");
  }

  @Test
  void deleteFromFilestore_gateDenies_throwsAndDeletesNothing() throws Exception {
    // created 90 minutes before the fixed clock; window is 60 minutes. Exercises the manager's
    // clock + window-property -> cutoff wiring and that a denial becomes a 403 with no delete.
    // (The per-condition predicate logic is covered by FilestoreAuditMetadataTest.)
    when(client.resolveDeletableTarget("dir/file.txt"))
        .thenReturn(new DeletableTarget("dir/file.txt", meta("alice", "2026-06-18T10:30:00Z")));

    assertThrows(
        FilestoreOperationForbiddenException.class,
        () -> manager.deleteFromFilestore(FS_ID, "dir/file.txt", errors(), user));
    verify(client, never()).deleteByKey(any());
  }

  @Test
  void deleteFromFilestore_noCreationRecord_deniedWithoutDeleting() throws Exception {
    // No created-by/at (written outside RSpace): denied as no-metadata, not as non-creator.
    when(client.resolveDeletableTarget("dir/file.txt"))
        .thenReturn(new DeletableTarget("dir/file.txt", FilestoreAuditMetadata.from(null)));

    assertThrows(
        FilestoreOperationForbiddenException.class,
        () -> manager.deleteFromFilestore(FS_ID, "dir/file.txt", errors(), user));
    verify(client, never()).deleteByKey(any());
  }

  @Test
  void deleteFromFilestore_rootPath_rejectedWithoutTouchingClient() throws Exception {
    assertThrows(
        BindException.class, () -> manager.deleteFromFilestore(FS_ID, "/", errors(), user));
    verify(client, never()).resolveDeletableTarget(any());
    verify(client, never()).deleteByKey(any());
  }

  @Test
  void moveWithinFilestore_rootSource_rejectedWithoutTouchingClient() throws Exception {
    assertThrows(
        BindException.class, () -> manager.moveWithinFilestore(FS_ID, "/", "dest", errors(), user));
    verify(client, never()).moveWithin(any(), any());
  }

  @Test
  void createFolderInFilestore_delegatesWithCreatedByAndCreatedAt() throws Exception {
    String result = manager.createFolderInFilestore(FS_ID, "parent", "newfolder", errors(), user);

    assertEquals("parent/newfolder", result);
    verify(client)
        .createFolder(
            "parent/newfolder",
            Map.of("rspace-created-by", "alice", "rspace-created-at", "2026-06-18T12:00:00Z"));
  }

  @Test
  void createFolderInFilestore_atRootOfFilestoreWithConfiguredRootPrefix_noDoubleSlash()
      throws Exception {
    // Regression: when the filestore has a non-empty configured root and a folder is created at the
    // filestore root (empty parentPath), the key must be "root/newfolder", not "root//newfolder".
    // A double slash lists as an empty-named folder in S3.
    var filestore = GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(FS_ID, "fs", user);
    filestore.setPath("bucket-prefix");
    when(nfsManager.getNfsFileStore(FS_ID)).thenReturn(filestore);

    String result = manager.createFolderInFilestore(FS_ID, "", "newfolder", errors(), user);

    verify(client)
        .createFolder(
            "bucket-prefix/newfolder",
            Map.of("rspace-created-by", "alice", "rspace-created-at", "2026-06-18T12:00:00Z"));
    assertEquals("newfolder", result);
  }

  @Test
  void createFolderInFilestore_invalidName_rejectedWithoutCreating() throws Exception {
    // path separator or leading/trailing whitespace is rejected before any S3 call
    assertThrows(
        BindException.class,
        () -> manager.createFolderInFilestore(FS_ID, "parent", "a/b", errors(), user));
    assertThrows(
        BindException.class,
        () -> manager.createFolderInFilestore(FS_ID, "parent", " spaced ", errors(), user));
    verify(client, never()).createFolder(any(), any());
  }

  @Test
  void moveWithinFilestore_delegatesAndReturnsDestKey() throws Exception {
    when(client.moveWithin("src/a.txt", "destFolder")).thenReturn("destFolder/a.txt");

    String result = manager.moveWithinFilestore(FS_ID, "src/a.txt", "destFolder", errors(), user);

    assertEquals("destFolder/a.txt", result);
    verify(client).moveWithin("src/a.txt", "destFolder");
  }

  @Test
  void getAuditMetadata_delegatesToClientWithResolvedPath() throws Exception {
    FilestoreAuditMetadata expected =
        new FilestoreAuditMetadata("alice", Instant.parse("2026-06-18T12:00:00Z"));
    when(client.getAuditMetadata("dir/a.txt")).thenReturn(expected);

    FilestoreAuditMetadata result = manager.getAuditMetadata(FS_ID, "dir/a.txt", user);

    assertEquals(expected, result);
    verify(client).getAuditMetadata("dir/a.txt");
  }
}

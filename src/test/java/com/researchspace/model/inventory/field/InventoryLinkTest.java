package com.researchspace.model.inventory.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.util.Date;
import org.junit.jupiter.api.Test;

class InventoryLinkTest {

  @Test
  void settersAndGettersRoundTrip() {
    InventoryLink link = new InventoryLink();
    link.setId(7L);
    link.setTargetGlobalId("SA123v3");
    link.setTargetPrefix(GlobalIdPrefix.SA);
    link.setTargetDbId(123L);
    link.setVersionPin(3L);
    link.setRelationType("IsCalibratedBy");
    link.setDeleted(true);

    assertEquals(7L, link.getId());
    assertEquals("SA123v3", link.getTargetGlobalId());
    assertEquals(GlobalIdPrefix.SA, link.getTargetPrefix());
    assertEquals(123L, link.getTargetDbId());
    assertEquals(3L, link.getVersionPin());
    assertEquals("IsCalibratedBy", link.getRelationType());
    assertTrue(link.isDeleted());
  }

  @Test
  void versionPinMayBeNullForLatest() {
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId("SA123");
    assertNull(link.getVersionPin());
  }

  @Test
  void shallowCopyCopiesPayloadButNotIdentityOrTimestamps() {
    InventoryLink original = new InventoryLink();
    original.setId(7L);
    original.setTargetGlobalId("SA123v3");
    original.setTargetPrefix(GlobalIdPrefix.SA);
    original.setTargetDbId(123L);
    original.setVersionPin(3L);
    original.setTargetRevisionId(55L);
    original.setRelationType("IsCalibratedBy");
    original.prePersist();

    InventoryLink copy = original.shallowCopy();

    assertNotSame(original, copy);
    assertNull(copy.getId());
    assertNull(copy.getCreatedAt());
    assertNull(copy.getModifiedAt());
    assertEquals("SA123v3", copy.getTargetGlobalId());
    assertEquals(GlobalIdPrefix.SA, copy.getTargetPrefix());
    assertEquals(123L, copy.getTargetDbId());
    assertEquals(3L, copy.getVersionPin());
    assertEquals(55L, copy.getTargetRevisionId());
    assertEquals("IsCalibratedBy", copy.getRelationType());
  }

  @Test
  void shallowCopyPreservesDeletedFlag() {
    InventoryLink original = new InventoryLink();
    original.setTargetGlobalId("SA123");
    original.setTargetPrefix(GlobalIdPrefix.SA);
    original.setTargetDbId(123L);
    original.setRelationType("References");
    original.setDeleted(true);

    assertTrue(original.shallowCopy().isDeleted());
  }

  @Test
  void prePersistSetsCreatedAtAndModifiedAt() {
    InventoryLink link = new InventoryLink();
    assertNull(link.getCreatedAt());
    assertNull(link.getModifiedAt());

    link.prePersist();

    assertNotNull(link.getCreatedAt());
    assertNotNull(link.getModifiedAt());
    assertEquals(link.getCreatedAt(), link.getModifiedAt());
  }

  @Test
  void prePersistPreservesExplicitlySetCreatedAt() {
    InventoryLink link = new InventoryLink();
    Date originalCreatedAt = new Date(1_700_000_000_000L);
    link.setCreatedAt(originalCreatedAt);

    link.prePersist();

    assertEquals(originalCreatedAt, link.getCreatedAt());
    assertNotNull(link.getModifiedAt());
  }

  @Test
  void preUpdateAdvancesModifiedAtWithoutTouchingCreatedAt() throws InterruptedException {
    InventoryLink link = new InventoryLink();
    link.prePersist();
    Date createdAt = link.getCreatedAt();
    Date firstModifiedAt = link.getModifiedAt();

    Thread.sleep(5);
    link.preUpdate();

    assertEquals(createdAt, link.getCreatedAt());
    assertThat(link.getModifiedAt()).isAfterOrEqualTo(firstModifiedAt);
  }

  private static NfsFileStore s3Store(long id, String basePath) {
    NfsFileStore store =
        GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(id, "My S3", new User("owner"));
    store.setPath(basePath);
    return store;
  }

  @Test
  void filestoreTargetStoresTheCanonicalPathAndDerivesFilesystemAndAbsolutePath() {
    InventoryLink link = new InventoryLink();

    link.setFilestoreTarget(s3Store(12L, "/data/"), "XRD/run.dat/");

    assertTrue(link.isFilestoreTarget());
    assertEquals(12L, link.getFilestoreId());
    assertEquals("/XRD/run.dat", link.getTargetRemotePath());
    assertEquals(12L, link.getTargetFileSystemId());
    assertEquals("data/XRD/run.dat", link.getTargetAbsolutePath());
  }

  @Test
  void innerDoubledSlashesNameADifferentKeyAndAreKept() {
    // ADR 0015: data/XRD//run.dat and data/XRD/run.dat are different S3 keys
    InventoryLink link = new InventoryLink();

    link.setFilestoreTarget(s3Store(12L, "/data"), "/XRD//run.dat");

    assertEquals("/XRD//run.dat", link.getTargetRemotePath());
    assertEquals("data/XRD//run.dat", link.getTargetAbsolutePath());
  }

  @Test
  void aMissingOrBlankRemotePathIsTheFilestoreRoot() {
    // the root names no item, so these are refused before any lookup
    for (String remotePath : new String[] {null, "", " ", "//"}) {
      assertEquals("/", InventoryLink.canonicalRemotePath(remotePath));
    }
  }

  @Test
  void filestoreTargetClearsTheGlobalIdTargetItsPinAndRevision() {
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId("SA123");
    link.setTargetPrefix(GlobalIdPrefix.SA);
    link.setTargetDbId(123L);
    link.setVersionPin(3L);
    link.setTargetRevisionId(55L);

    link.setFilestoreTarget(s3Store(12L, ""), "/XRD");

    assertNull(link.getTargetGlobalId());
    assertNull(link.getTargetPrefix());
    assertNull(link.getTargetDbId());
    assertNull(link.getVersionPin());
    assertNull(link.getTargetRevisionId());
  }

  @Test
  void globalIdTargetClearsTheFilestoreTarget() {
    InventoryLink link = new InventoryLink();
    link.setFilestoreTarget(s3Store(12L, ""), "/XRD");

    link.setGlobalIdTarget(new GlobalIdentifier("SD42"));

    assertFalse(link.isFilestoreTarget());
    assertEquals("SD42", link.getTargetGlobalId());
    assertEquals(GlobalIdPrefix.SD, link.getTargetPrefix());
    assertEquals(42L, link.getTargetDbId());
    assertNull(link.getTargetRemotePath());
    assertNull(link.getTargetFileSystemId());
    assertNull(link.getTargetAbsolutePath());
  }

  @Test
  void shallowCopyCarriesTheFilestoreTarget() {
    InventoryLink original = new InventoryLink();
    original.setRelationType("IsSourceOf");
    original.setFilestoreTarget(s3Store(12L, "/data"), "/XRD/run.dat");

    InventoryLink copy = original.shallowCopy();

    assertEquals(12L, copy.getFilestoreId());
    assertEquals("/XRD/run.dat", copy.getTargetRemotePath());
    assertEquals(12L, copy.getTargetFileSystemId());
    assertEquals("data/XRD/run.dat", copy.getTargetAbsolutePath());
    assertNull(copy.getTargetGlobalId());
  }
}

package com.researchspace.service.inventory.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkFilestoreTarget;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.inventory.FilestoreLinkingItem;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryLinkManagerImplTest extends SpringTransactionalTest {

  @Autowired private InventoryLinkManager linkManager;
  @Autowired private InventoryLinkDao linkDao;

  @Test
  public void createLinkPersistsRowWithParsedPrefixAndDbId() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("IsCalibratedBy");
    api.setTargetGlobalId(target.getGlobalId());

    InventoryLink saved = linkManager.createLink(api, user);

    assertNotNull(saved.getId());
    assertEquals(GlobalIdPrefix.SA, saved.getTargetPrefix());
    assertEquals(target.getId(), saved.getTargetDbId());
    assertEquals("IsCalibratedBy", saved.getRelationType());
    assertNull(saved.getVersionPin());
  }

  @Test
  public void createLinkToElnDocumentResolvesAndPersists() {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "linkable doc");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(doc.getOid().getIdString());

    InventoryLink saved = linkManager.createLink(api, user);

    assertNotNull(saved.getId());
    assertEquals(GlobalIdPrefix.SD, saved.getTargetPrefix());
    assertEquals(doc.getId(), saved.getTargetDbId());
  }

  /**
   * RSDEV-1528: the pre-check the PIDINST import runs must answer exactly as createLink would, and
   * answer false for every failure alike, so a caller learns nothing about why.
   */
  @Test
  public void canCreateLinkIsTrueOnlyWhereCreateLinkWouldSucceed() {
    User owner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(owner);
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("IsCalibratedBy");
    api.setTargetGlobalId(target.getGlobalId());

    // unpinned on purpose: Envers writes audit rows only on commit, which this rolled-back test
    // never does; the pinned cases live in InventoryLinkManagerImplIT
    assertTrue(linkManager.canCreateLink(api, owner), "a readable target");

    User stranger = createInitAndLoginAnyUser();
    assertFalse(linkManager.canCreateLink(api, stranger), "a target the actor cannot read");
    api.setTargetGlobalId("SA999999999");
    assertFalse(linkManager.canCreateLink(api, owner), "a target that does not exist");
    api.setTargetGlobalId("FL" + target.getId());
    assertFalse(linkManager.canCreateLink(api, owner), "a kind links cannot target");
    api.setTargetGlobalId("not-a-global-id");
    assertFalse(linkManager.canCreateLink(api, owner), "a malformed id");
    api.setTargetGlobalId(target.getGlobalId());
    api.setRelationType("IsFriendsWith");
    assertFalse(linkManager.canCreateLink(api, owner), "an unknown relation type");
  }

  @Test
  public void updateLinkChangesRelationAndPreservesCreatedAt() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target1 = createBasicSampleForUser(user, "target1");
    ApiSampleWithFullSubSamples target2 = createBasicSampleForUser(user, "target2");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(target1.getGlobalId());
    InventoryLink saved = linkManager.createLink(api, user);
    Date originalCreated = saved.getCreatedAt();

    Thread.sleep(5);
    ApiInventoryLink update = new ApiInventoryLink();
    update.setRelationType("IsCalibratedBy");
    update.setTargetGlobalId(target2.getGlobalId());
    InventoryLink updated = linkManager.updateLink(saved, update, user);

    assertEquals("IsCalibratedBy", updated.getRelationType());
    assertEquals(target2.getGlobalId(), updated.getTargetGlobalId());
    assertEquals(target2.getId(), updated.getTargetDbId());
    assertEquals(originalCreated, updated.getCreatedAt());
  }

  @Test
  public void deleteLinkSoftDeletes() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(target.getGlobalId());
    InventoryLink saved = linkManager.createLink(api, user);

    linkManager.deleteLink(saved, user);

    InventoryLink reloaded = linkDao.get(saved.getId());
    assertTrue(reloaded.isDeleted());
  }

  @Test
  public void findReferencingItemsReturnsEmptyWhenNoSources() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    List<ApiInventoryReferencingItem> rows =
        linkManager.findReferencingItems(target.getGlobalId(), user);

    assertThat(rows).isEmpty();
  }

  @Test
  public void findReferencingItemsRejectsTargetTheCallerCannotRead() {
    User owner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(owner);
    User stranger = createInitAndLoginAnyUser();
    String targetGlobalId = target.getGlobalId();

    // same error as a missing record, so the response does not confirm the target exists
    assertThrows(
        ApiRuntimeException.class,
        () -> linkManager.findReferencingItems(targetGlobalId, stranger));
  }

  @Test
  public void findReferencingItemsRejectsMissingTarget() {
    User user = createInitAndLoginAnyUser();
    assertThrows(ApiRuntimeException.class, () -> linkManager.findReferencingItems("SA9999", user));
  }

  private NfsFileSystem savedFileSystem() {
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsMgr.saveNfsFileSystem(fs);
    return fs;
  }

  private ApiSampleWithFullSubSamples sampleLinking(
      User owner, NfsFileStore store, String remotePath, String relation) {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType(relation);
    link.setFilestoreTarget(new ApiInventoryLinkFilestoreTarget(store.getId(), remotePath));
    ApiExtraField field = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    field.setName("Raw data");
    field.setNewFieldRequest(true);
    field.setLink(link);
    ApiSample update = new ApiSample();
    update.setId(sample.getId());
    update.setExtraFields(List.of(field));
    sampleApiMgr.updateApiSample(update, owner);
    return sample;
  }

  @Test
  public void findsTheItemsLinkingAFilestorePathAndHidesUnreadableOnesBehindTheirOwner() {
    User owner = createInitAndLoginAnyUser();
    NfsFileSystem fs = savedFileSystem();
    // linked through a filestore rooted at /data, looked up by the absolute path
    NfsFileStore store = nfsMgr.createAndSaveNewFileStore(fs.getId(), "data", "/data", owner);
    ApiSampleWithFullSubSamples sample = sampleLinking(owner, store, "/XRD/run.dat", "IsSourceOf");
    flushDatabaseState();

    List<FilestoreLinkingItem> own =
        linkManager.findItemsLinkingFilestorePaths(
            fs, List.of("data/XRD", "data/XRD/run.dat"), owner);

    assertEquals(1, own.size());
    assertEquals(sample.getGlobalId(), own.get(0).globalId());
    assertEquals("SAMPLE", own.get(0).type());
    assertEquals("IsSourceOf", own.get(0).relationType());

    User stranger = createInitAndLoginAnyUser();
    List<FilestoreLinkingItem> hidden =
        linkManager.findItemsLinkingFilestorePaths(fs, List.of("data/XRD/run.dat"), stranger);

    assertEquals(1, hidden.size());
    assertFalse(hidden.get(0).isReadable());
    assertNull(hidden.get(0).name());
    assertEquals(owner.getFullName(), hidden.get(0).ownerFullName());
  }

  @Test
  public void ignoresOtherPathsAndTheSamePathOnAnotherFilesystem() {
    User owner = createInitAndLoginAnyUser();
    NfsFileSystem fs = savedFileSystem();
    NfsFileSystem otherFs = savedFileSystem();
    NfsFileStore store = nfsMgr.createAndSaveNewFileStore(fs.getId(), "data", "/data", owner);
    sampleLinking(owner, store, "/XRD/run.dat", "IsSourceOf");
    flushDatabaseState();

    assertThat(linkManager.findItemsLinkingFilestorePaths(fs, List.of("data/XRD/other.dat"), owner))
        .isEmpty();
    assertThat(
            linkManager.findItemsLinkingFilestorePaths(otherFs, List.of("data/XRD/run.dat"), owner))
        .isEmpty();
    assertThat(linkManager.findItemsLinkingFilestorePaths(fs, List.of(), owner)).isEmpty();
  }

  @Test
  public void aDeletedLinkNoLongerReportsItsItem() {
    User owner = createInitAndLoginAnyUser();
    NfsFileSystem fs = savedFileSystem();
    NfsFileStore store = nfsMgr.createAndSaveNewFileStore(fs.getId(), "data", "/data", owner);
    sampleLinking(owner, store, "/XRD/run.dat", "IsSourceOf");
    InventoryLink link =
        linkDao.getAll().stream()
            .filter(saved -> store.getId().equals(saved.getFilestoreId()))
            .findFirst()
            .orElseThrow();
    linkManager.deleteLink(link, owner);
    flushDatabaseState();

    assertThat(linkManager.findItemsLinkingFilestorePaths(fs, List.of("data/XRD/run.dat"), owner))
        .isEmpty();
  }

  @Test
  public void aPathDifferingOnlyByATrailingSpaceIsAnotherItem() {
    User owner = createInitAndLoginAnyUser();
    NfsFileSystem fs = savedFileSystem();
    NfsFileStore store = nfsMgr.createAndSaveNewFileStore(fs.getId(), "data", "/data", owner);
    sampleLinking(owner, store, "/XRD/run.dat", "IsSourceOf");
    flushDatabaseState();

    // S3 keys keep trailing spaces, which the path column's collation ignores
    assertThat(linkManager.findItemsLinkingFilestorePaths(fs, List.of("data/XRD/run.dat "), owner))
        .isEmpty();
  }
}

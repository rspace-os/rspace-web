package com.researchspace.dao.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.dao.NfsDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryLinkDaoHibernateTest extends SpringTransactionalTest {

  @Autowired private InventoryLinkDao dao;
  @Autowired private NfsDao nfsDao;

  @Test
  public void saveAndGenerateId() {
    InventoryLink link = newLink("SA123", GlobalIdPrefix.SA, 123L, "References");
    InventoryLink saved = dao.save(link);

    assertNotNull(saved.getId());
    assertNotNull(saved.getCreatedAt());
    assertNotNull(saved.getModifiedAt());
  }

  private NfsFileStore savedStore(String username) {
    User owner = createAndSaveUserIfNotExists(username);
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsDao.saveNfsFileSystem(fs);
    NfsFileStore store = GalleryFilestoreTestUtils.createFileStore("s3", owner, fs);
    store.setPath("/data");
    nfsDao.saveNfsFileStore(store);
    return store;
  }

  @Test
  public void savesAndReloadsAFilestoreTarget() {
    NfsFileStore store = savedStore("invlinkfs1");
    InventoryLink link = new InventoryLink();
    link.setRelationType("IsSourceOf");
    link.setFilestoreTarget(store, "/XRD/run.dat");

    Long id = dao.save(link).getId();
    flushDatabaseState();
    sessionFactory.getCurrentSession().clear();

    InventoryLink reloaded = dao.get(id);
    assertEquals(store.getId(), reloaded.getFilestoreId());
    assertEquals("/XRD/run.dat", reloaded.getTargetRemotePath());
    assertEquals(store.getFileSystem().getId(), reloaded.getTargetFileSystemId());
    assertEquals("data/XRD/run.dat", reloaded.getTargetAbsolutePath());
    assertNull(reloaded.getTargetGlobalId());
  }

  @Test
  public void theDatabaseRefusesARowNamingBothTargetKinds() {
    NfsFileStore store = savedStore("invlinkfs2");
    InventoryLink link = newLink("SA123", GlobalIdPrefix.SA, 123L, "References");
    link.setFilestoreId(store.getId());
    link.setTargetRemotePath("/XRD/run.dat");
    link.setTargetFileSystemId(store.getFileSystem().getId());
    link.setTargetAbsolutePath("data/XRD/run.dat");

    RuntimeException refused =
        assertThrows(
            RuntimeException.class,
            () -> {
              dao.save(link);
              flushDatabaseState();
            });

    assertThat(ExceptionUtils.getRootCauseMessage(refused)).contains("CK_InvLink_one_target_kind");
  }

  @Test
  public void filestoreTargetPathsCompareCaseSensitively() {
    // S3 keys are case-sensitive, as for the document link index (ADR 0015)
    InventoryLink link = new InventoryLink();
    link.setRelationType("IsSourceOf");
    link.setFilestoreTarget(savedStore("invlinkfs3"), "/XRD/run.dat");
    dao.save(link);
    flushDatabaseState();

    assertThat(
            sessionFactory
                .getCurrentSession()
                .createQuery(
                    "from InventoryLink where targetAbsolutePath = :p", InventoryLink.class)
                .setParameter("p", "data/xrd/run.dat")
                .list())
        .isEmpty();
  }

  private InventoryLink newLink(
      String globalId, GlobalIdPrefix prefix, Long dbId, String relationType) {
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId(globalId);
    link.setTargetPrefix(prefix);
    link.setTargetDbId(dbId);
    link.setRelationType(relationType);
    return link;
  }
}

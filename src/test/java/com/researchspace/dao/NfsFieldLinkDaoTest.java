package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class NfsFieldLinkDaoTest extends SpringTransactionalTest {

  @Autowired private NfsDao nfsDao;
  @Autowired private RecordDao recordDao;

  private NfsFileStore savedStore(User u, String basePath) {
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsDao.saveNfsFileSystem(fs);
    NfsFileStore store = GalleryFilestoreTestUtils.createFileStore("s3", u, fs);
    store.setPath(basePath);
    nfsDao.saveNfsFileStore(store);
    return store;
  }

  @Test
  public void findsCurrentDocumentsLinkingAnyPathOnTheFilesystem() {
    User u = createAndSaveUserIfNotExists("nfslinkdao");
    initialiseContentWithEmptyContent(u);
    NfsFileStore store = savedStore(u, "/data");
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    Field field = doc.getFields().get(0);
    nfsDao.saveFieldLink(new NfsFieldLink(field, store, "/XRD/run.dat"));
    nfsDao.saveFieldLink(new NfsFieldLink(field, store, "/XRD"));
    // another user's filestore rooted at the bucket reaches the same file through another base path
    User other = createAndSaveUserIfNotExists("nfslinkdao3");
    initialiseContentWithEmptyContent(other);
    NfsFileStore otherStore =
        GalleryFilestoreTestUtils.createFileStore("s3-root", other, store.getFileSystem());
    nfsDao.saveNfsFileStore(otherStore);
    StructuredDocument otherDoc = createBasicDocumentInRootFolderWithText(other, "any");
    nfsDao.saveFieldLink(
        new NfsFieldLink(otherDoc.getFields().get(0), otherStore, "/data/XRD/run.dat"));

    assertThat(nfsDao.getFieldLinks(field.getId())).hasSize(2);
    Long fsId = store.getFileSystem().getId();
    assertThat(nfsDao.getDocumentsLinkingTo(fsId, List.of("data/XRD", "data/XRD/run.dat")))
        .extracting(StructuredDocument::getId)
        .containsExactly(doc.getId(), otherDoc.getId());
    assertThat(nfsDao.getDocumentsLinkingTo(fsId, List.of("data/XRD")))
        .extracting(StructuredDocument::getId)
        .containsExactly(doc.getId());
    assertThat(nfsDao.getDocumentsLinkingTo(fsId, List.of("data/other.dat"))).isEmpty();
    assertThat(nfsDao.getDocumentsLinkingTo(fsId, List.of())).isEmpty();
  }

  @Test
  public void ignoresDeletedDocumentsAndDeletesRows() {
    User u = createAndSaveUserIfNotExists("nfslinkdao2");
    initialiseContentWithEmptyContent(u);
    NfsFileStore store = savedStore(u, "");
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    Field field = doc.getFields().get(0);
    NfsFieldLink link = new NfsFieldLink(field, store, "/run.dat");
    nfsDao.saveFieldLink(link);
    Long fsId = store.getFileSystem().getId();
    assertThat(nfsDao.getDocumentsLinkingTo(fsId, List.of("run.dat"))).hasSize(1);

    doc.setRecordDeleted(true);
    recordDao.save(doc);
    assertThat(nfsDao.getDocumentsLinkingTo(fsId, List.of("run.dat"))).isEmpty();

    nfsDao.deleteFieldLink(link);
    assertThat(nfsDao.getFieldLinks(field.getId())).isEmpty();
  }
}

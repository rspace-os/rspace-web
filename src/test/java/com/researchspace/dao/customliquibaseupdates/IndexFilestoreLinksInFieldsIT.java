package com.researchspace.dao.customliquibaseupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.NfsFieldLinkManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.interceptor.TransactionInterceptor;

public class IndexFilestoreLinksInFieldsIT extends RealTransactionSpringTestBase {

  @Autowired private NfsDao nfsDao;
  @Autowired private NfsFieldLinkManager nfsFieldLinkManager;

  @Autowired
  @Qualifier("txAdvice")
  private TransactionInterceptor txAdvice;

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void indexesExistingLinksAndSkipsUnknownFilestores() throws Exception {
    User u = loggedInUser();
    NfsFileStore store = committedS3Store(u);
    StructuredDocument known = createDocumentLinking(u, store.getId(), "/run.dat");
    StructuredDocument foreign = createDocumentLinking(u, 999999L, "/run.dat");
    assertNoLinksIndexed(known.getFields().get(0).getId());

    IndexFilestoreLinksInFields task = new IndexFilestoreLinksInFields();
    task.setUp();
    task.execute(null);

    openTransaction();
    assertThat(nfsFieldLinkManager.getLinkedDocuments(store.getId(), "/run.dat", u))
        .extracting(RecordInformation::getId)
        .containsExactly(known.getId());
    assertThat(nfsDao.getFieldLinks(known.getFields().get(0).getId())).hasSize(1);
    assertThat(nfsDao.getFieldLinks(foreign.getFields().get(0).getId())).isEmpty();
    commitTransaction();
  }

  @Test
  public void aFieldThatCannotBeIndexedDoesNotStopTheOthersBeingCommitted() throws Exception {
    User u = loggedInUser();
    NfsFileStore store = committedS3Store(u);
    StructuredDocument broken = createDocumentLinking(u, store.getId(), "/broken.dat");
    StructuredDocument good = createDocumentLinking(u, store.getId(), "/good.dat");
    Long brokenFieldId = broken.getFields().get(0).getId();
    assertNoLinksIndexed(good.getFields().get(0).getId());
    // the throw must happen inside the manager's transaction advice, as it would in production
    NfsFieldLinkManager failingOnBroken =
        mock(NfsFieldLinkManager.class, delegatesTo(nfsFieldLinkManager));
    doThrow(new IllegalStateException("cannot index"))
        .when(failingOnBroken)
        .reindex(argThat(field -> brokenFieldId.equals(field.getId())), anyString());
    ProxyFactory transactional = new ProxyFactory(failingOnBroken);
    transactional.addAdvice(txAdvice);

    IndexFilestoreLinksInFields task = new IndexFilestoreLinksInFields();
    task.setUp();
    task.nfsFieldLinkManager = (NfsFieldLinkManager) transactional.getProxy();
    task.execute(null);

    openTransaction();
    assertThat(nfsDao.getFieldLinks(good.getFields().get(0).getId())).hasSize(1);
    assertThat(nfsDao.getFieldLinks(brokenFieldId)).isEmpty();
    commitTransaction();
  }

  @Test
  public void aLinkOnlyInAPendingAutosaveIsIndexedOnItsDocumentField() throws Exception {
    User u = loggedInUser();
    NfsFileStore store = committedS3Store(u);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "saved text");
    Field field = doc.getFields().get(0);
    String pendingLink =
        "<a href=\"#\" class=\"nfs_file mceNonEditable\" data-linktype=\"file\""
            + " data-nfsid=\"42\" rel=\""
            + store.getId()
            + ":/run.dat\">run.dat</a>";
    recordMgr.saveTemporaryDocument(field.getId(), u, "saved text" + pendingLink);
    openTransaction();
    Long autosaveCopyId = fieldMgr.get(field.getId(), u).orElseThrow().getTempField().getId();
    // an upgraded database has no rows for the autosave the live code would have indexed
    nfsDao.getFieldLinks(field.getId()).forEach(nfsDao::deleteFieldLink);
    commitTransaction();

    IndexFilestoreLinksInFields task = new IndexFilestoreLinksInFields();
    task.setUp();
    task.execute(null);

    openTransaction();
    assertThat(nfsDao.getFieldLinks(field.getId()))
        .singleElement()
        .satisfies(
            row -> {
              assertEquals("/run.dat", row.getRelPath());
              assertEquals(42L, row.getNfsId());
            });
    assertThat(nfsDao.getFieldLinks(autosaveCopyId)).isEmpty();
    commitTransaction();
  }

  private void assertNoLinksIndexed(Long fieldId) {
    openTransaction();
    assertThat(nfsDao.getFieldLinks(fieldId)).isEmpty();
    commitTransaction();
  }

  private User loggedInUser() throws Exception {
    User u = createAndSaveUser(getRandomAlphabeticString("bf"));
    initUser(u);
    logoutAndLoginAs(u);
    return u;
  }

  private NfsFileStore committedS3Store(User owner) {
    openTransaction();
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsMgr.saveNfsFileSystem(fs);
    NfsFileStore store = nfsMgr.createAndSaveNewFileStore(fs.getId(), "s3", "/data", owner);
    commitTransaction();
    return store;
  }

  private StructuredDocument createDocumentLinking(User u, Long fileStoreId, String path) {
    return createBasicDocumentInRootFolderWithText(
        u, richTextUpdater.generateURLStringForNfs(fileStoreId, path, false));
  }
}

package com.researchspace.dao.customliquibaseupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.User;
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

    IndexFilestoreLinksInFields task = new IndexFilestoreLinksInFields();
    openTransaction();
    task.setUp();
    task.execute(null);
    commitTransaction();

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

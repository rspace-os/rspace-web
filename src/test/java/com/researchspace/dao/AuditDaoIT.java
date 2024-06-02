package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * This test class is outside of the Spring tests transaction environment. This is because auditing
 * only happens after a transaction is really committed to the database, and regular Spring Tests
 * always roll back. <br>
 * Therefore it's really important to ensure that all entries made to the DB during these tests are
 * removed afterwards.
 */
public class AuditDaoIT extends RealTransactionSpringTestBase {

  private static final String OLD_DATE = "1970-10-23";
  private static final String NEW_DATE = "2000-12-22";
  private static final String SDOC_NAME = "SDOC_NAME";

  private @Autowired AuditDao dao;
  private @Autowired FormDao formDao;
  private @Autowired RecordDao recordDao;
  private @Autowired FolderDao folderDao;

  private RecordFactory recordFactory = new RecordFactory();
  private User user;
  private RSForm form;
  private List<BaseRecord> recorded = new ArrayList<BaseRecord>();

  @Before
  public void setUp() {

    user = createInitAndLoginAnyUser();
    form = recordFactory.createFormForSeleniumTests("sed", "", user);
    recorded.clear();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testGetHistoryForArchiving() throws Exception {

    openTransaction();
    int initDocs = dao.getRecordsToArchive(1).size();
    commitTransaction();

    StructuredDocument[] sdocs = createNDocuments(4);
    makeNModifications(3, sdocs);
    final int EXPECTED_TO_REMOVE = 12;
    doInTransaction(
        () -> {
          Collection<BaseRecord> test1 = dao.getRecordsToArchive(2);
          assertTrue(test1.size() > 0);
          assertEquals(8, test1.size()); // 4 records * 2 modifications to archive

          Collection<BaseRecord> test2 = dao.getRecordsToArchive(1);
          assertEquals(
              initDocs + EXPECTED_TO_REMOVE,
              test2.size()); // 4 records * 3 modifications to archive
        });
    doInTransaction(
        () -> {
          int numDeleted = dao.deleteOldArchives(1);
          assertEquals(initDocs + EXPECTED_TO_REMOVE, numDeleted);
          PaginationCriteria<AuditedRecord> pgCrit = createDefaultPagCrit();

          // check there is now only 1 revision left for each entity after deletion
          for (StructuredDocument deleted : sdocs) {
            assertEquals(1, dao.getRevisionsForDocument(deleted, pgCrit).size());
          }
        });
  }

  private PaginationCriteria<AuditedRecord> createDefaultPagCrit() {
    return PaginationCriteria.createDefaultForClass(AuditedRecord.class);
  }

  @Test
  public void testGetDeleted() throws Exception {

    StructuredDocument sd1 = insertFormAndDocument();
    // now modify a field several times and commit each change
    modifyDocument(sd1, NEW_DATE);
    modifyDocument(sd1, "2000-11-27");
    modifyDocument(sd1, "2000-11-28");

    deleteDocAndCommit(sd1);
    // now check revisions
    openTransaction();

    // insert, 3 mods + delete
    assertEquals(5, dao.getRevisionCountForDocument(sd1, null).intValue());
    PaginationCriteria<AuditedRecord> pgCrit = createDefaultPagCrit();
    assertEquals(5, dao.getRevisionsForDocument(sd1, pgCrit).size()); // sanity check
    ISearchResults<AuditedRecord> asd =
        dao.getRestorableDeletedRecords(user, sd1.getName(), pgCrit);
    assertEquals(1, asd.getHits().intValue());
    // test substring
    ISearchResults<AuditedRecord> asd2 =
        dao.getRestorableDeletedRecords(user, sd1.getName().substring(0, 4), pgCrit);
    assertEquals(1, asd2.getHits().intValue());

    ISearchResults<AuditedRecord> asd3 = dao.getRestorableDeletedRecords(user, "nohit", pgCrit);
    assertEquals(0, asd3.getHits().intValue());

    // the SD will have all null fields
    // assertEquals("DEL",asd.get(0).getRevisionTypeString());
    commitTransaction();
  }

  private void deleteDocAndCommit(StructuredDocument sd1) throws Exception {
    // now delete the  document
    doInTransaction(
        () -> { // none deleted at the moment
          assertEquals(
              0,
              dao.getRestorableDeletedRecords(
                      user,
                      sd1.getName(),
                      PaginationCriteria.createDefaultForClass(AuditedRecord.class))
                  .getHits()
                  .intValue());
          sd1.setRecordDeleted(true);
          sd1.getParents().iterator().next().markRecordInFolderDeleted(true);
          recordDao.save(sd1);
        });
  }

  /*
   * Opens and closes transactions
   */
  private void modifyDocument(StructuredDocument sd1, String data) throws InterruptedException {

    openTransaction();
    StructuredDocument sd2 = (StructuredDocument) recordDao.get(sd1.getId());
    Collections.sort(sd2.getFields());
    // text data
    sd2.getFields().get(4).setFieldData(data);
    Thread.sleep(10); // ensure is later.
    sd2.setModificationDate(new Date().getTime());
    recordDao.save(sd2);
    commitTransaction();
  }

  @Test
  public void testLatestModifications() throws IllegalAddChildOperation, InterruptedException {
    StructuredDocument sd1 = insertFormAndDocument();
    makeNModifications(3, new StructuredDocument[] {sd1});
    openTransaction();
    List<AuditedRecord> revisions = dao.getRevisionsForDocument(sd1, null);
    int maxRevision = getNewestRevisionManually(revisions);
    assertEquals(
        maxRevision,
        dao.getNewestRevisionForEntity(StructuredDocument.class, sd1.getId()).getRevision());
    commitTransaction();
  }

  private int getNewestRevisionManually(List<AuditedRecord> revisions) {
    int maxRevision = Integer.MIN_VALUE;
    for (AuditedRecord ar : revisions) {
      if (ar.getRevision().intValue() > maxRevision) {
        maxRevision = ar.getRevision().intValue();
      }
    }
    return maxRevision;
  }

  @Test
  public void testBasicAuditOfDocument() throws InterruptedException, IllegalAddChildOperation {

    StructuredDocument sd1 = insertFormAndDocument();

    // now modify a field
    openTransaction();
    StructuredDocument sd2 = (StructuredDocument) recordDao.get(sd1.getId());
    Collections.sort(sd2.getFields());
    sd2.getFields().get(0).setFieldData(NEW_DATE);
    Thread.sleep(1000); // ensure is later.
    sd2.setModificationDate(new Date().getTime());
    recordDao.save(sd2);
    commitTransaction();

    // now check revisions
    openTransaction();
    PaginationCriteria<AuditedRecord> pgCrit = createDefaultPagCrit();
    List<AuditedRecord> rc = dao.getRevisionsForDocument(sd1, pgCrit);

    assertEquals(2, rc.size()); // original insert, plus modification
    assertEquals(NEW_DATE, rc.get(1).getRecordAsDocument().getFields().get(0).getFieldData());
    commitTransaction();

    // now check getting the old version
    openTransaction();
    Number firstRevision = rc.get(0).getRevision();
    AuditedRecord asd = dao.getDocumentForRevision(sd1, firstRevision);
    assertEquals(OLD_DATE, asd.getRecordAsDocument().getFields().get(0).getFieldData());
    commitTransaction();
  }

  @Test
  public void testBasicAuditOfGalleryFile() throws Exception {

    // let's add two gallery items
    EcatDocumentFile docFile = addDocumentToGallery(user);
    String docFileOrgFile = docFile.getName();
    String docFileOrgFilenameFromFP = docFile.getFileProperty().getFileName();
    EcatImage imageFile = addImageToGallery(user);

    // confirm one revision
    openTransaction();
    List<AuditedEntity<EcatDocumentFile>> docFileRevisions =
        dao.getRevisionsForObject(EcatDocumentFile.class, docFile.getId());
    assertEquals(1, docFileRevisions.size());
    commitTransaction();

    // now let's change doc file: fileproperty pointing to image file and new modification date
    openTransaction();
    Thread.sleep(50); // ensure is later.
    docFile.setFileProperty(imageFile.getFileProperty());
    docFile.setModificationDate(new Date().getTime());
    recordDao.save(docFile);
    commitTransaction();

    // now check both doc file revisions
    openTransaction();
    docFileRevisions = dao.getRevisionsForObject(EcatDocumentFile.class, docFile.getId());
    assertEquals(2, docFileRevisions.size());
    EcatDocumentFile firstRev = docFileRevisions.get(0).getEntity();
    EcatDocumentFile secondRev = docFileRevisions.get(1).getEntity();
    assertEquals(docFileOrgFile, firstRev.getName());
    assertEquals(docFileOrgFilenameFromFP, firstRev.getFileName());
    assertEquals(docFileOrgFile, secondRev.getName());
    assertEquals(
        imageFile.getFileProperty().getFileName(), secondRev.getFileProperty().getFileName());
    commitTransaction();
  }

  @Test
  public void testGetEveryDocByUser() throws IllegalAddChildOperation, InterruptedException {

    openTransaction();
    int initDocs = dao.getEveryDocumentAndRevisionModifiedByUser(user).size();
    commitTransaction();

    // inserts and modify doc for user
    StructuredDocument sd = insertFormAndDocument();
    modifyDocument(sd, "newData");

    openTransaction();
    assertEquals(initDocs + 2, dao.getEveryDocumentAndRevisionModifiedByUser(user).size());
    assertEquals(0, dao.getEveryDocumentAndRevisionModifiedByUser(new User("unnown")).size());
    commitTransaction();
  }

  private StructuredDocument[] createNDocuments(int n) throws IllegalAddChildOperation {
    StructuredDocument[] rc = new StructuredDocument[n];
    for (int i = 0; i < n; i++) {
      rc[i] = insertFormAndDocument();
    }
    return rc;
  }

  private int curr = 1;
  private String newData = curr + "";

  /*
   * gets  a new data that will trigger a new audit
   */
  private String newData() {
    curr++;
    newData = curr + "";
    return newData;
  }

  private void makeNModifications(int n, StructuredDocument[] docs) throws InterruptedException {
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < docs.length; j++) {
        modifyDocument(docs[j], newData());
      }
    }
  }

  // inserts and commits a template and document
  private StructuredDocument insertFormAndDocument() throws IllegalAddChildOperation {

    openTransaction();
    formDao.save(form);
    Folder f1 = folderDao.get(user.getRootFolder().getId());
    StructuredDocument sd = recordFactory.createStructuredDocument(SDOC_NAME, user, form);
    recorded.add(sd);
    recorded.add(f1);
    Collections.sort(sd.getFields());
    sd.getFields().get(0).setFieldData(OLD_DATE);
    f1.addChild(sd, user);
    recordDao.save(sd);
    folderDao.save(f1);
    commitTransaction();

    return sd;
  }

  public static TransactionStatus openTransaction(PlatformTransactionManager txMgr) {
    return txMgr.getTransaction(
        new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED));
  }
}

package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This test class is outside of the Spring tests transaction environment. This is because auditing
 * only happens after a transaction is really committed to the database, and regular Spring Tests
 * always roll back. <br>
 * Therefore it's really important to ensure that all entries made to the DB during these tests are
 * removed afterwards.
 */
public class AuditManager2IT extends RealTransactionSpringTestBase {

  private @Autowired AuditManager auditMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void getCommentRevisions() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();

    EcatComment comment = addNewCommentToField("comment1", doc.getFields().get(0), piUser);

    assertEquals(1, auditMgr.getRevisionsForEntity(EcatComment.class, comment.getComId()).size());
    // simulate saving of text field following comment addition.
    recordMgr.forceVersionUpdate(doc.getId(), DeltaType.COMMENT, null, piUser);
    addNewCommentItemToExistingComment(
        "comment2", comment.getComId(), doc.getFields().get(0), piUser);
    List<AuditedEntity<EcatComment>> history =
        auditMgr.getRevisionsForEntity(EcatComment.class, comment.getComId());
    assertEquals(2, history.size());
    AuditedEntity<StructuredDocument> mostRecentDoc =
        auditMgr.getNewestRevisionForEntity(StructuredDocument.class, doc.getId());
    assertEquals(
        1, // no longer force revision update, for RSPAC-311 bug
        auditMgr
            .getCommentItemsForCommentAtDocumentRevision(
                comment.getComId(), mostRecentDoc.getRevision().intValue())
            .size());
  }

  @Test
  public void getImageAnnotationRevisions() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    EcatImageAnnotation annotation = addImageAnnotationToField(doc.getFields().get(0), piUser);
    assertEquals(
        1, auditMgr.getRevisionsForEntity(EcatImageAnnotation.class, annotation.getId()).size());
    updateExistingImageAnnotation(
        annotation.getId(),
        doc.getFields().get(0),
        piUser,
        getTestZwibblerAnnotationString(getRandomName(5)));
    assertEquals(
        2, auditMgr.getRevisionsForEntity(EcatImageAnnotation.class, annotation.getId()).size());
  }

  @Test
  public void getChemStructureRevisions() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    RSChemElement chemElement = addChemStructureToField(doc.getFields().get(0), piUser);
    assertEquals(
        1, auditMgr.getRevisionsForEntity(RSChemElement.class, chemElement.getId()).size());
    updateExistingChemElement(chemElement.getId(), doc.getFields().get(0), piUser);
    assertEquals(
        2, auditMgr.getRevisionsForEntity(RSChemElement.class, chemElement.getId()).size());
  }

  @Test
  public void getRevision() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // Transaction end
    int ORIGINAL_COUNT = 1;
    assertNRevisionsForDocument(doc, ORIGINAL_COUNT, null);
    final int NUM_UPDATES = 12;
    doc = renameDocumentNTimes(doc, NUM_UPDATES); // force paginated response
    // total number of revisions
    assertNRevisionsForDocument(doc, ORIGINAL_COUNT + NUM_UPDATES, null);

    // if pgcriteria is null, we get all records:
    List<AuditedRecord> historyListAll = auditMgr.getHistory(doc, null);
    assertEquals(ORIGINAL_COUNT + NUM_UPDATES, historyListAll.size());

    List<AuditedRecord> historyList =
        auditMgr.getHistory(doc, createDefaultAuditedRecordListPagCrit());
    assertEquals(PaginationCriteria.getDefaultResultsPerPage(), historyList.size());
    Number revision = historyList.get(0).getRevision();

    // now we'll check search:
    RevisionSearchCriteria searchCrit =
        new RevisionSearchCriteria(piUser.getUsername(), "1900-01-23,2100-01-23");
    PaginationCriteria<AuditedRecord> pgCrit = createDefaultAuditedRecordListPagCrit();
    pgCrit.setSearchCriteria(searchCrit);
    List<AuditedRecord> historyList2 = auditMgr.getHistory(doc, pgCrit);
    assertEquals(PaginationCriteria.getDefaultResultsPerPage(), historyList2.size());
    assertNRevisionsForDocument(doc, ORIGINAL_COUNT + NUM_UPDATES, searchCrit);

    // now check search using unknown name, should return none:
    searchCrit.setModifiedBy("UNKNOWN");
    List<AuditedRecord> historyList3 = auditMgr.getHistory(doc, pgCrit);
    assertEquals(0, historyList3.size());
    assertNRevisionsForDocument(doc, 0, searchCrit);

    // now lets search between an impossible date range, should return none
    searchCrit.setDateRange("2500-01-01");
    searchCrit.setModifiedBy(piUser.getUsername());
    List<AuditedRecord> historyList4 = auditMgr.getHistory(doc, pgCrit);
    assertEquals(0, historyList4.size());
    assertNRevisionsForDocument(doc, 0, searchCrit);

    // now lets search between an impossible date range, should return none
    searchCrit.setDateRange("1918-06-01, 2013-01-01");
    List<AuditedRecord> historyList5 = auditMgr.getHistory(doc, pgCrit);
    assertEquals(0, historyList5.size());
    assertNRevisionsForDocument(doc, 0, searchCrit);

    // now let's search by modification of a field
    // create a document with several text fields
    RSForm form = new RecordFactory().createExperimentForm("EXperiment", " ", piUser);
    formMgr.save(form, piUser);
    // modify each field in turn so each field has a revision.
    StructuredDocument sd = createDocumentInFolder(root, form, piUser);
    for (Field field : sd.getFields()) {
      if (field.getType().equals(FieldType.TEXT)) {
        field.setFieldData("new data");
        fieldMgr.save(field, null);
        recordMgr.save(sd, piUser);
        sd.clearDelta();
      }
    }

    searchCrit.setSelectedFields(new String[] {"Objective", "Method"});
    searchCrit.setDateRange(null); // clear date selection
    List<AuditedRecord> historyList6 = auditMgr.getHistory(sd, pgCrit);
    // there is one revision of each method, so there should be two matches,
    // as these queries are 'or'd together.
    assertEquals(2, historyList6.size());
    assertNRevisionsForDocument(sd, 2, searchCrit);

    AuditedRecord oldversion = auditMgr.getDocumentRevisionOrVersion(doc, revision, null);
    assertEquals(StructuredDocument.DEFAULT_NAME, oldversion.getRecord().getName());

    User other = createAndSaveUser(getRandomName(10));

    RSpaceTestUtils.logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    auditMgr.getDocumentRevisionOrVersion(doc, revision, null);
  }

  // sets up a folder hierarchy. Each nested level has name 'Sharedi' where
  // 'i' is depth of folder hierarchy
  // returns the child folder of the argment root.
  /*
   * returns a flat array of the folders in order of increasing depth in the
   * hierarchy
   */
  Folder[] setUpFolderHierarchyNLevelsDeep(int n, Folder root) throws Exception {
    Folder[] rc = new Folder[n];
    Folder parent = root;

    for (int i = 0; i < n; i++) {
      parent = folderMgr.createNewFolder(parent.getId(), "Shared" + i, piUser);
      Thread.sleep(1); // ensure equals() different for each
      rc[i] = parent;
    }
    return rc;
  }

  @Test
  //  @Ignore("inconsistnetly fails with ConcurrentModificationException")
  public void viewDeletedFolderOnlyShowsTopLevelFolder() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();

    Folder[] children = setUpFolderHierarchyNLevelsDeep(3, root);
    recordMgr.move(doc.getId(), children[1].getId(), doc.getParent().getId(), piUser);

    // so now we have :RootFolder->ChildFolder->grandchild->Record
    recordDeletionMgr.deleteFolder(children[0].getId(), children[1].getId(), piUser);

    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    // i.e., only top level deleted folder is shown
    openTransaction();
    AuditedRecord ar =
        auditMgr
            .getDeletedDocuments(
                piUser, "", PaginationCriteria.createDefaultForClass(AuditedRecord.class))
            .getFirstResult();
    commitTransaction();
    assertTrue(ar.getRecord().isFolder());
    assertEquals(children[1].getName(), ar.getRecord().getName());
  }

  @Test
  public void moveDoesntTriggerRevision() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    int revisions = auditMgr.getNumRevisionsForDocument(doc.getId(), null);
    Folder root = getRootFolderForUser(piUser);
    Folder otherFolder = createSubFolder(root, "xxx", piUser);
    recordMgr.move(doc.getId(), otherFolder.getId(), root.getId(), piUser);
    int afterMoveRevisions = auditMgr.getNumRevisionsForDocument(doc.getId(), null);
    assertEquals(revisions, afterMoveRevisions);
  }

  @Test
  public void lastParentInAllRevisionsOfMovedDocument_RSPAC_1304() throws Exception {

    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    Folder rootFolder = doc.getParent();
    Folder targetFolder = createSubFolder(rootFolder, "moveTargetFolder", piUser);
    final String newname = "exportTest";
    recordMgr.renameRecord(newname, doc.getId(), piUser);
    recordMgr.move(doc.getId(), targetFolder.getId(), rootFolder.getId(), piUser);

    doc = recordMgr.get(doc.getId()).asStrucDoc();
    assertEquals(newname, doc.getName());
    assertEquals(targetFolder, doc.getParent());

    List<AuditedRecord> allRevisions = auditMgr.getHistory(doc, null);
    assertEquals(2, allRevisions.size()); // two revisions

    AuditedRecord firstRevision =
        auditMgr.getDocumentRevisionOrVersion(doc, allRevisions.get(0).getRevision(), null);
    AuditedRecord lastRevision =
        auditMgr.getDocumentRevisionOrVersion(doc, allRevisions.get(1).getRevision(), null);

    assertEquals(StructuredDocument.DEFAULT_NAME, firstRevision.getRecord().getName());
    assertEquals(targetFolder, firstRevision.getRecord().getOwnerParent().get());
    assertEquals(newname, lastRevision.getRecord().getName());
    assertEquals(targetFolder, lastRevision.getRecord().getOwnerParent().get());
  }

  @Test
  public void restoreDeletedFolder() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    final Long EXPECTED_IN_ROOT = 7L;
    Folder[] children = setUpFolderHierarchyNLevelsDeep(3, root);
    recordMgr.move(doc.getId(), children[2].getId(), doc.getParent().getId(), piUser);
    assertEquals(
        EXPECTED_IN_ROOT,
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits());
    // so now we have
    // :RootFolder->ChildFolder->grandchild->great-grndchild->Record
    // delete child
    recordDeletionMgr.deleteFolder(root.getId(), children[0].getId(), piUser);
    // check that document is marked deleted
    assertTrue(recordMgr.get(doc.getId()).isDeleted());
    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    assertEquals(
        EXPECTED_IN_ROOT - 1,
        recordMgr
            .listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION)
            .getTotalHits()
            .intValue());

    RestoreDeletedItemResult restoreDeletedItemResult =
        auditMgr.fullRestore(children[0].getId(), piUser);
    // 3 folders and document
    assertEquals(4, restoreDeletedItemResult.getRestoredItemCount());
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    assertEquals(
        EXPECTED_IN_ROOT,
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits());

    assertFalse(recordMgr.get(doc.getId()).isDeleted());
    // check all folders, records etc are restored
    openTransaction();
    for (Folder original : children) {
      Folder refreshed = folderDao.get(original.getId());
      assertFalse(refreshed.isDeleted());
      assertTrue(
          refreshed.getChildren().stream().noneMatch(RecordToFolder::isRecordInFolderDeleted));
    }
    commitTransaction();
  }

  @Test
  public void restoreMultiFieldRevisionAsCurrent_RSPAC1623() {
    root = initUser(piUser);
    logoutAndLoginAs(piUser);
    RSForm multFieldForm = createAnExperimentForm("expt", piUser);
    StructuredDocument doc = createDocumentInFolder(root, multFieldForm, piUser);

    // make 2 revisions
    IntStream.range(0, doc.getFieldCount())
        .forEach(
            i -> {
              doc.getFields().get(i).setFieldData(i + "Rev2");
            });
    recordMgr.save(doc, piUser);

    IntStream.range(0, doc.getFieldCount())
        .forEach(
            i -> {
              doc.getFields().get(i).setFieldData(i + "Rev3");
            });
    recordMgr.save(doc, piUser);
    final Number originalRevision = getNthRevisionForDocument(doc, 1);
    StructuredDocument restoredFirstRevisionDoc =
        auditMgr.restoreRevisionAsCurrent(originalRevision, doc.getId());
    // assert that ordering of revision is same as original
    for (int i = 0; i < doc.getFieldCount(); i++) {
      assertEquals(i + "Rev2", restoredFirstRevisionDoc.getFields().get(i).getFieldData());
    }
  }

  @Test
  public void restoreRevision() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    final Long DOC_ID = doc.getId();

    int numFields = doc.getFieldCount();
    if (numFields == 0) {
      fail("Should have fields!!");
    }
    doc.getFields().get(0).setFieldData("New data1");
    recordMgr.save(doc, piUser);

    doc = (StructuredDocument) recordMgr.get(DOC_ID);
    doc.getFields().get(0).setFieldData("New data2");
    Long fieldId = doc.getFields().get(0).getId();
    recordMgr.save(doc, piUser);

    // initial creation, + 2 revisions = 3
    assertEquals(3, auditMgr.getNumRevisionsForDocument(DOC_ID, null).intValue());
    final Number firstRevission = getNthRevisionForDocument(doc, 1);
    // revision 4
    StructuredDocument restoredFirstRevisionDoc =
        auditMgr.restoreRevisionAsCurrent(firstRevission, DOC_ID);
    assertEquals("New data1", restoredFirstRevisionDoc.getFields().get(0).getFieldData());
    // restore
    assertEquals(4, auditMgr.getNumRevisionsForDocument(DOC_ID, null).intValue());

    assertTrue(restoredFirstRevisionDoc.getDeltaStr().contains(DeltaType.RESTORED.toString()));
    Field newestRevision = auditMgr.getNewestRevisionForEntity(Field.class, fieldId).getEntity();
    // check that the latest revision in the _AUD table is the same as the current document.
    // RSPAC-222
    assertEquals(
        restoredFirstRevisionDoc.getFields().get(0).getFieldData(), newestRevision.getFieldData());

    StructuredDocument newCurrent = (StructuredDocument) recordMgr.get(DOC_ID);
    assertUserVersionIncremented(doc, newCurrent);
    assertFalse(newCurrent.isDeleted());

    // now check that cannot restore from signed document
    newCurrent.setSigned(true);
    recordMgr.save(newCurrent, piUser);
    assertAuthorisationExceptionThrown(
        () -> auditMgr.restoreRevisionAsCurrent(firstRevission, DOC_ID));
  }

  @Test
  public void restoreRevisionWithLinks() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    Field field = doc.getFields().get(0);
    EcatImageAnnotation ann = addImageAnnotationToField(field, piUser);
    // revision 1
    recordMgr.save(doc, piUser);
    String newAnnotation = ann.getAnnotations() + "revised";
    ann.setAnnotations(newAnnotation);
    // save new version of image annotation
    mediaMgr.saveImageAnnotation(
        newAnnotation,
        getBase64Image(),
        ann.getParentId(),
        ann.getRecord(),
        ann.getImageId(),
        piUser);
    // so now, there are 2 revisions of
    assertEquals(2, auditMgr.getRevisionsForEntity(EcatImageAnnotation.class, ann.getId()).size());

    // now we'll add some text to prompt revision 2 of document
    field.setFieldData(field.getFieldData() + "<p>New data</p>");
    recordMgr.save(doc, piUser);

    // now restore 2nd revision of doc - i.e., that which should link to 1st annotation
    List<AuditedEntity<StructuredDocument>> revisions =
        auditMgr.getRevisionsForEntity(StructuredDocument.class, doc.getId());
    assertEquals(3, revisions.size());
    final int firstAnnotationRevision = revisions.get(1).getRevision().intValue();
    StructuredDocument restored =
        auditMgr.restoreRevisionAsCurrent(firstAnnotationRevision, doc.getId());
    String restoredField = restored.getFields().get(0).getFieldData();
    assertTrue(restoredField.contains("revision=" + firstAnnotationRevision));
  }

  private void assertUserVersionIncremented(StructuredDocument from, StructuredDocument to) {
    assertTrue(to.getUserVersion().after(from.getUserVersion()));
  }

  private void assertUserVersionNotIncremented(StructuredDocument from, StructuredDocument to) {
    assertFalse(to.getUserVersion().after(from.getUserVersion()));
  }

  @Test
  public void simpleRestoreOfSingleDocumentAsOwner() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    long B4_COUNT = getTotalSearchHitsInFolder(root);
    final Long DOC_ID = doc.getId();

    recordDeletionMgr.deleteRecord(root.getId(), DOC_ID, piUser);

    // check appears deleted
    assertEquals(B4_COUNT - 1, getTotalSearchHitsInFolder(root));

    AuditedRecord record = auditMgr.restoredDeletedForView(DOC_ID);
    assertNotNull(record.getRecordAsDocument());

    // check this outside of DB session
    assertFieldsAreInitialized(record);

    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    auditMgr.fullRestore(DOC_ID, piUser);

    // i.e., original is now restored
    assertEquals(B4_COUNT, getTotalSearchHitsInFolder(root));
  }

  @Test
  public void sortDeleted() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    StructuredDocument sd1 = createBasicDocumentInRootFolderWithText(u1, "doc1");
    StructuredDocument sd2 = createBasicDocumentInRootFolderWithText(u1, "doc2");
    StructuredDocument sd3 = createBasicDocumentInRootFolderWithText(u1, "doc3");
    recordMgr.renameRecord("AAAA", sd1.getId(), u1);
    recordMgr.renameRecord("ZZZ", sd2.getId(), u1);
    recordMgr.renameRecord("MMM", sd3.getId(), u1);

    recordDeletionMgr.deleteRecord(sd1.getParent().getId(), sd1.getId(), u1);
    recordDeletionMgr.deleteRecord(sd2.getParent().getId(), sd2.getId(), u1);
    recordDeletionMgr.deleteRecord(sd3.getParent().getId(), sd3.getId(), u1);

    PaginationCriteria<AuditedRecord> pg =
        PaginationCriteria.createDefaultForClass(AuditedRecord.class);
    pg.setSortOrder(SortOrder.DESC);
    pg.setOrderBy("name");
    ISearchResults<AuditedRecord> res = auditMgr.getDeletedDocuments(u1, "", pg);
    assertEquals(sd2, res.getFirstResult().getRecord());

    pg.setSortOrder(SortOrder.ASC);
    res = auditMgr.getDeletedDocuments(u1, "", pg);
    assertEquals(sd1, res.getFirstResult().getRecord());

    pg.setOrderBy("creationDate");
    res = auditMgr.getDeletedDocuments(u1, "", pg);
    assertEquals(sd1, res.getFirstResult().getRecord());

    /* MK there is something strange with modification dates retrieved by auditMgr,
     * they don't necessarily follow order of modification calls or often are identical.
     * so for the test we just assert that result order is not incorrect */
    pg.setSortOrder(SortOrder.DESC);
    pg.setOrderBy("modificationDate");
    List<AuditedRecord> resList = auditMgr.getDeletedDocuments(u1, "", pg).getResults();
    assertEquals(3, resList.size());
    assertTrue(
        resList
                .get(0)
                .getEntity()
                .getModificationDate()
                .compareTo(resList.get(1).getEntity().getModificationDate())
            >= 0);
    assertTrue(
        resList
                .get(1)
                .getEntity()
                .getModificationDate()
                .compareTo(resList.get(2).getEntity().getModificationDate())
            >= 0);
  }

  @Test
  public void listDeletedWhereOwnerIsSubject() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    final Long DOC_ID = doc.getId();

    // delete record
    recordDeletionMgr.deleteRecord(doc.getParent().getId(), DOC_ID, piUser);

    // check it appears in the deleted documents list
    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));

    // now restore the deleted document
    auditMgr.fullRestore(DOC_ID, piUser);
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(piUser));
  }

  /*
   * Tests delete/restore when the subject is dealing with a document owned by
   * someone else
   */
  @Test
  public void listDeletedWhereShareeIsSubject() throws Exception {
    // set up group with a document
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    final Long DOC_ID = doc.getId();
    User other = createAndSaveUser(getRandomName(10));
    initUser(other);
    Group grp = createGroupForUsersWithDefaultPi(piUser, other);
    ShareConfigElement gsCommand = new ShareConfigElement(grp.getId(), "edit");
    sharingMgr.shareRecord(piUser, doc.getId(), new ShareConfigElement[] {gsCommand});

    logoutAndLoginAs(other);
    openTransaction();
    StructuredDocument otherDOc =
        createDocumentInFolder(folderDao.getRootRecordForUser(other), createAnyForm(other), other);
    commitTransaction();
    sharingMgr.shareRecord(other, otherDOc.getId(), new ShareConfigElement[] {gsCommand});
    // delete record from group folder
    grp = grpMgr.getGroup(grp.getId()); // refresh groups
    other = userMgr.get(other.getId()); // refresh user
    // we're dleting a record we own from the group folder - it should be
    // deleted from the grp folder
    // but not avaialble to restore.
    openTransaction();
    Folder shared = folderDao.getSharedFolderForGroup(grp);
    commitTransaction();
    recordDeletionMgr.deleteRecord(shared.getId(), otherDOc.getId(), other);
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    // it is actually deleted ( not just marked as deleted)
    // from shared folder; does not appear in deleted docs as is not
    // recoverable
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(other));

    // now, 'user' will delete the record from their own folder. AS they're
    // the owner, it should remove from
    // view for everyone, but should not be restorable by 'other'.
    logoutAndLoginAs(piUser);
    recordDeletionMgr.deleteRecord(root.getId(), DOC_ID, piUser);

    // check it appears in the deleted documents list for the owner,
    // but not in 'other'.
    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(other));
  }

  @Test
  public void savingUnchangedDocumentDoesNotCauseRevision() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // :XXX: I've had to increase by one no clue why its breaking LG
    // RA your change this break for me, so reverting... see what hudson
    // does..
    int NUM_REVISIONSB4__SAVE = getRevisionCount(doc);
    doc = (StructuredDocument) recordMgr.save(doc, piUser);
    // assert revision not listed if not modified
    assertEquals(NUM_REVISIONSB4__SAVE, getRevisionCount(doc));
  }

  @Test
  public void simpleRestoreOfSingleDocumentAsSharee() throws Exception {
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(10));
    initUser(other);
    Group grp = createGroupForUsersWithDefaultPi(piUser, other);
    ShareConfigElement gsCommand = new ShareConfigElement(grp.getId(), "edit");
    sharingMgr.shareRecord(piUser, doc.getId(), new ShareConfigElement[] {gsCommand});

    // test sharing has worked
    openTransaction();
    User other2 = userMgr.get(other.getId());
    grp = grpMgr.getGroup(grp.getId()); // refresh grp with shared folder id
    Folder labGrpFlder = folderDao.getSharedFolderForGroup(grp);
    assertEquals(1, labGrpFlder.getChildren().size());
    BaseRecord shared = (BaseRecord) labGrpFlder.getChildren().iterator().next().getRecord();
    commitTransaction();
    assertEquals(1, getTotalSearchHitsInFolder(labGrpFlder));

    final long ORIGINAL = getTotalSearchHitsInFolder(root);
    // // now the *owner* deletes the record.
    openTransaction();
    Folder userRoot = folderDao.getRootRecordForUser(piUser);
    commitTransaction();
    recordDeletionMgr.deleteRecord(userRoot.getId(), doc.getId(), piUser);

    assertEquals(ORIGINAL - 1, getTotalSearchHitsInFolder(root));
    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    // because the owner has deleted the record, it should not be available
    // to be restored by sharees.
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(other));
    // restore the deleted document by the user.
    auditMgr.fullRestore(shared.getId(), piUser);
    assertEquals(ORIGINAL, getTotalSearchHitsInFolder(root));
  }

  @Test
  public void restoreOfSingleDeletedDocument() throws Exception {
    // create Document owned by 'user'
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();

    // delete parent record
    recordDeletionMgr.deleteRecord(root.getId(), doc.getId(), piUser);

    // assert parent appears in deleted record list:
    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    RestoreDeletedItemResult restored = auditMgr.fullRestore(doc.getId(), piUser);
    assertEquals(doc, restored.getFirstItem().get());

    StructuredDocument restoredPar = (StructuredDocument) recordMgr.get(doc.getId());
    // check restored versions are incremented
    assertUserVersionNotIncremented(doc, restoredPar);
    assertFalse(restoredPar.isDeleted());
    // chek n othing is deleted
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(piUser));
  }

  @Test
  public void restoreOfFolderWithSharedDocumentInGroup() throws Exception {
    // set up group with a parent/child document
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    Folder[] children = setUpFolderHierarchyNLevelsDeep(3, root);

    recordMgr.move(doc.getId(), children[2].getId(), doc.getParent().getId(), piUser);

    User other = createAndSaveUser(getRandomName(10));
    initUser(other);

    Group grp = createGroupForUsersWithDefaultPi(piUser, other);
    ShareConfigElement gsCommand = new ShareConfigElement(grp.getId(), "edit");
    sharingMgr.shareRecord(piUser, doc.getId(), new ShareConfigElement[] {gsCommand});
    // sanity check that sharing works
    openTransaction();
    other = userMgr.getUserByUsername(other.getUsername());
    grp = grpMgr.getGroup(grp.getId()); // update group from DB
    Folder shared = folderDao.getSharedFolderForGroup(grp);
    assertEquals(1, shared.getChildren().size());
    commitTransaction();

    // now the owner deletes the top-level child folder:
    recordDeletionMgr.deleteFolder(root.getId(), children[0].getId(), piUser);
    assertEquals(1, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(other));
    // should be deleted for sharee as well
    assertEquals(0, getTotalSearchHitsInFolder(shared));

    // now restore - but sharing should not be restored
    auditMgr.fullRestore(children[0].getId(), piUser);
    assertEquals(0, getNumberOfVisibleDeletedDocumentsForUser(piUser));
    assertEquals(0, getTotalSearchHitsInFolder(shared));
  }

  @Test
  public void checkDocRevisionsAfterUploadingNewAttachmentVersionByOwnerAndSharee()
      throws IOException {

    // create doc, ensure just one rev
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    List<AuditedRecord> newDocRevs = auditMgr.getHistory(doc, null);
    int initDocRevs = newDocRevs.size();

    // add image to doc, ensure doc has two revs and image has one
    EcatImage image = addImageToField(doc.getFields().get(0), piUser);
    List<AuditedRecord> docWithImageRevs = auditMgr.getHistory(doc, null);
    assertEquals(initDocRevs + 1, docWithImageRevs.size());

    List<AuditedEntity<EcatImage>> imageRevs =
        auditMgr.getRevisionsForEntity(EcatImage.class, image.getId());
    int initImgRevs = imageRevs.size();

    // upload new version of the image
    updateImageInGallery(image.getId(), piUser);

    // check both image and doc have new revision
    List<AuditedEntity<EcatImage>> updatedImageRevs =
        auditMgr.getRevisionsForEntity(EcatImage.class, image.getId());
    assertEquals(initImgRevs + 1, updatedImageRevs.size());
    List<AuditedRecord> docWithUpdatedImageRevs = auditMgr.getHistory(doc, null);
    assertEquals(initDocRevs + 2, docWithUpdatedImageRevs.size());

    // check details of latest image and doc revision
    BaseRecord latestImg = updatedImageRevs.get(updatedImageRevs.size() - 1).getEntity();
    assertEquals(piUser.getUsername(), latestImg.getModifiedBy());
    StructuredDocument latestDoc =
        docWithUpdatedImageRevs.get(docWithUpdatedImageRevs.size() - 1).getEntity().asStrucDoc();
    assertEquals(piUser.getUsername(), latestDoc.getModifiedBy());
    assertEquals("ATTACHMENT_CHG-" + image.getId(), latestDoc.getDeltaStr());

    // share doc with another user for edit
    User other = createAndSaveUser(getRandomName(10));
    initUser(other);
    Group grp = createGroupForUsersWithDefaultPi(piUser, other);
    ShareConfigElement gsCommand = new ShareConfigElement(grp.getId(), "edit");
    sharingMgr.shareRecord(piUser, doc.getId(), new ShareConfigElement[] {gsCommand});

    // other user updates the image
    logoutAndLoginAs(other);
    updateImageInGallery(image.getId(), other);

    // confirm both image and doc are updated
    List<AuditedEntity<EcatImage>> twiceUpdatedImageRevs =
        auditMgr.getRevisionsForEntity(EcatImage.class, image.getId());
    assertEquals(initImgRevs + 2, twiceUpdatedImageRevs.size());
    List<AuditedRecord> docWithTwiceUpdatedImageRevs = auditMgr.getHistory(doc, null);
    assertEquals(initDocRevs + 3, docWithTwiceUpdatedImageRevs.size());

    // check details of latest image and doc revision
    latestImg = twiceUpdatedImageRevs.get(twiceUpdatedImageRevs.size() - 1).getEntity();
    assertEquals(other.getUsername(), latestImg.getModifiedBy());
    latestDoc =
        docWithTwiceUpdatedImageRevs
            .get(docWithTwiceUpdatedImageRevs.size() - 1)
            .getEntity()
            .asStrucDoc();
    assertEquals(other.getUsername(), latestDoc.getModifiedBy());
    assertEquals("ATTACHMENT_CHG-" + image.getId(), latestDoc.getDeltaStr());
  }

  @Test
  public void testRestoreRevisionAsCurrentWithAudioFile() throws IOException {
    // Create structured document and login
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // Add text then save
    doc.getFields().get(0).setFieldData("text");
    StructuredDocument docRev1 = (StructuredDocument) recordMgr.save(doc, piUser);

    // Add audio file to doc and save
    EcatAudio audio = addAudioFileToField(docRev1.getFields().get(0), piUser);
    StructuredDocument docRev2 = (StructuredDocument) recordMgr.save(docRev1, piUser);

    // Update audio file
    updateAudioInGallery(audio.getId(), piUser);

    // Get the history and number of revisions
    List<AuditedRecord> auditedRecords = auditMgr.getHistory(docRev2, null);
    int initialNumberOfRevisions = auditedRecords.size();

    // Restore to revision before audio file was updated
    Number revisionToRestore = auditedRecords.get(initialNumberOfRevisions - 1).getRevision();
    StructuredDocument restored =
        auditMgr.restoreRevisionAsCurrent(revisionToRestore, docRev2.getId());

    int finalNumberOfRevisions = auditMgr.getHistory(restored, null).size();

    // Check revision numbers have increased by 1
    assertEquals(initialNumberOfRevisions + 1, finalNumberOfRevisions);
    // Check if field data contains correct name of first audio file
    String restoredFieldData = restored.getFields().get(0).getFieldData();
    assertTrue(restoredFieldData.contains("mpthreetest"));
  }

  @Test
  public void testRestoreRevisionAsCurrentWithChemElement() throws Exception {
    // Create structured document and login
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // Add text then save
    doc.getFields().get(0).setFieldData("text");
    StructuredDocument docRev1 = (StructuredDocument) recordMgr.save(doc, piUser);

    // Add chemical element to doc and save
    RSChemElement chemElement =
        addChemStructureToField(
            RSpaceTestUtils.getMolString("Amfetamine.mol"), docRev1.getFields().get(0), piUser);
    StructuredDocument docRev2 = (StructuredDocument) recordMgr.save(docRev1, piUser);

    // Update chemical element
    updateExistingChemElement(chemElement.getId(), docRev2.getFields().get(0), piUser);

    // Get the history and number of revisions
    List<AuditedRecord> auditedRecords = auditMgr.getHistory(docRev2, null);
    int initialNumberOfRevisions = auditedRecords.size();

    // Restore to revision before audio file was updated
    Number revisionToRestore = auditedRecords.get(initialNumberOfRevisions - 1).getRevision();
    StructuredDocument restored =
        auditMgr.restoreRevisionAsCurrent(revisionToRestore, docRev2.getId());

    int finalNumberOfRevisions = auditMgr.getHistory(restored, null).size();

    // Check revision numbers have increased by 1
    assertEquals(initialNumberOfRevisions + 1, finalNumberOfRevisions);
    // Check if field data contains correct name of first audio file
    String restoredFieldData = restored.getFields().get(0).getFieldData();
    assertTrue(restoredFieldData.contains("revision=" + revisionToRestore.intValue()));
  }

  @Test
  @Ignore // ignoring as this won't work in current open-source version
  public void testRestoreRevisionAsCurrentWithChemistryFile() throws Exception {
    // Create structured document and login
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // Add text then save
    doc.getFields().get(0).setFieldData("text");
    StructuredDocument docRev1 = (StructuredDocument) recordMgr.save(doc, piUser);

    // Add chemical element to doc and save
    EcatChemistryFile chemistryFile = addChemistryFileToGallery("Amfetamine.mol", piUser);
    RSChemElement chemElement =
        addChemStructureToFieldWithLinkedChemFile(
            chemistryFile, docRev1.getFields().get(0), piUser);
    StructuredDocument docRev2 = (StructuredDocument) recordMgr.save(docRev1, piUser);

    // Update chemical element
    updateChemistryFileInGallery(chemistryFile.getId(), piUser);

    // Get the history and number of revisions
    List<AuditedRecord> auditedRecords = auditMgr.getHistory(docRev2, null);
    int initialNumberOfRevisions = auditedRecords.size();

    // Restore to revision before audio file was updated
    Number revisionToRestore = auditedRecords.get(initialNumberOfRevisions - 1).getRevision();
    StructuredDocument restored =
        auditMgr.restoreRevisionAsCurrent(revisionToRestore, docRev2.getId());

    int finalNumberOfRevisions = auditMgr.getHistory(restored, null).size();

    // Check revision numbers have increased by 1
    assertEquals(initialNumberOfRevisions + 1, finalNumberOfRevisions);
    // Check if field data contains correct name of first audio file
    String restoredFieldData = restored.getFields().get(0).getFieldData();
    assertTrue(restoredFieldData.contains("revision=" + revisionToRestore.intValue()));
  }

  @Test
  public void testRestoreRevisionAsCurrentWithImageFile() throws IOException {
    // Create structured document and login
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // Add text then save
    doc.getFields().get(0).setFieldData("text");
    StructuredDocument docRev1 = (StructuredDocument) recordMgr.save(doc, piUser);

    // Add image file to doc and save
    EcatImage image = addImageToField(docRev1.getFields().get(0), piUser);
    StructuredDocument docRev2 = (StructuredDocument) recordMgr.save(docRev1, piUser);

    // Update image file
    updateImageInGallery(image.getId(), piUser);

    // Get the history and number of revisions
    List<AuditedRecord> auditedRecords = auditMgr.getHistory(docRev2, null);
    int initialNumberOfRevisions = auditedRecords.size();

    // Restore to revision before image file was updated
    Number revisionToRestore = auditedRecords.get(initialNumberOfRevisions - 1).getRevision();
    StructuredDocument restored =
        auditMgr.restoreRevisionAsCurrent(revisionToRestore, docRev2.getId());

    int finalNumberOfRevisions = auditMgr.getHistory(restored, null).size();

    // Check revision numbers have increased by 1
    assertEquals(initialNumberOfRevisions + 1, finalNumberOfRevisions);
    // Check if field data contains correct name of first audio file
    String restoredFieldData = restored.getFields().get(0).getFieldData();
    assertTrue(restoredFieldData.contains("Picture1"));
  }

  @Test
  public void testRestoreRevisionAsCurrentWithDocumentFile()
      throws IOException, URISyntaxException {
    // Create structured document and login
    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    // Add text then save
    doc.getFields().get(0).setFieldData("text");
    StructuredDocument docRev1 = (StructuredDocument) recordMgr.save(doc, piUser);

    // Add file to doc and save
    EcatDocumentFile documentFile = addFileAttachmentToField(docRev1.getFields().get(0), piUser);
    StructuredDocument docRev2 = (StructuredDocument) recordMgr.save(docRev1, piUser);

    // Update doc file
    updateFileAttachmentInGallery(documentFile.getId(), piUser);

    // Get the history and number of revisions
    List<AuditedRecord> auditedRecords = auditMgr.getHistory(docRev2, null);
    int initialNumberOfRevisions = auditedRecords.size();

    // Restore to revision before image file was updated
    Number revisionToRestore = auditedRecords.get(initialNumberOfRevisions - 1).getRevision();
    StructuredDocument restored =
        auditMgr.restoreRevisionAsCurrent(revisionToRestore, docRev2.getId());

    int finalNumberOfRevisions = auditMgr.getHistory(restored, null).size();

    // Check revision numbers have increased by 1
    assertEquals(initialNumberOfRevisions + 1, finalNumberOfRevisions);
    // Check if field data contains correct name of first audio file
    String restoredFieldData = restored.getFields().get(0).getFieldData();
    assertTrue(restoredFieldData.contains("genFilesi"));
  }

  int getNumberOfVisibleDeletedDocumentsForUser(User user) {
    return auditMgr
        .getDeletedDocuments(
            user, "", PaginationCriteria.createDefaultForClass(AuditedRecord.class))
        .getHits();
  }

  private void assertFieldsAreInitialized(AuditedRecord record) {
    for (Field field : record.getRecordAsDocument().getFields()) {
      assertEquals(field.getColumnIndex(), field.getFieldForm().getColumnIndex());
    }
  }

  /*
   * Performs a search operation, like for a folder listing in workspace
   */
  long getTotalSearchHitsInFolder(Folder folder) throws Exception {
    openTransaction();
    long totalhits =
        recordMgr.listFolderRecords(folder.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();

    commitTransaction();
    return totalhits;
  }

  private Number getNthRevisionForDocument(StructuredDocument doc, int n) {
    return auditMgr.getHistory(doc, createDefaultAuditedRecordListPagCrit()).get(n).getRevision();
  }

  private int getRevisionCount(StructuredDocument doc) {
    return auditMgr.getHistory(doc, createDefaultAuditedRecordListPagCrit()).size();
  }

  /**
   * @param doc
   * @param numRevisions the expected total number of revisions
   * @param ar An optional search filter to restrict the search
   * @throws Exception
   */
  void assertNRevisionsForDocument(
      StructuredDocument doc, int numRevisions, RevisionSearchCriteria ar) throws Exception {
    doInTransaction(
        () ->
            assertEquals(
                numRevisions, auditMgr.getNumRevisionsForDocument(doc.getId(), ar).intValue()));
  }
}

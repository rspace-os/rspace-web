package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.model.record.BaseRecord.DEFAULT_VARCHAR_LENGTH;
import static com.researchspace.testutils.RSpaceTestUtils.login;
import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static com.researchspace.testutils.matchers.TotalSearchResults.totalSearchResults;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.SortOrder;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FieldDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EditStatus;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.GalleryFilterCriteria;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.LinkedFieldsToMediaRecordInitPolicy;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.FolderRecordPair;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

/**
 * This test case uses real objects and a rollbacked transaction - it is an acceptance test for the
 * service layer but doesn't actually commit to the DB. A test executes within a single hibernate
 * session.
 */
public class RecordManagerTest extends SpringTransactionalTest {

  private @Autowired RecordDeletionManager deletionManager;
  private @Autowired EcatCommentManager commentManager;
  private @Autowired BaseRecordAdaptable baseRecordAdapter;
  private @Autowired FieldDao fieldDao;

  private RSForm anyForm;
  private User user;
  private PaginationCriteria<BaseRecord> pgCrit =
      PaginationCriteria.createDefaultForClass(BaseRecord.class);

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithExampleContent(user);
    assertTrue(user.isContentInitialized());
    logoutAndLoginAs(user);
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
    super.tearDown();
  }

  @Test
  public void testInitialSetup() throws Exception {
    final int galleryFolders = 8;
    Folder mediaRecord = folderDao.getGalleryFolderForUser(user);
    assertTrue(mediaRecord.isSystemFolder());
    assertEquals(galleryFolders, mediaRecord.getChildren().size());
    assertTrue(mediaRecord.getSubfolders().stream().allMatch(subF -> subF.isSystemFolder()));

    Folder imges = recordMgr.getGallerySubFolderForUser(IMAGES_MEDIA_FLDER_NAME, user);
    Set<BaseRecord> imgGalleryContent = imges.getChildrens();
    assertEquals(2, imgGalleryContent.size()); // 'examples' folder + apifolder only

    Folder examplesGallery =
        (Folder)
            imgGalleryContent.stream()
                .filter(f -> f.getName().equals(MediaUtils.IMAGES_EXAMPLES_MEDIA_FLDER_NAME))
                .findFirst()
                .get();
    assertTrue(examplesGallery.getChildrens().size() > 1);
  }

  @Test
  public void testAllGallerySubfoldersHaveSystemType() {
    Folder galleryRoot = folderDao.getGalleryFolderForUser(user);
    for (BaseRecord child : galleryRoot.getChildrens()) {
      assertTrue(child.hasType(RecordType.SYSTEM));
      if (child.isFolder()) {
        assertTrue(((Folder) child).isSystemFolder());
      }
    }
  }

  @Test
  public void searchResultsBasic() throws Exception {
    final int defPag = IPagination.DEFAULT_RESULTS_PERPAGE;
    Folder root = user.getRootFolder();
    anyForm = formDao.getAll().get(0);
    long numb4InDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    // add page full of folders
    addNFoldersAndMRecords(root, defPag, 4, user, anyForm);
    long totalRecordNumber = defPag + 4 + numb4InDB;
    ISearchResults<BaseRecord> res =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION);
    assertThat(res, totalSearchResults((int) totalRecordNumber));

    assertEquals(IPagination.DEFAULT_RESULTS_PERPAGE, res.getResults().size());
    assertEquals((res.getTotalHits().intValue() / defPag) + 1, res.getTotalPages().intValue());

    // last page should have less elements
    pgCrit.setPageNumber((long) (totalRecordNumber / IPagination.DEFAULT_RESULTS_PERPAGE));
    ISearchResults<BaseRecord> res2 = recordMgr.listFolderRecords(root.getId(), pgCrit, null);
    assertEquals(totalRecordNumber % IPagination.DEFAULT_RESULTS_PERPAGE, res2.getResults().size());
  }

  @Test
  public void searchResultsByRecordTypeFilter() throws Exception {
    Folder root = user.getRootFolder();
    anyForm = formDao.getAll().get(0);
    EnumSet<RecordType> foldersOnly = EnumSet.of(RecordType.FOLDER);
    RecordTypeFilter foldersOnlyFilter = new RecordTypeFilter(foldersOnly, true);

    EnumSet<RecordType> recordsOnly = EnumSet.of(RecordType.NORMAL);
    RecordTypeFilter recordsOnlyFilter = new RecordTypeFilter(recordsOnly, true);

    long numFolders4InDB =
        recordMgr
            .listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION, foldersOnlyFilter)
            .getTotalHits();
    long numRecords4InDB =
        recordMgr
            .listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION, recordsOnlyFilter)
            .getTotalHits();
    final int numFolders = 2;
    final int numRecords = 3;
    addNFoldersAndMRecords(root, numFolders, numRecords, user, anyForm);

    ISearchResults<BaseRecord> res2 =
        recordMgr.listFolderRecords(root.getId(), pgCrit, foldersOnlyFilter);
    assertThat(res2, totalSearchResults((int) (numFolders4InDB + numFolders)));

    ISearchResults<BaseRecord> res3 =
        recordMgr.listFolderRecords(root.getId(), pgCrit, recordsOnlyFilter);
    assertEquals(numRecords4InDB + numRecords, res3.getTotalHits().intValue());
  }

  @Test
  public void searchResultsWithSortCriteria() throws Exception {
    final int n = 10, m = 4;
    Folder root = user.getRootFolder();
    anyForm = formDao.getAll().get(0);
    recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();

    // add 10 folders & 4 docs
    addNFoldersAndMRecords(root, n, m, user, anyForm);
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setResultsPerPage(2);
    assertEquals(2L, recordMgr.listFolderRecords(root.getId(), pgCrit).getHits().longValue());

    pgCrit.setResultsPerPage(20);
    pgCrit.setOrderBy("name");
    pgCrit.setSortOrder(SortOrder.DESC);
    List<BaseRecord> rc = recordMgr.listFolderRecords(root.getId(), pgCrit).getResults();
    assertSortedNameDesc(rc);
    pgCrit.setSortOrder(SortOrder.ASC);
    List<BaseRecord> rc2 = recordMgr.listFolderRecords(root.getId(), pgCrit).getResults();
    assertSortedNameAsc(rc2);

    // use a different form to see if sort working
    anyForm = formDao.getAll().get(0);
    recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    flushDatabaseState();
    pgCrit.setResultsPerPage(20);
    pgCrit.setSortOrder(SortOrder.DESC);
    pgCrit.setOrderBy("template");
    recordMgr.listFolderRecords(root.getId(), pgCrit).getResults();
  }

  @Test
  public void addNewStructuredDocument() throws Exception {
    Folder root = user.getRootFolder();
    flushDatabaseState();
    long numb4InDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    anyForm = formDao.getAll().get(0);
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    assertColumnIndicesAreTheSameForFieldsAndFormss(child);
    assertNotNull(recordMgr.get(child.getId()));
    assertNotNull(child.getId());
    // assertEquals(numb4InDB + 1, root.getChildren().size());
    flushDatabaseState();
    ISearchResults<BaseRecord> results =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION);

    assertEquals(numb4InDB + 1, results.getTotalHits().intValue());
    // check search results are parsed properly into objects
    assertTrue(BaseRecord.class.isAssignableFrom(results.getResults().get(0).getClass()));
  }

  @Test
  public void createRetrieveNewSnippet() {
    final String snippetName = "name2";
    final String snippetContent = "test snippet content for retrieve test";

    flushDatabaseState();

    Snippet child = recordMgr.createSnippet(snippetName, snippetContent, user);
    Long createdId = child.getId();
    Snippet retrievedSnippet = recordMgr.getAsSubclass(createdId, Snippet.class);

    assertEquals(createdId, retrievedSnippet.getId());
    assertEquals(snippetName, retrievedSnippet.getName());
    assertEquals(snippetContent, retrievedSnippet.getContent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void creatingNewSnippetWithEmptyNameThrowsIAE() {
    recordMgr.createSnippet("", "b", user);
  }

  @Test(expected = IllegalArgumentException.class)
  public void creatingNewSnippetWithNullContentThrowsIAE() {
    recordMgr.createSnippet("a", null, user);
  }

  @Test
  public void copyFolder() throws Exception {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    Folder root = folderDao.getRootRecordForUser(user);
    int numberb4 = root.getChildren().size();
    flushDatabaseState();
    long numb4InDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    anyForm = formDao.getAll().get(0);
    Folder cf1 = root.getSubFolderByName(Folder.SHARED_FOLDER_NAME);
    String newName = "newname";
    Folder copied = folderMgr.copy(cf1.getId(), user, newName).getParentCopy();
    flushDatabaseState();
    assertEquals(cf1.getChildren().size(), copied.getChildren().size());
    assertEquals(newName, copied.getName());

    Folder f = folderDao.getRootRecordForUser(user);
    // original + copy
    assertEquals(numberb4 + 1, f.getChildren().size());
    assertEquals(
        numb4InDB + 1,
        recordMgr
            .listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION)
            .getTotalHits()
            .intValue());
  }

  @Test
  public void RSPAC_676CopyStructuredDocumentWithFieldAttachments() throws Exception {
    // set up group with 2 users
    User u1 = createAndSaveRandomUser();
    User u2 = createAndSaveRandomUser();
    User pi = createAndSaveAPi();
    initialiseContentWithEmptyContent(u1, u2, pi);
    Group gp = createGroup("anyGroup", pi);
    addUsersToGroup(pi, gp, u1, u2);
    logoutAndLoginAs(u1);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u1, "text1");
    Field field = doc.getFields().get(0);
    EcatImage image1 = addImageToField(field, u1);
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), field, u1);

    // sanity check u2 can't see image1/attachment yet
    logoutAndLoginAs(u2);

    assertFalse(
        permissionUtils.isPermitted(
            baseRecordAdapter.getAsBaseRecord(image1).get(), PermissionType.READ, u2));
    assertFalse(
        permissionUtils.isPermitted(
            baseRecordAdapter.getAsBaseRecord(doc).get(), PermissionType.READ, u2));

    // now login as u1 and copy and share doc
    logoutAndLoginAs(u1);
    RecordCopyResult result = recordMgr.copy(doc.getId(), "U1copy", u1, u1.getRootFolder().getId());
    StructuredDocument copy = result.getCopy(doc).asStrucDoc();
    shareRecordWithGroup(u1, gp, copy);

    logoutAndLoginAs(u2);
    assertTrue(permissionUtils.isPermitted(copy, PermissionType.READ, u2));
    // reload and assert are viewable
    image1 = (EcatImage) recordDao.get(image1.getId());
    attachment = (EcatDocumentFile) recordDao.get(attachment.getId());
    Set<EcatMediaFile> shared = TransformerUtils.toSet(image1, attachment);
    for (BaseRecord sharedAttachment : shared) {
      Set<BaseRecord> linked = baseRecordAdapter.getAsBaseRecord(sharedAttachment, true);
      boolean isOK = false;
      for (BaseRecord br : linked) {
        if (permissionUtils.isPermitted(br, PermissionType.READ, u2)) {
          isOK = true;
          break;
        }
      }
      assertTrue(isOK);
    }
  }

  @Test
  public void mediaFilesCopiedProperlyWithSnippets() throws IOException {

    // create document being a source for a snippet
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "text1");
    Field field = doc.getFields().get(0);
    EcatImage image = addImageToField(field, user);

    // create snippet
    Snippet snippet = recordMgr.createSnippet("testSnip", field.getFieldData(), user);
    // check record attachment was created
    assertEquals(1, snippet.getLinkedMediaFiles().size());

    // create new document
    StructuredDocument targetDoc = createBasicDocumentInRootFolderWithText(user, "text1");
    Field targetField = targetDoc.getFields().get(0);
    // check record attachments are initially empty
    assertEquals(0, targetField.getLinkedMediaFiles().size());

    // insert snippet
    recordMgr.copySnippetIntoField(snippet.getId(), targetField.getId(), user);

    // check correct field attachments are created
    Field updatedTargetField = fieldDao.get(targetField.getId());
    Set<FieldAttachment> targetLinkedMedia = updatedTargetField.getLinkedMediaFiles();
    assertEquals(1, targetLinkedMedia.size());

    FieldAttachment firstTargetAttachment = (FieldAttachment) targetLinkedMedia.toArray()[0];
    EcatMediaFile firstTargetMediaFile = firstTargetAttachment.getMediaFile();
    assertTrue(
        "retrieved linked media: " + firstTargetMediaFile,
        firstTargetMediaFile instanceof EcatImage);
    assertEquals(image.getId(), ((EcatImage) firstTargetMediaFile).getId());
  }

  @Test
  public void insertSnippetChecksPermissions() throws Exception {

    logoutAndLoginAs(user);

    // create a snippet
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "text1");
    final Field field = doc.getFields().get(0);
    final Snippet snippet = recordMgr.createSnippet("testSnip", field.getFieldData(), user);

    // user can insert their snippet fine
    recordMgr.copySnippetIntoField(snippet.getId(), field.getId(), user);

    // other user
    final User otherUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(otherUser);
    logoutAndLoginAs(otherUser);

    // create other snippet
    StructuredDocument otherDoc = createBasicDocumentInRootFolderWithText(otherUser, "text1");
    final Field otherField = otherDoc.getFields().get(0);
    final Snippet otherSnippet =
        recordMgr.createSnippet("otherTestSnip", otherField.getFieldData(), otherUser);

    // other user can insert their snippet fine
    recordMgr.copySnippetIntoField(otherSnippet.getId(), otherField.getId(), otherUser);

    // other user can't use user's snippet
    assertAuthorisationExceptionThrown(
        () -> recordMgr.copySnippetIntoField(snippet.getId(), otherField.getId(), otherUser));
    // other user can't insert snippet into user's field
    assertAuthorisationExceptionThrown(
        () -> recordMgr.copySnippetIntoField(otherSnippet.getId(), field.getId(), otherUser));
  }

  @Test // RSPAC-1126
  public void copySharedDocument() {

    // set up group with just a pi
    User pi = createAndSaveAPi();
    initialiseContentWithEmptyContent(pi);
    Group gp = createGroup("anyGroup", pi);
    addUsersToGroup(pi, gp);
    logoutAndLoginAs(pi);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi, "text1");
    assertEquals(1, doc.getParents().size());
    assertEquals(pi.getRootFolder().getId(), doc.getParent().getId());

    // share a document with group
    doc = shareRecordWithGroup(pi, gp, doc).getShared().asStrucDoc();
    assertEquals(2, doc.getParents().size());

    // copy the doc and assert it's in the right folder
    RecordCopyResult result = recordMgr.copy(doc.getId(), "PIcopy", pi, null);
    StructuredDocument copy = result.getCopy(doc).asStrucDoc();
    assertEquals(
        "copied record should end up in the same folder", pi.getRootFolder(), copy.getParent());
  }

  @Test
  public void annotationsCopiedProperlyWithSnippets() throws IOException {

    // create document being a source for a snippet
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "text1");
    Field field = doc.getFields().get(0);
    EcatImageAnnotation imageAnnotation = addImageAnnotationToField(field, user);
    EcatImageAnnotation sketch = addSketchToField(field, user);
    assertNotNull(sketch.getAnnotations());

    // create snippet
    Snippet snippet = recordMgr.createSnippet("testSnip", field.getFieldData(), user);
    // check there is single record attachment for image that is annotated
    assertEquals(1, snippet.getLinkedMediaFiles().size());

    // create new document
    StructuredDocument targetDoc = createBasicDocumentInRootFolderWithText(user, "text1");
    Field targetField = targetDoc.getFields().get(0);

    // check no annotations linked yet
    List<EcatImageAnnotation> initialAnnotations =
        imageAnnotationDao.getAllImageAnnotationsFromField(targetField.getId());
    assertEquals(0, initialAnnotations.size());

    // insert snippet
    recordMgr.copySnippetIntoField(snippet.getId(), targetField.getId(), user);

    // check the sketch was also copied fine
    List<EcatImageAnnotation> finalAnnotations =
        imageAnnotationDao.getAllImageAnnotationsFromField(targetField.getId());
    assertEquals(2, finalAnnotations.size());

    EcatImageAnnotation copiedImageAnnotation = finalAnnotations.get(0);
    assertNotEquals(imageAnnotation.getId(), copiedImageAnnotation.getId());
    assertEquals(imageAnnotation.getAnnotations(), copiedImageAnnotation.getAnnotations());

    EcatImageAnnotation copiedSketch = finalAnnotations.get(1);
    assertNotEquals(sketch.getId(), copiedSketch.getId());
    assertEquals(sketch.getAnnotations(), copiedSketch.getAnnotations());

    // check that field attachment is created for image
    Field updatedTargetField = fieldDao.get(targetField.getId());
    Set<FieldAttachment> targetLinkedMedia = updatedTargetField.getLinkedMediaFiles();
    assertEquals(1, targetLinkedMedia.size());
  }

  @Test
  public void elementsDuplicatedOnContentCopy() throws IOException {

    // create content with some elements
    StructuredDocument sourceDoc = createBasicDocumentInRootFolderWithText(user, "text1");
    Field sourceField = sourceDoc.getFields().get(0);
    RSChemElement sourceChem = addChemStructureToField(sourceField, user);
    EcatImageAnnotation sourceSketch = addSketchToField(sourceField, user);
    RSMath sourceMath = addMathToField(sourceField, user);
    String contentForCopy = sourceField.getFieldData();

    // create new document - so target field id is different
    StructuredDocument targetDoc = createBasicDocumentInRootFolderWithText(user, "text2");
    Long targetFieldId = targetDoc.getFields().get(0).getId();

    // copy content
    String copiedContent =
        recordMgr.copyRSpaceContentIntoField(contentForCopy, targetFieldId, user);

    // verify copied content points to new elements, not to old ones
    FieldContents elementsFromCopiedContent = fieldParser.findFieldElementsInContent(copiedContent);
    FieldElementLinkPairs<RSChemElement> chemsInCopiedContent =
        elementsFromCopiedContent.getElements(RSChemElement.class);
    assertEquals(1, chemsInCopiedContent.size());
    assertTrue(chemsInCopiedContent.getElements().get(0).getId() > sourceChem.getId());
    FieldElementLinkPairs<EcatImageAnnotation> sketchesInCopiedContent =
        elementsFromCopiedContent.getSketches();
    assertEquals(1, sketchesInCopiedContent.size());
    assertTrue(sketchesInCopiedContent.getElements().get(0).getId() > sourceSketch.getId());
    FieldElementLinkPairs<RSMath> mathsInCopiedContent =
        elementsFromCopiedContent.getElements(RSMath.class);
    assertEquals(1, mathsInCopiedContent.size());
    assertTrue(mathsInCopiedContent.getElements().get(0).getId() > sourceMath.getId());
  }

  @Test
  public void deleteStructuredDocumentTest() {
    // final int numFields = 7;
    anyForm = formDao.getAll().get(0);
    final int numFields = anyForm.getNumActiveFields();
    Folder root = user.getRootFolder();
    flushDatabaseState();
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);

    assertEquals(
        anyForm.getNumActiveFields(),
        fieldDao.getFieldFromStructuredDocument(child.getId()).size());
    EcatComment commnt = createCommentWithItem();
    commnt.setParentId(child.getId());
    commnt.setRecord(child);
    commentManager.addComment(commnt);
    // mgr.requestRecordEdit(child.getId(), TESTUSER,
    // Collections.EMPTY_SET);
    assertEquals(1, commentManager.getCommentAll(child.getId()).size());

    flushDatabaseState();
    long numAfterInDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    long numFormsB4Delete = formDao.getAll().size();
    folderMgr.setRecordFromFolderDeleted(child.getId(), root.getId(), user);
    flushDatabaseState();
    // search is unaffected, does not find deleted records
    long numAfterDelete =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    assertEquals(1, numAfterInDB - numAfterDelete);

    try {
      // these are not globally flagged as deleted, only from a folder,
      // since a record
      // can belong to multiple folders, if it is shared
      StructuredDocument childDeleted = (StructuredDocument) recordMgr.get(child.getId());
      assertTrue(root.isMarkedDeleted(childDeleted));
      assertFalse(childDeleted.isDeleted());

    } catch (ObjectRetrievalFailureException e) {
      fail(child.getId() + " was actually deleted!");
    }

    // check comments are NOT deleted too
    assertEquals(1, commentManager.getCommentAll(child.getId()).size());
    // check fields NOT deleted too.

    assertEquals(numFields, fieldDao.getFieldFromStructuredDocument(child.getId()).size());

    // check form is NOT deleted
    long numFormsAfterDelete = formDao.getAll().size();
    assertEquals(numFormsB4Delete, numFormsAfterDelete);
  }

  @Test
  public void createDocumentAndListOfMaterials() {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(user, "lom test");
    Field basicField = basicDoc.getFields().get(0);

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(user);
    ApiMaterialUsage subSampleUsage =
        new ApiMaterialUsage(basicSample.getSubSamples().get(0), null);

    // create list of materials through lomManager
    ApiListOfMaterials basicLom =
        createBasicListOfMaterialsForUserAndDocField(user, basicField, List.of(subSampleUsage));

    // retrieve latest doc/field
    Field latestField =
        recordMgr.getRecordWithFields(basicDoc.getId(), user).asStrucDoc().getFields().get(0);
    assertEquals(1, latestField.getListsOfMaterials().size());
    assertEquals("basic list of materials", latestField.getListsOfMaterials().get(0).getName());
    assertEquals(
        "mySubSample",
        latestField
            .getListsOfMaterials()
            .get(0)
            .getMaterials()
            .get(0)
            .getInventoryRecord()
            .getName());

    // update list of materials
    ApiListOfMaterials lomUpdate = new ApiListOfMaterials();
    lomUpdate.setId(basicLom.getId());
    lomUpdate.setName("updated");
    listOfMaterialsApiMgr.updateListOfMaterials(lomUpdate, user);

    // retrieve latest doc/field again
    latestField =
        recordMgr.getRecordWithFields(basicDoc.getId(), user).asStrucDoc().getFields().get(0);
    assertEquals(1, latestField.getListsOfMaterials().size());
    assertEquals("updated", latestField.getListsOfMaterials().get(0).getName());

    // now delete list of materials
    listOfMaterialsApiMgr.deleteListOfMaterials(basicLom.getId(), user);

    // retrieve latest doc/field again
    latestField =
        recordMgr.getRecordWithFields(basicDoc.getId(), user).asStrucDoc().getFields().get(0);
    assertEquals(0, latestField.getListsOfMaterials().size());
  }

  @Test
  public void copyingFolderSkipsRemovedDocumentsAndKeepsOrderRSPAC916()
      throws IOException, IllegalAddChildOperation, DocumentAlreadyEditedException {

    // create folder with 3 documents
    Folder root = folderMgr.getRootFolderForUser(user);
    Folder folder = TestFactory.createAFolder("folder916", user);
    folderMgr.addChild(root.getId(), folder, user);

    StructuredDocument doc1 = createBasicDocumentInFolder(user, folder, "doc1");
    createBasicDocumentInFolder(user, folder, "doc2");
    createBasicDocumentInFolder(user, folder, "doc3");
    createBasicDocumentInFolder(user, folder, "doc4");
    createBasicDocumentInFolder(user, folder, "doc5");
    createBasicDocumentInFolder(user, folder, "doc6");

    // copy the folder
    Folder copied = (Folder) folderMgr.copy(folder.getId(), user, "916copy").getParentCopy();
    ISearchResults<BaseRecord> res =
        recordMgr.listFolderRecords(copied.getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(6, res.getHits().intValue());

    // check the order of new entries match original entries
    assertEquals("doc6", res.getResults().get(0).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc5", res.getResults().get(1).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc4", res.getResults().get(2).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc3", res.getResults().get(3).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc2", res.getResults().get(4).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc1", res.getResults().get(5).asStrucDoc().getFields().get(0).getFieldData());

    // delete one document, copy the folder again
    deletionManager.deleteRecord(folder.getId(), doc1.getId(), user);

    Folder copied2 = (Folder) folderMgr.copy(folder.getId(), user, "916copy2").getParentCopy();
    ISearchResults<BaseRecord> res2 =
        recordMgr.listFolderRecords(copied2.getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(5, res2.getHits().intValue());
    assertEquals("doc6", res.getResults().get(0).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc5", res.getResults().get(1).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc4", res.getResults().get(2).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc3", res.getResults().get(3).asStrucDoc().getFields().get(0).getFieldData());
    assertEquals("doc2", res.getResults().get(4).asStrucDoc().getFields().get(0).getFieldData());
  }

  @Test
  public void recursiveFolderCopy() throws IOException {
    Folder root = folderMgr.getRootFolderForUser(user);

    Folder t1 = TestFactory.createAFolder("level1", user);
    Folder t2 = TestFactory.createAFolder("level2", user);
    Folder t3 = TestFactory.createAFolder("level3", user);

    folderMgr.addChild(root.getId(), t1, user);
    folderMgr.addChild(t1.getId(), t2, user);
    folderMgr.addChild(t2.getId(), t3, user);
    StructuredDocument doc = recordMgr.createBasicDocument(t3.getId(), user);

    // assert the copied record exists in the nested folder
    Folder copied = (Folder) folderMgr.copy(t1.getId(), user, "newname").getParentCopy();
    Folder t3Copy =
        (Folder)
            (((Folder) copied.getChildrens().iterator().next()).getChildrens().iterator().next());
    ISearchResults<BaseRecord> res =
        recordMgr.listFolderRecords(t3Copy.getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(1, res.getHits().intValue());

    // now let's test that content is copied in a recursive folder copy:
    Field txtFld = doc.getTextFields().get(0);
    EcatImageAnnotation ann = addImageAnnotationToField(doc, txtFld, user);

    // do the copy
    Folder copied2 = (Folder) folderMgr.copy(t1.getId(), user, "newname").getParentCopy();
    Folder t3Copy2 =
        (Folder)
            (((Folder) copied2.getChildrens().iterator().next()).getChildrens().iterator().next());
    StructuredDocument copiedDoc =
        (StructuredDocument)
            recordMgr
                .listFolderRecords(t3Copy2.getId(), DEFAULT_RECORD_PAGINATION)
                .getResults()
                .get(0);
    Field txtFldCpy = copiedDoc.getTextFields().get(0);

    // copied document should point to copied annotation, not the original one
    List<EcatImageAnnotation> anns =
        imageAnnotationDao.getAllImageAnnotationsFromField(txtFldCpy.getId());
    assertEquals(1, anns.size());
    assertNotEquals(ann.getId(), anns.get(0).getId());
    assertNotEquals(txtFld.getFieldData(), txtFldCpy.getFieldData());
  }

  private EcatComment createCommentWithItem() {
    EcatComment cm1 = new EcatComment();
    cm1.setComName("image1");
    cm1.setAuthor("someone1");

    EcatCommentItem itm11 = new EcatCommentItem();
    itm11.setItemContent("This is a comment 1 item 1");
    itm11.setLastUpdater("sunny1");
    cm1.addCommentItem(itm11);
    return cm1;
  }

  @Test
  public void addFolder() {
    Folder root = user.getRootFolder();
    int numberb4 = root.getChildren().size();
    Folder child = folderMgr.createNewFolder(root.getId(), "", user);
    assertNotNull(folderMgr.getFolder(child.getId(), user));
    assertNotNull(child.getId());
    // check handles empty names OK
    assertEquals(Folder.DEFAULT_FOLDER_NAME, child.getName());
    assertNotNull(folderMgr.getFolder(child.getId(), user));

    Folder child2 = folderMgr.createNewFolder(root.getId(), "xxx", user);
    assertEquals("xxx", child2.getName());

    assertEquals(numberb4 + 2, root.getChildren().size());

    ISearchResults<BaseRecord> noChildrens =
        recordMgr.listFolderRecords(child.getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(0, noChildrens.getResults().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void addStructuredDocumentArgumentCheckingNoNulls() {
    user.getRootFolder();
    flushDatabaseState();
    recordMgr.createNewStructuredDocument(user.getId(), null, null);
  }

  @Test
  public void createBasicDocumentWithContent() {
    Folder rootF = user.getRootFolder();
    flushDatabaseState();
    StructuredDocument basic =
        recordMgr.createBasicDocumentWithContent(rootF.getId(), "newdoc", user, "<p>Content</p>");
    StructuredDocument persisted = (StructuredDocument) recordMgr.get(basic.getId());
    assertEquals("newdoc", persisted.getName());
    assertEquals("<p>Content</p>", persisted.getFields().get(0).getFieldData());
  }

  @Test
  public void createBasicDocument() {
    Folder rootF = user.getRootFolder();
    flushDatabaseState();
    StructuredDocument basic = recordMgr.createBasicDocument(rootF.getId(), user);
    assertTrue(basic.getFieldCount() == 1);
    assertTrue(basic.isBasicDocument());

    // get persisted
    StructuredDocument persisted = (StructuredDocument) recordMgr.get(basic.getId());
    assertTrue(persisted.getFieldCount() == 1);
    assertTrue(persisted.isBasicDocument());
    // assert ACL acuired from parent...
    assertEquals(persisted.getSharingACL().getString(), rootF.getSharingACL().getString());
    flushDatabaseState();

    // now check we can set a long delta String
    persisted.notifyDelta(DeltaType.FIELD_CHG, CoreTestUtils.getRandomName(2000));
    persisted = (StructuredDocument) recordMgr.save(persisted, user);
  }

  @Test
  public void sysAdminCanViewOtherUsersDocuments() {
    logoutAndLoginAs(user);
    StructuredDocument any = createBasicDocumentInRootFolderWithText(user, "any");
    User sysadmin = logoutAndLoginAsSysAdmin();
    // read, but not edit permission
    assertTrue(permissionUtils.isPermitted(any, PermissionType.READ, sysadmin));
    assertFalse(permissionUtils.isPermitted(any, PermissionType.WRITE, sysadmin));
  }

  @Test
  public void addStructuredDocumentArgumentUnpublishedFormReturnsNull() {
    Folder root = user.getRootFolder();
    flushDatabaseState();
    anyForm = formDao.getAll().stream().filter(form -> !form.isSystemForm()).findFirst().get();

    anyForm.unpublish();
    anyForm = formDao.save(anyForm);
    try {
      assertNull(recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user));
      // tidy up
    } finally {
      anyForm.publish();
      formDao.save(anyForm);
    }
  }

  @Test
  public void addStructuredDocumentIgnoresUnpublishedFormWhenImporting() {
    anyForm = formDao.getAll().stream().filter(form -> !form.isSystemForm()).findFirst().get();
    anyForm.unpublish();
    try {
      formDao.save(anyForm);
      Folder root = user.getRootFolder();
      StructuredDocument child =
          recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
      assertNull(child);
      child =
          recordMgr.createNewStructuredDocument(
              root.getId(),
              anyForm.getId(),
              user,
              new ImportContext(),
              new ImportOverride(Instant.now(), Instant.now(), "someone"));
      assertNotNull(child);
      // restore in all cases, so that future tests work.
    } finally {
      anyForm.publish();
      formDao.save(anyForm);
    }
  }

  @Test
  public void fieldDefaultValueSanitizedWhenCreatingDocFromForm() {

    final String TEST_XSS_CONTENT = "<p>test content with script <script>alert(1)</script></p>";
    final String TEST_CLEAN_CONTENT = "<p>test content with script</p>";

    RSForm form = createAnyForm(user);
    TextFieldForm formField = (TextFieldForm) form.getFieldForms().get(0);
    formField.setDefaultValue(TEST_XSS_CONTENT);
    form.publish();
    form = formMgr.save(form, user);

    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form.getId(), user);
    Field docField = doc.getFields().get(0);

    assertEquals(TEST_CLEAN_CONTENT, docField.getFieldData());
  }

  @Test
  public void addStructuredDocument() {
    flushDatabaseState();
    Folder root = user.getRootFolder();

    int numberb4 = getFolderAndRecordCount(root);

    anyForm = formDao.getAll().get(0);
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);

    Record child2 = recordMgr.get(child.getId());
    assertNotNull(child2);

    assertNotNull(child.getId());
    assertEquals(numberb4 + 1, getFolderAndRecordCount(root));
    flushDatabaseState();

    long numAfterInDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    assertEquals(numberb4 + 1, numAfterInDB);
  }

  private int getFolderAndRecordCount(Folder f) {
    return f.getChildren().size();
  }

  @Test
  public void moveSearchResults() throws Exception {
    User pi = createAndSaveAPi();
    User grpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, grpMember);
    logoutAndLoginAs(grpMember);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(grpMember, "quiop");
    Group g = createGroup("any", pi);
    addUsersToGroup(pi, g, grpMember);
    // now login  as PI:
    logoutAndLoginAs(pi);
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, pi)); // must be true
    // but shouldn't be able to move from group members folder into pis folder
    assertFalse(
        recordMgr
            .move(doc.getId(), pi.getRootFolder().getId(), grpMember.getRootFolder().getId(), pi)
            .isSucceeded());
  }

  @Test
  public void move() throws Exception {
    Folder root = user.getRootFolder();
    anyForm = formDao.getAll().get(0);
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    Folder target = TestFactory.createAFolder("target", user);
    target = folderMgr.save(target, user);
    root.addChild(target, user);
    folderDao.save(target);
    // int numberb4= root.getChildren().size();
    root = folderMgr.save(root, user);
    recordDao.save(child);
    flushDatabaseState();
    long numberb4 =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    // move
    assertTrue(recordMgr.move(child.getId(), target.getId(), root.getId(), user).isSucceeded());
    flushDatabaseState();
    long numbAfter =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    assertEquals(1, numberb4 - numbAfter);

    long numAfterInTarget =
        recordMgr.listFolderRecords(target.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    StructuredDocument child2 = (StructuredDocument) recordMgr.get(child.getId());
    assertTrue(child2.getFolders().contains(target));
    assertFalse(child2.getFolders().contains(root));
    assertEquals(1, numAfterInTarget);
  }

  @Test
  public void cannotMoveEntryOutOfNotebook() throws Exception {
    Folder root = folderMgr.getRootFolderForUser(user);
    Notebook nbook = createNotebookWithNEntries(root.getId(), "nbook", 1, user);
    BaseRecord entry = (BaseRecord) nbook.getChildrens().toArray()[0];

    ServiceOperationResult<BaseRecord> moveResult =
        recordMgr.move(entry.getId(), root.getId(), nbook.getId(), user);
    assertTrue(moveResult.isSucceeded());
  }

  @Test
  public void saveTemporaryDocument() throws Exception {

    Folder root = folderMgr.getRootFolderForUser(user);
    anyForm = formDao.getAll().get(0);
    // set this as example for this tempalte
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);

    Field field = child.getFields().get(0);
    int fieldsb4 = fieldDao.getAll().size();
    int rcordsb4 = recordDao.getAll().size();
    // create new temp doc
    recordMgr.saveTemporaryDocument(field, user, "new data");

    // now load
    StructuredDocument child2 = (StructuredDocument) recordMgr.get(child.getId());

    // it has a temp field associated with it
    assertNotNull(child2.getTempRecord());
    // temp record has current user as an owner
    assertEquals(user, child2.getTempRecord().getOwner());
    // autosave should not increment version

    Field temp = child2.getFields().get(0).getTempField();
    assertNotNull(temp);
    assertNull(temp.getStructuredDocument()); // not linked to original
    assertNull(temp.getTempField()); // no temp field for a temp field.
    // the temp record doesn't have a parent
    assertNull(child2.getTempRecord().getParent());
    int fieldsafter = fieldDao.getAll().size();
    assertEquals(fieldsafter, fieldsb4 + 1);
    int rcordsafter = recordDao.getAll().size();
    assertEquals(rcordsafter, rcordsb4 + 1);
  }

  @Test
  public void cancelStructuredDocument() throws Exception {
    Folder root = folderMgr.getRootFolderForUser(user);
    anyForm = formDao.getAll().get(0);
    // set this as example for this tempalte
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    child.addType(RecordType.NORMAL);
    recordMgr.save(child, user);
    int fieldsb4 = fieldDao.getAll().size();

    Version b4 = child.getUserVersion();

    Field field = child.getFields().get(0);
    // create new temp doc ( e.g., from autosave)
    recordMgr.saveTemporaryDocument(field, user, "new data");
    // and now make sure we can edit
    recordMgr.requestRecordEdit(child.getId(), user, anySessionTracker());

    BaseRecord container =
        recordMgr.cancelStructuredDocumentAutosavedEdits(child.getId(), user.getUsername());
    assertEquals(root, container);

    // check temp fields removed
    int fieldsafter = fieldDao.getAll().size();
    assertEquals(fieldsafter, fieldsb4);

    field = child.getFields().get(0);
    assertFalse(field.getData().contains("new data"));

    StructuredDocument child2 = (StructuredDocument) recordMgr.get(child.getId());
    logoutAndLoginAs(user);
    checkPermissions(child2);

    // check version not incremented
    // and temp fields removed
    assertNull(child2.getTempRecord());
    assertNull(child2.getFields().get(0).getTempField());
    assertEquals(b4, child2.getUserVersion());
  }

  @Test
  public void saveStructuredDocumentOnlyIncrementsVersionOncePerSave() {
    Folder root = folderMgr.getRootFolderForUser(user);
    anyForm = formDao.getAll().get(0);
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    child.addType(RecordType.NORMAL);
    recordMgr.save(child, user);
    // will be v1
    StructuredDocument child2 = (StructuredDocument) recordMgr.get(child.getId());
    child2.setName("new1");
    child2.setName("new2");
    // multiple edits
    recordMgr.save(child2, user);

    StructuredDocument child3 = (StructuredDocument) recordMgr.get(child.getId());
    assertEquals(new Version(2L), child3.getUserVersion());
  }

  @Test(expected = IllegalArgumentException.class)
  public void renameThrowsIAEIfInvalidName() {
    recordMgr.renameRecord(" ", 1L, user);
  }

  @Test
  public void rename() throws Exception {
    anyForm = formDao.getAll().get(0);

    final Record doc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), anyForm.getId(), user);

    // same as original name, no effect.
    assertTrue(recordMgr.renameRecord(doc.getName(), doc.getId(), user));

    // can't rename system Folder
    Folder f = folderDao.getCollaborationGroupsSharedFolderForUser(user);
    String origName = f.getName();
    assertTrue(f.hasType(RecordType.SYSTEM)); // sanity check
    assertFalse(recordMgr.renameRecord("XXX", f.getId(), user));
    // and check has not been renamed
    Folder f2 = folderDao.getCollaborationGroupsSharedFolderForUser(user);
    assertEquals(origName, f2.getName());

    // owner can rename.
    login(user.getUsername(), TESTPASSWD);
    recordMgr.renameRecord("newname23", doc.getId(), user);
    assertEquals("newname23", recordMgr.get(doc.getId()).getName());

    final User other = createAndSaveUserIfNotExists("OTHERU");
    logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    // other user doesn't have permission to rename 'users' record
    assertAuthorisationExceptionThrown(
        () -> recordMgr.renameRecord("newnameOTHER", doc.getId(), other));

    // new names are abbreviated
    assertTrue(
        recordMgr.renameRecord(getRandomName(DEFAULT_VARCHAR_LENGTH + 10), doc.getId(), user));

    StructuredDocument doc2 = recordMgr.get(doc.getId()).asStrucDoc();
    String newName = doc2.getName();
    assertEquals(DEFAULT_VARCHAR_LENGTH, newName.length());
    // newlines removed
    assertTrue(recordMgr.renameRecord("a\nb", doc.getId(), user));
    newName = "a b";
    assertEquals(newName, recordMgr.get(doc.getId()).getName());

    // rename fails if doc is signed: RSPAC-642
    doc2.setSigned(true);
    recordMgr.save(doc2, user);
    assertFalse(recordMgr.renameRecord("newnameFailsAsIsSigned", doc2.getId(), user));
    assertEquals(
        "Doc should not be renamed, but was ", newName, recordMgr.get(doc2.getId()).getName());
  }

  @Test
  public void accessPolicy() throws Exception {
    anyForm = formDao.getAll().get(0);

    Record record =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), anyForm.getId(), user);
    final Long unknownId = 1234567L;
    // assert that the error is returned if the record doesn't exist,
    assertEquals(
        EditStatus.ACCESS_DENIED,
        recordMgr.requestRecordEdit(unknownId, user, anySessionTracker()));
    // owner can edit
    assertEquals(
        EditStatus.EDIT_MODE,
        recordMgr.requestRecordEdit(record.getId(), user, anySessionTracker()));

    // but can't access if record is deleted
    deletionManager.deleteRecord(record.getParent().getId(), record.getId(), user);
    assertEquals(
        EditStatus.ACCESS_DENIED,
        recordMgr.requestRecordEdit(record.getId(), user, anySessionTracker()));
  }

  @Test
  public void fieldOrder() throws Exception {
    Folder root = folderMgr.getRootFolderForUser(user);
    List<RSForm> forms = formDao.getAllVisibleNormalForms();
    for (RSForm form : forms) {
      if (form.getName().equals("Experiment")) {
        StructuredDocument child =
            recordMgr.createNewStructuredDocument(root.getId(), form.getId(), user);
        recordMgr.save(child, user);
        StructuredDocument child2 = (StructuredDocument) recordMgr.get(child.getId());
        for (int i = 0; i < child2.getFieldCount(); i++) {
          assertEquals(i, child2.getFields().get(i).getFieldForm().getColumnIndex());
        }
      }
    }
  }

  @Test
  public void saveStructuredDocument() throws Exception {
    Folder root = folderMgr.getRootFolderForUser(user);
    anyForm = formDao.getAll().get(0);
    // set this as example for this template
    final StructuredDocument doc =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    doc.addType(RecordType.NORMAL);
    recordMgr.save(doc, user);
    Long docId = doc.getId();
    int fieldsb4 = fieldDao.getAll().size();

    Version b4 = doc.getUserVersion();
    ErrorList warnings = new ErrorList();

    Field field = doc.getFields().get(0);
    // create new temp doc (e.g. from autosave)
    recordMgr.saveTemporaryDocument(field, user, "new data");
    // and now make sure we can edit
    recordMgr.requestRecordEdit(docId, user, anySessionTracker());
    FolderRecordPair savedPair =
        recordMgr.saveStructuredDocument(docId, user.getUsername(), true, warnings);
    assertEquals(root, savedPair.getParent());
    assertFalse(warnings.hasErrorMessages());
    // check temp fields removed
    int fieldsafter = fieldDao.getAll().size();
    assertEquals(fieldsafter, fieldsb4);

    StructuredDocument child2 = (StructuredDocument) recordMgr.get(docId);
    logoutAndLoginAs(user);
    checkPermissions(child2);
    // check version incremented and temp fields removed
    assertNull(child2.getTempRecord());
    assertNull(child2.getFields().get(0).getTempField());
    assertEquals(b4.increment(), child2.getUserVersion());

    // try another save - without temporary document being created
    recordMgr.requestRecordEdit(docId, user, anySessionTracker());
    recordMgr.saveStructuredDocument(docId, user.getUsername(), false, warnings);
    assertTrue(warnings.hasErrorMessages());
    assertEquals(1, warnings.getErrorMessages().size());
    assertEquals("content.not.changed", warnings.getErrorMessages().get(0));

    // try another save - with temporary field being created, but content unchanged
    recordMgr.saveTemporaryDocument(child2.getFields().get(0), user, "new data");
    recordMgr.saveStructuredDocument(docId, user.getUsername(), true, warnings);
    assertEquals(2, warnings.getErrorMessages().size());
    assertEquals("content.not.changed", warnings.getErrorMessages().get(1));

    // check requires authorisation
    final User other = createAndSaveUserIfNotExists("OTHERU");
    logoutAndLoginAs(other);
    assertAuthorisationExceptionThrown(
        () -> recordMgr.saveStructuredDocument(docId, other.getUsername(), true, null));
  }

  @Test
  @SuppressWarnings("unused")
  public void folderMgerCopySetsOwners() throws Exception {
    Folder root = user.getRootFolder();
    Set<BaseRecord> children = root.getChildrens();
    for (BaseRecord subbF : children) {
      if (subbF.isFolder()
          && subbF.getName().equals(ContentInitializerForDevRunManager.OTHER_DATA_FOLDER_NAME)) {
        Folder subbF2 = folderMgr.getFolder(subbF.getId(), user);
        // int numFoldersOrig = subbF2.getChildrens().size();
        Folder copy = folderMgr.copy(subbF.getId(), user, "newname").getParentCopy();
        // assertEquals(numFoldersOrig, copy.getChildren().size());

        for (RecordToFolder r : copy.getChildren()) {
          assertNotNull(r.getRecord().getOwner());
          assertEquals(user, r.getRecord().getOwner());
        }
      }
    }
  }

  private void checkPermissions(StructuredDocument child2) {

    boolean ispermittedRead =
        permissionUtils.isPermitted(child2, PermissionType.READ, child2.getOwner());
    assertTrue(ispermittedRead);

    boolean ispermittedEdit =
        permissionUtils.isPermitted(child2, PermissionType.WRITE, child2.getOwner());
    assertTrue(ispermittedEdit);

    boolean ispermittedDelete =
        permissionUtils.isPermitted(child2, PermissionType.DELETE, child2.getOwner());
    assertTrue(ispermittedDelete);
  }

  @Test
  public void forceRefresh() throws Exception {
    Folder root = folderMgr.getRootFolderForUser(user);
    anyForm = formDao.getAll().get(0);
    // set this as example for this template
    StructuredDocument child =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    final Version originalVersion = child.getUserVersion();
    // can be any delta type
    recordMgr.forceVersionUpdate(child.getId(), DeltaType.COMMENT, " a message", user);
    StructuredDocument updated = (StructuredDocument) recordMgr.get(child.getId());
    // check version is incremented
    assertTrue(updated.getUserVersion().compareTo(originalVersion) > 0);
  }

  @Test
  public void getParentFolderOfOwner() throws Exception {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "any");
    assertEquals(user.getRootFolder(), recordMgr.getParentFolderOfRecordOwner(sd.getId(), user));

    // now add another parent folder belonging to another user.
    User other = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    initialiseContentWithEmptyContent(other);
    other.getRootFolder().addChild(sd, other);
    assertEquals(2, sd.getParentFolders().size());
    // but owner folder still returned.
    assertEquals(user.getRootFolder(), recordMgr.getParentFolderOfRecordOwner(sd.getId(), user));
  }

  @Test
  public void getAll() throws Exception {
    Record r1 = createBasicDocumentInRootFolderWithText(user, "r1");
    Record r2 = createBasicDocumentInRootFolderWithText(user, "r2");
    List<RSpaceDocView> hits = recordMgr.getAllFrom(toSet(r1.getId(), r2.getId()));
    assertEquals(2, hits.size());
    // check no hits handled OK
    List<RSpaceDocView> hits2 = recordMgr.getAllFrom(toSet(-123456l)); // unknown
    assertEquals(0, hits2.size());
    // no search terms?

    assertExceptionThrown(
        () -> recordMgr.getAllFrom(Collections.EMPTY_SET), IllegalArgumentException.class);

    // folders are returned as well!
    Folder f1 = createFolder("any2", user.getRootFolder(), user);
    List<RSpaceDocView> hits4 = recordMgr.getAllFrom(toSet(f1.getId(), r1.getId(), r2.getId()));
    assertEquals(3, hits4.size());
  }

  @Test
  public void ignorPermissionsInLazyLoadProperties() throws Exception {
    final User newUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithEmptyContent(newUser);
    logoutAndLoginAs(newUser);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(newUser, "text");
    logoutAndLoginAs(user);
    // allows unauthorised access
    final StructuredDocument anyDoc2 =
        recordMgr
            .getRecordWithLazyLoadedProperties(
                anyDoc.getId(), newUser, new LinkedFieldsToMediaRecordInitPolicy(), true)
            .asStrucDoc();
    assertNotNull(anyDoc);

    // now we do check permissions, so this should throw AuthException as wrong user is logged in in
    assertAuthorisationExceptionThrown(
        () ->
            recordMgr
                .getRecordWithLazyLoadedProperties(
                    anyDoc2.getId(), user, new LinkedFieldsToMediaRecordInitPolicy(), false)
                .asStrucDoc());
  }

  @Test
  public void createFromTemplateHappyCase() throws Exception {
    User newUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithEmptyContent(newUser);
    logoutAndLoginAs(newUser);

    // create document
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(newUser, "text");

    // create template from document
    final int EXPECTED_FORMS = formDao.getAll().size();
    StructuredDocument template1 =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc1.getId(), newUser);
    assertTrue(template1.hasType(RecordType.TEMPLATE));
    assertFalse(template1.isInvisible());
    assertEquals(doc1.getForm(), template1.getForm());
    assertEquals(EXPECTED_FORMS, formDao.getAll().size());

    // create new document from template
    Long targetFolderId = newUser.getRootFolder().getId();
    String newName = "fromTemplate";
    RecordCopyResult fromTemplateResult =
        recordMgr.createFromTemplate(template1.getId(), newName, newUser, targetFolderId);
    StructuredDocument doc2FromTemplate1 = (StructuredDocument) fromTemplateResult.getUniqueCopy();
    assertTrue(doc2FromTemplate1.getDeltaStr().contains(template1.getGlobalIdentifier()));
    assertTrue(doc2FromTemplate1.getDeltaStr().contains(DeltaType.CREATED_FROM_TEMPLATE.name()));
    assertTrue(doc2FromTemplate1.hasType(RecordType.NORMAL));
    assertFalse(doc2FromTemplate1.hasType(RecordType.TEMPLATE));
    assertEquals(newName, doc2FromTemplate1.getName());
    assertEquals(EXPECTED_FORMS, formDao.getAll().size());
    assertEquals(doc1.getForm(), doc2FromTemplate1.getForm());
    assertEquals(1, doc2FromTemplate1.getParentFolders().size());
    assertEquals(targetFolderId, doc2FromTemplate1.getParent().getId());
    assertEquals(template1, doc2FromTemplate1.getTemplate());

    // create new template from  from doc2FromTemplate RSPAC-163
    StructuredDocument template2 =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc2FromTemplate1.getId(), newUser);
    // this is not null. Allows reconstruction of provenance of templates
    assertEquals(template1, template2.getTemplate());

    //  create new document from template2
    StructuredDocument doc3FromTemplate2 =
        recordMgr
            .createFromTemplate(template2.getId(), newName, newUser, targetFolderId)
            .getUniqueCopy()
            .asStrucDoc();
    // check that document references the template it is immediately created from, not the original
    // template.
    assertEquals(template2, doc3FromTemplate2.getTemplate());
  }

  @Test
  public void filterGalleryItems() throws IOException {
    // add 3 images with defined name to Gallery
    final int TOTAL_NUM_IMAGES = 3;
    final int IMAGES_PER_PAGE = 2;

    EcatImage[] images = new EcatImage[TOTAL_NUM_IMAGES];
    for (int i = 0; i < TOTAL_NUM_IMAGES; i++) {
      images[i] = addImageToGallery(user);
      recordMgr.renameRecord(i + "_name", images[i].getId(), user);
    }
    pgCrit.setResultsPerPage(IMAGES_PER_PAGE); // will get 2 pages
    ISearchResults<BaseRecord> results = listGallery(images, null, user);
    assertEquals("should get paginated list of results", IMAGES_PER_PAGE, results.getHitsPerPage());

    // now search by name:
    GalleryFilterCriteria filter = new GalleryFilterCriteria("0_name");
    results = listGallery(images, filter, user);
    assertEquals("should get single search hit", 1, results.getResults().size());
    assertEquals("should match with image of same name", images[0], results.getFirstResult());

    filter = new GalleryFilterCriteria("0_na");
    results = listGallery(images, filter, user);
    assertEquals("should get single search hit for partial term", 1, results.getResults().size());
    assertEquals("should match with image of same name", images[0], results.getFirstResult());

    // search by gallery global Id
    filter = new GalleryFilterCriteria(images[0].getGlobalIdentifier());
    results = listGallery(images, filter, user);
    assertEquals("should get single search hit for global Id", 1, results.getResults().size());

    // search by non-gallery global id fails gracefully
    filter = new GalleryFilterCriteria("SD12345");
    results = listGallery(images, filter, user);
    assertEquals("should get no hits", 0, results.getResults().size());

    //		// now add tiff file to gallery and check uploaded OK
    // for unknown reason this works in application but not intest....
    //		addTiffImageToGallery(user);
    //		assertEquals(TOTAL_NUM_IMAGES + 1, listGallery(images, null).getTotalHits().intValue());
  }

  protected void assertSortedNameDesc(List<BaseRecord> rc) {
    for (int i = 0; i < rc.size() - 1; i++) {
      assertTrue(rc.get(i).getName().compareTo(rc.get(i + 1).getName()) >= 0);
    }
  }

  protected void assertSortedNameAsc(List<BaseRecord> rc) {
    for (int i = 0; i < rc.size() - 1; i++) {
      assertTrue(rc.get(i).getName().compareTo(rc.get(i + 1).getName()) <= 0);
    }
  }

  private ISearchResults<BaseRecord> listGallery(
      EcatImage[] images, GalleryFilterCriteria filter, User user) {
    return recordMgr.getGalleryItems(
        images[0].getParent().getId(), pgCrit, filter, RecordTypeFilter.GALLERY_FILTER, user);
  }

  @Test
  public void editStatusForOpenedAndEditedDocs() {

    User pi = createAndSaveAPi();
    User grpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, grpMember);
    Group g = createGroup("any", pi);
    addUsersToGroup(pi, g, grpMember);

    logoutAndLoginAs(grpMember);

    // pi will be able to see the status of unshared doc
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(grpMember, "text3");
    UserSessionTracker users = anySessionTracker();

    EditStatus memberViewStatus = recordMgr.requestRecordView(doc.getId(), grpMember, users);
    assertEquals(EditStatus.VIEW_MODE, memberViewStatus);
    EditStatus piViewStatus = recordMgr.requestRecordView(doc.getId(), pi, users);
    assertEquals(EditStatus.VIEW_MODE, piViewStatus);

    // user starts editing
    EditStatus memberEditStatus = recordMgr.requestRecordEdit(doc.getId(), grpMember, users);
    assertEquals(EditStatus.EDIT_MODE, memberEditStatus);
    // they can see the document being edited by themselves
    EditStatus memberViewStatus2 = recordMgr.requestRecordView(doc.getId(), grpMember, users);
    assertEquals(EditStatus.EDIT_MODE, memberViewStatus2);
    // pi sees document as edited
    EditStatus piEditStatus = recordMgr.requestRecordView(doc.getId(), pi, users);
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, piEditStatus);
  }

  @Test
  public void adminUserCantSeeDeletedDocs_RSPAC1285()
      throws AuthorizationException, DocumentAlreadyEditedException {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(any, "text3");
    recordDeletionMgr.deleteRecord(doc.getParent().getId(), doc.getId(), any);
    UserSessionTracker otherUsers = anySessionTracker();
    assertEquals(
        EditStatus.ACCESS_DENIED, recordMgr.requestRecordView(doc.getId(), any, otherUsers));
    User sysadmin = logoutAndLoginAsSysAdmin();
    assertEquals(
        EditStatus.ACCESS_DENIED, recordMgr.requestRecordView(doc.getId(), sysadmin, otherUsers));
  }

  @Test
  public void filteringMediaFiles() throws IOException {

    User newUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithEmptyContent(newUser);
    logoutAndLoginAs(newUser);

    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setMediaFilesFilter(true);
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);

    ISearchResults<BaseRecord> mediaFiles = recordMgr.getFilteredRecords(filters, pgCrit, newUser);
    assertEquals(0, mediaFiles.getHits().intValue());

    addImageToGallery(newUser);
    mediaFiles = recordMgr.getFilteredRecords(filters, pgCrit, newUser);
    assertEquals(1, mediaFiles.getHits().intValue());
  }

  @Test
  public void transitiveSharingNotListedinWorkspace_RSPAC1122() {
    TestGroup mainGrp = createTestGroup(3);
    TestGroup otherGrp = createTestGroup(2);
    // main-u1 is in both groups
    Group other =
        grpMgr.addUserToGroup(
            mainGrp.getUserByPrefix("u1").getUsername(),
            otherGrp.getGroup().getId(),
            RoleInGroup.DEFAULT);
    // login as another user in otherGroup and share a docu with group ( and hence main-u1)
    User inOtherGrp = otherGrp.getUserByPrefix("u2");
    logoutAndLoginAs(inOtherGrp);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(inOtherGrp, "any");
    shareRecordWithGroup(inOtherGrp, other, doc);

    // now login as mainGrpPI and list documents shouldn't see doc
    logoutAndLoginAs(mainGrp.getPi());
    WorkspaceFilters filteres = new WorkspaceFilters();
    filteres.setViewableItemsFilter(true);
    List<BaseRecord> hits = recordMgr.getFilteredRecordsList(filteres, mainGrp.getPi());
    assertFalse(hits.stream().anyMatch(br -> br.getId().equals(doc.getId())));
  }

  // rspac-2084
  @Test
  public void viewAllFilterShouldNotEnableListingsOfOtherPisInGroup() {
    User piOfGroupUser = createAndSaveAPi();
    User otherPiGroupMember = createAndSaveAPi();
    User otherGrpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(piOfGroupUser, otherGrpMember, otherPiGroupMember);
    User admin = logoutAndLoginAsSysAdmin();
    Group labGroup =
        createGroupForUsers(
            admin,
            piOfGroupUser.getUsername(),
            otherPiGroupMember.getUsername(),
            piOfGroupUser,
            otherPiGroupMember,
            otherGrpMember);

    // create 1 doc each.
    logoutAndLoginAs(piOfGroupUser);
    createBasicDocumentInRootFolderWithText(piOfGroupUser, "created by pi");
    logoutAndLoginAs(otherPiGroupMember);
    createBasicDocumentInRootFolderWithText(otherPiGroupMember, "created by other  pi");
    logoutAndLoginAs(otherGrpMember);
    createBasicDocumentInRootFolderWithText(otherGrpMember, "created by group member");

    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setViewableItemsFilter(true);
    List<BaseRecord> hitsSeenByPi = recordMgr.getFilteredRecordsList(filters, piOfGroupUser);
    // should not be able to see any records belonging to other PI, but can see other user
    assertEquals(2, hitsSeenByPi.size());
    assertTrue(hitsSeenByPi.stream().noneMatch(br -> br.getOwner().equals(otherPiGroupMember)));

    // other group member just sees their own documents
    logoutAndLoginAs(otherGrpMember);
    List<BaseRecord> hitsSeenByOtherMember =
        recordMgr.getFilteredRecordsList(filters, otherGrpMember);
    assertEquals(1, hitsSeenByOtherMember.size());
    // should not be able to see any records belonging to anyone else
    assertTrue(hitsSeenByOtherMember.get(0).getOwner().equals(otherGrpMember));

    // the pi can see the same number of otherMember's documents as otherMember himself can
    long hitsSize = hitsSeenByOtherMember.size();
    assertEquals(
        hitsSize, hitsSeenByPi.stream().filter(br -> br.getOwner().equals(otherGrpMember)).count());
  }

  @Test
  public void getSafeNull() {
    assertFalse(recordMgr.getSafeNull(Long.MIN_VALUE).isPresent());
  }

  @Test
  public void exists() {
    assertFalse(recordMgr.exists(Long.MIN_VALUE));
  }

  @Test
  public void getAuthorisedRecordsById() throws IOException {
    User any = createInitAndLoginAnyUser();
    StructuredDocument basic1 = recordMgr.createBasicDocument(any.getRootFolder().getId(), any);
    EcatMediaFile emf = addImageToGallery(any);
    List<Long> anyUserIds = TransformerUtils.transform(toList(basic1, emf), r -> r.getId());
    List retrieved = recordMgr.getAuthorisedRecordsById(anyUserIds, any, PermissionType.READ);
    assertEquals(2, retrieved.size());
    assertTrue(retrieved.contains(basic1));
    assertTrue(retrieved.contains(emf));

    // empty list handled
    assertTrue(
        recordMgr
            .getAuthorisedRecordsById(Collections.emptyList(), any, PermissionType.READ)
            .isEmpty());

    // unauthorised not included
    logoutAndLoginAs(user);
    assertTrue(recordMgr.getAuthorisedRecordsById(anyUserIds, user, PermissionType.READ).isEmpty());
  }

  @Test
  public void testCreateNewStructuredDocumentIntoSharedFolder() throws Exception {
    User admin = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    Group group = new Group(CoreTestUtils.getRandomName(10), admin);
    group.addMember(user, RoleInGroup.DEFAULT);
    group = grpMgr.saveGroup(group, admin);
    initialiseContentWithEmptyContent(user, admin);
    grpMgr.createSharedCommunalGroupFolders(group.getId(), admin.getUsername());

    Long sharedFolderId = group.getCommunalGroupFolderId();
    Folder rootFolder = user.getRootFolder();

    flushDatabaseState();

    long initialCountRootFolder =
        recordMgr.listFolderRecords(rootFolder.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    anyForm = formDao.getAll().get(0);
    StructuredDocument newCreatedDocument =
        recordMgr.createNewStructuredDocument(sharedFolderId, anyForm.getId(), user);
    assertColumnIndicesAreTheSameForFieldsAndFormss(newCreatedDocument);
    assertNotNull(recordMgr.get(newCreatedDocument.getId()));
    assertNotNull(newCreatedDocument.getId());
    assertEquals(initialCountRootFolder + 1, rootFolder.getChildren().size());
  }
}

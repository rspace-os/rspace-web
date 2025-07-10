package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.Invokable;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.RenameAuditEvent;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.MessagedServiceOperationResult;
import com.researchspace.service.AuditManager;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.struts.mock.MockPrincipal;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.web.multipart.MultipartFile;

public class StructuredDocumentControllerTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  @Mock private UserManager userMgr;
  @Mock private RecordManager recordMgr;
  @Mock private SharingHandler recordShareHandler;
  @Mock private DocumentTagManager documentTagManager;
  @Mock private EcatCommentManager commentMgr;
  @Mock private BaseRecordManager baseRecordMgr;
  @Mock private BreadcrumbGenerator brgen;
  @Mock private MediaManager mediaMgr;
  @Mock private AuditTrailService auditTrail;
  @Mock private AuditManager auditMgr;
  @Mock private IPermissionUtils permissionUtils;
  @Mock private FieldManager fieldManager;
  @Mock BaseRecordAdaptable recordAdapter;
  @Mock ExternalFileImporter fileimporter;
  @Mock FolderManager fMgr;
  @Mock IControllerInputValidator validator;
  private StructuredDocumentController strucDocCtrller;

  private Principal mockPrincipal;
  private User user;
  private StaticMessageSource messageSource;
  private MockServletContext context;
  private MockHttpSession session;

  @Before
  public void setUp() {
    messageSource = new StaticMessageSource();
    session = new MockHttpSession();
    strucDocCtrller = new StructuredDocumentController();
    context = new MockServletContext();
    strucDocCtrller.setServletContext(context);
    strucDocCtrller.setUserManager(userMgr);
    strucDocCtrller.setRecordManager(recordMgr);
    strucDocCtrller.setMediaManager(mediaMgr);
    strucDocCtrller.setMessageSource(new MessageSourceUtils(messageSource));
    strucDocCtrller.setServletContext(new MockServletContext());
    strucDocCtrller.setAuditService(auditTrail);
    strucDocCtrller.setAuditManager(auditMgr);
    strucDocCtrller.setBaseRecordMgr(baseRecordMgr);
    strucDocCtrller.setPermissionUtils(permissionUtils);
    strucDocCtrller.setFieldManager(fieldManager);
    strucDocCtrller.setRecordAdapter(recordAdapter);
    strucDocCtrller.setCommentManager(commentMgr);
    strucDocCtrller.setExternalWordFileImporter(fileimporter);
    strucDocCtrller.setFolderManager(fMgr);
    strucDocCtrller.setdocumentTagManager(documentTagManager);
    strucDocCtrller.setInputValidator(validator);
    strucDocCtrller.setRecordShareHandler(recordShareHandler);

    user = TestFactory.createAnyUser("user");
    mockPrincipal = new MockPrincipal(user.getUsername());
  }

  @After
  public void tearDown() {}

  @Test
  public void testRenameErrorWithAnEmptyName() {
    final StructuredDocument sd = setUpStructuredDocument();

    // check error generated for an empty name.
    AjaxReturnObject<String> rc2 = strucDocCtrller.rename(sd.getId(), "", mockPrincipal);
    // indicates failure
    assertNull(rc2.getData());
    assertNotNull(rc2.getErrorMsg());
    verifyNoInteractions(recordMgr);
    verifyNoInteractions(auditTrail);
  }

  @Test
  public void testRenameErrorWithIUllegalCharacters() {
    final StructuredDocument sd = setUpStructuredDocument();

    // check error generated for a name with a '/'. RSPAC-295
    AjaxReturnObject<String> rc3 =
        strucDocCtrller.rename(sd.getId(), "some / slash", mockPrincipal);
    // indicates failure
    assertNull(rc3.getData());
    assertNotNull(rc3.getErrorMsg());
    verifyNoInteractions(recordMgr);
    verifyNoInteractions(auditTrail);
  }

  @Test
  public void testRenameSuccess() {
    final StructuredDocument sd = setUpStructuredDocument();
    RenameAuditEvent renameAuditEvent = new RenameAuditEvent(user, sd, "oldname", "newname");

    when(userMgr.getUserByUsername(eq(user.getUsername()))).thenReturn(user);
    when(baseRecordMgr.get(anyLong(), eq(user))).thenReturn(sd);
    when(recordMgr.renameRecord("newname", sd.getId(), user)).thenReturn(true);

    AjaxReturnObject<String> rc = strucDocCtrller.rename(sd.getId(), "newname", mockPrincipal);
    // indicates success
    assertNotNull(rc.getData());
    verify(auditTrail).notify(eq(renameAuditEvent));
    assertEquals("from: \"oldname\" to: \"newname\"", renameAuditEvent.getDescription());
  }

  @Test
  public void testRenameDoesNothingWhenDatabaseIsNotUpdated() {
    final StructuredDocument sd = setUpStructuredDocument();
    // now simulate failure
    when(userMgr.getUserByUsername(eq(user.getUsername()))).thenReturn(user);
    when(baseRecordMgr.get(anyLong(), eq(user))).thenReturn(sd);
    when(recordMgr.renameRecord("newname", sd.getId(), user)).thenReturn(false);
    AjaxReturnObject<String> rc = strucDocCtrller.rename(sd.getId(), "newname", mockPrincipal);
    // indicates success
    assertNotNull(rc.getErrorMsg());
    verifyNoInteractions(auditTrail);
  }

  @NotNull
  private StructuredDocument setUpStructuredDocument() {
    final StructuredDocument sd = TestFactory.createAnySD();
    sd.setId(3L);
    messageSource.addMessage("errors.required", Locale.getDefault(), "any");
    messageSource.addMessage("errors.invalidchars", Locale.getDefault(), "any");
    messageSource.addMessage("rename.failed.msg", Locale.getDefault(), "any");
    return sd;
  }

  @Test
  public void testTagRecord() {
    final Long recordId = 6L;
    String tagText = "test&&Test";
    when(userMgr.getUserByUsername(eq(user.getUsername()))).thenReturn(user);
    when(documentTagManager.saveTag(recordId, tagText, user)).thenReturn(anySuccessResult());

    AjaxReturnObject<Boolean> rc = strucDocCtrller.tagRecord(recordId, tagText, mockPrincipal);
    assertNotNull(rc.getData());
    // empty text ok if recordMgr returns OK
    tagText = "";
    when(documentTagManager.saveTag(recordId, tagText, user)).thenReturn(anySuccessResult());
    rc = strucDocCtrller.tagRecord(recordId, tagText, mockPrincipal);
    assertNotNull(rc.getData());

    tagText = "";
    when(documentTagManager.saveTag(recordId, tagText, user)).thenReturn(anyFailResult());
    rc = strucDocCtrller.tagRecord(recordId, tagText, mockPrincipal);
    assertNotNull(rc.getErrorMsg());
    assertNull(rc.getData());

    // invalid tags rejected
    messageSource.addMessage("errors.invalidchars", Locale.getDefault(), "xss");
    tagText = "<img src='' onerror='alert(3);'>";
    when(documentTagManager.saveTag(recordId, tagText, user)).thenReturn(anySuccessResult());

    when(validator.validateAndGetErrorList(
            Mockito.any(RSpaceTag.class), Mockito.any(TagValidator.class)))
        .thenReturn(ErrorList.createErrListWithSingleMsg("msg"));
    rc = strucDocCtrller.tagRecord(recordId, tagText, mockPrincipal);
    assertNotNull(rc.getErrorMsg());
    assertEquals("msg", rc.getErrorMsg().getErrorMessages().get(0));
    assertNull(rc.getData());
  }

  @Test()
  public void testGetTooLongTagRejected() {
    messageSource.addMessage("errors.maxlength", Locale.getDefault(), "any");
    CoreTestUtils.assertIllegalArgumentException(
        () -> strucDocCtrller.getTags(randomAlphanumeric(StructuredDocument.MAX_TAG_LENGTH + 1)));
  }

  @Test()
  public void testGetTags() {
    messageSource.addMessage("errors.maxlength", Locale.getDefault(), "any");
    strucDocCtrller.getTags(randomAlphanumeric(StructuredDocument.MAX_TAG_LENGTH));
    Mockito.verify(documentTagManager)
        .getTagsPlusMetaForViewableELNDocuments(Mockito.any(), Mockito.anyString());
  }

  private MessagedServiceOperationResult<BaseRecord> anySuccessResult() {
    return new MessagedServiceOperationResult<>(TestFactory.createAnySD(), true, "");
  }

  private MessagedServiceOperationResult<BaseRecord> anyFailResult() {
    return new MessagedServiceOperationResult<>(TestFactory.createAnySD(), false, "fail");
  }

  @Test
  public void editDescriptionRequiresEditPermission() {
    generalExpectations();
    final Folder toEdit = TestFactory.createAFolder("any", user);
    toEdit.setId(1L);
    when(baseRecordMgr.get(1L, user)).thenReturn(toEdit);
    when(permissionUtils.isPermitted(toEdit, PermissionType.WRITE, user)).thenReturn(true);

    AjaxReturnObject<Boolean> aro =
        strucDocCtrller.setDocumentDescription(1L, "desc23", mockPrincipal);
    assertTrue(aro.getData());
  }

  @Test(expected = AuthorizationException.class)
  public void editDescriptionThrowsAuthExceptionIfNotWritePermission() {
    generalExpectations();
    messageSource.addMessage("error.authorization.failure.polite", Locale.getDefault(), "any");
    final Folder toEdit = TestFactory.createAFolder("any", user);
    toEdit.setId(1L);
    when(baseRecordMgr.get(1L, user)).thenReturn(toEdit);
    verify(baseRecordMgr, never()).save(toEdit, user);
    when(permissionUtils.isPermitted(toEdit, PermissionType.WRITE, user)).thenReturn(false);

    strucDocCtrller.setDocumentDescription(1L, "desc23", mockPrincipal);
  }

  @Test(expected = ObjectRetrievalFailureException.class)
  public void editDescriptionThrowsISEIfNotExists() {
    generalExpectations();
    messageSource.addMessage("record.inaccessible", Locale.getDefault(), "any");
    final Folder toEdit = TestFactory.createAFolder("any", user);
    toEdit.setId(1L);
    when(baseRecordMgr.get(1L, user)).thenThrow(new ObjectRetrievalFailureException("", null));
    verify(baseRecordMgr, never()).save(toEdit, user);
    verify(permissionUtils, never()).isPermitted(toEdit, PermissionType.WRITE, user);
    when(permissionUtils.isPermitted(toEdit, PermissionType.WRITE, user)).thenReturn(false);

    strucDocCtrller.setDocumentDescription(1L, "desc23", mockPrincipal);
  }

  @Test
  public void getAutosavedFields() throws InterruptedException {
    StructuredDocument sd = TestFactory.createAnySD();

    Field field = sd.getFields().iterator().next();
    field.setId(1L);
    final List<Field> rc = TransformerUtils.toList(field);
    generalExpectations();
    when(fieldManager.getFieldsByRecordId(1L, null)).thenReturn(rc);

    // no temp fields, returns empty list
    assertEquals(0, strucDocCtrller.getAutoSavedFields(field.getId()).size());

    // now let's set a etmp field into field
    Field tempField = TestFactory.createAnyField();
    field.setTempField(tempField);
    // field is connected
    assertNotNull(field.getFieldForm().getForm());
    assertNotNull(field.getStructuredDocument());

    assertEquals(field, strucDocCtrller.getAutoSavedFields(field.getId()).get(0));
    // returned fields are disconnected
    assertNull(field.getFieldForm().getForm());
    assertNull(field.getStructuredDocument());
  }

  private void generalExpectations() {
    when(userMgr.getUserByUsername(eq(user.getUsername()))).thenReturn(user);
  }

  @Test
  public void insertComment() throws InterruptedException {
    StructuredDocument sd = TestFactory.createAnySD();

    Field field = sd.getFields().iterator().next();
    field.setId(1L);
    generalExpectations();
    messageSource.addMessage("errors.emptyString.polite", Locale.getDefault(), "any");
    verify(mediaMgr, never())
        .insertEcatComment(
            Mockito.any(String.class), Mockito.any(String.class), Mockito.any(User.class));

    // empty comment rejected
    AjaxReturnObject<Long> aro = strucDocCtrller.insertComment(1L + "", "");
    assertNull(aro.getData());
    assertNotNull(aro.getErrorMsg());
    // too long comment rejected
    messageSource.addMessage("errors.maxlength", Locale.getDefault(), "any");
    String tooLong = RandomStringUtils.randomAlphabetic(EcatComment.MAX_COMMENT_LENGTH + 1);
    aro = strucDocCtrller.insertComment(1L + "", tooLong);
    assertNull(aro.getData());
    assertNotNull(aro.getErrorMsg());
    // happy case
    final EcatComment createdComment = TestFactory.createEcatComment(1L, sd, 2L);
    when(mediaMgr.insertEcatComment(1L + "", "comment", user)).thenReturn(createdComment);
    getAuthenticatedUser();

    aro = strucDocCtrller.insertComment(1L + "", "comment");
    assertEquals(2, aro.getData().intValue());
    assertNull(aro.getErrorMsg());
  }

  @Test
  public void addComment() throws InterruptedException {
    StructuredDocument sd = TestFactory.createAnySD();

    Field field = sd.getFields().iterator().next();
    field.setId(1L);
    final EcatComment createdComment = TestFactory.createEcatComment(1L, sd, 2L);
    generalExpectations();
    messageSource.addMessage("errors.emptyString.polite", Locale.getDefault(), "any");
    verify(mediaMgr, never())
        .insertEcatComment(
            Mockito.any(String.class), Mockito.any(String.class), Mockito.any(User.class));
    // empty comment rejected
    AjaxReturnObject<Boolean> aro =
        strucDocCtrller.addComment(createdComment.getComId() + "", 1L + "", "");
    assertNull(aro.getData());
    assertNotNull(aro.getErrorMsg());
    // too long comment rejected
    messageSource.addMessage("errors.maxlength", Locale.getDefault(), "any");
    String tooLong = RandomStringUtils.randomAlphabetic(EcatComment.MAX_COMMENT_LENGTH + 1);
    aro = strucDocCtrller.addComment(createdComment.getComId() + "", 1L + "", tooLong);
    assertNull(aro.getData());
    assertNotNull(aro.getErrorMsg());
    // happy case
    when(mediaMgr.addEcatComment(2L + "", 1L + "", "comment", user)).thenReturn(createdComment);
    getAuthenticatedUser();

    aro = strucDocCtrller.addComment(2L + "", 1L + "", "comment");
    assertTrue("Data reposnse not true", aro.getData());
    assertNull("error message not null", aro.getErrorMsg());
  }

  private void getAuthenticatedUser() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(user);
  }

  @Test
  public void onlyOwnerCanDeleteDocument() throws Exception {
    final StructuredDocument sd = TestFactory.createAnySD();
    final User owner = TestFactory.createAnyUser("any");
    sd.setOwner(owner);
    sd.setId(3L);
    messageSource.addMessage("document.deletebyuseronly.msg", Locale.getDefault(), "any");
    generalExpectations();
    when(recordMgr.getParentFolderOfRecordOwner(3L, user)).thenReturn(null);

    assertExceptionThrown(
        () -> strucDocCtrller.deleteStructuredDocument(3L, mockPrincipal),
        IllegalStateException.class);
  }

  @Test
  public void getComments() throws Exception {
    final StructuredDocument sd = TestFactory.createAnySD();
    Optional<BaseRecord> optSd = Optional.of(sd);
    sd.setId(3L);
    Field field = sd.getFields().iterator().next();
    field.setId(1L);
    final EcatComment createdComment = TestFactory.createEcatComment(1L, sd, 2L);
    createdComment.addCommentItem(new EcatCommentItem(createdComment, "comment", user));
    final List<EcatCommentItem> items = createdComment.getItems();
    generalExpectations();
    when(recordAdapter.getAsBaseRecord(createdComment)).thenReturn(optSd);
    when(permissionUtils.isPermitted(sd, PermissionType.READ, user)).thenReturn(true);
    when(commentMgr.getCommentItems(2L)).thenReturn(items);
    when(recordMgr.get(3L)).thenReturn(sd);

    assertTrue(
        "Did not have 1 comment ",
        strucDocCtrller.getComments(2L, null, mockPrincipal).size() == 1);

    items.get(0).setEcatComment(createdComment); // reset this
    // now try if permission denied....throws AuthException
    when(permissionUtils.isPermitted(sd, PermissionType.READ, user)).thenReturn(false);

    messageSource.addMessage("record.inaccessible", Locale.getDefault(), "any");
    assertAuthExceptionThrown(
        new Invokable() {
          public void invoke() {
            strucDocCtrller.getComments(2L, null, mockPrincipal);
          }
        });

    // now lets try revision history, also should be authorized
    final int revisionId = 123;
    when(auditMgr.getCommentItemsForCommentAtDocumentRevision(2L, revisionId)).thenReturn(items);

    assertAuthExceptionThrown(
        new Invokable() {
          public void invoke() {
            strucDocCtrller.getComments(2L, revisionId, mockPrincipal);
          }
        });
  }

  @Test
  public void importFromWordWithContentReplaceFailsSingleFileOnly() throws IOException {
    messageSource.addMessage(
        "workspace.create.fromMSWord.replace.error.1only", Locale.getDefault(), "toomany");

    getAuthenticatedUser();
    Long recordToReplaceId = 2L;
    // 2 files
    when(recordMgr.get(recordToReplaceId)).thenReturn(null);
    List<MultipartFile> files = getOKFilesToUpload();
    files.addAll(getOKFilesToUpload());
    StructuredDocument toReplace = TestFactory.createAnySD();
    toReplace.setId(2L);
    toReplace.setForm(new RecordFactory().createBasicDocumentForm(user));
    when(recordMgr.get(recordToReplaceId)).thenReturn(toReplace);
    when(permissionUtils.isPermitted(toReplace, PermissionType.WRITE, user)).thenReturn(true);
    AjaxReturnObject<List<RecordInformation>> resp =
        strucDocCtrller.createSDFromWordFile(1L, files, recordToReplaceId, session);
    assertNotNull(resp.getErrorMsg());
    assertTrue(resp.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy("").contains("toomany"));
  }

  @Test
  public void importFromWordWithContentReplaceFailsIfNotBasicDoc() throws IOException {
    messageSource.addMessage(
        "workspace.create.fromMSWord.replace.error.notbasic", Locale.getDefault(), "basic");
    messageSource.addMessage("errors.strucdoc.required", Locale.getDefault(), "notadoc");

    getAuthenticatedUser();
    Long recordToReplaceId = 2L;
    // not exists
    when(recordMgr.get(recordToReplaceId)).thenReturn(null);
    List<MultipartFile> files = getOKFilesToUpload();
    StructuredDocument toReplace = TestFactory.createAnySD();
    toReplace.setId(2L);
    when(recordMgr.get(recordToReplaceId)).thenReturn(toReplace);
    when(permissionUtils.isPermitted(toReplace, PermissionType.WRITE, user)).thenReturn(true);
    AjaxReturnObject<List<RecordInformation>> resp =
        strucDocCtrller.createSDFromWordFile(1L, files, recordToReplaceId, session);
    assertNotNull(resp.getErrorMsg());
    assertTrue(resp.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy("").contains("basic"));

    Record nonDocument = TestFactory.createEcatImage(3L);
    when(recordMgr.get(recordToReplaceId)).thenReturn(nonDocument);
    when(permissionUtils.isPermitted(nonDocument, PermissionType.WRITE, user)).thenReturn(true);
    resp = strucDocCtrller.createSDFromWordFile(1L, files, recordToReplaceId, session);
    assertNotNull(resp.getErrorMsg());
    assertTrue(resp.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy("").contains("notadoc"));

    verifyWordImportNotAttempted();
  }

  @Test
  public void importFromWordWithContentReplaceFailsIfRecordToReplaceNotExistsOrNotPermitted()
      throws IOException {
    messageSource.addMessage(
        "error.authorization.failure.polite", Locale.getDefault(), "not permitted");
    getAuthenticatedUser();
    Long recordToReplaceId = 2L;
    // not exists
    when(recordMgr.get(recordToReplaceId)).thenReturn(null);
    List<MultipartFile> files = getOKFilesToUpload();
    AjaxReturnObject<List<RecordInformation>> resp =
        strucDocCtrller.createSDFromWordFile(1L, files, recordToReplaceId, session);
    assertNotNull(resp.getErrorMsg());
    assertTrue(
        resp.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy("").contains("not permitted"));
    // not permitted
    StructuredDocument toReplace = TestFactory.createAnySD();
    toReplace.setId(2L);
    when(recordMgr.get(recordToReplaceId)).thenReturn(toReplace);
    when(permissionUtils.isPermitted(toReplace, PermissionType.WRITE, user)).thenReturn(false);
    resp = strucDocCtrller.createSDFromWordFile(1L, files, recordToReplaceId, session);
    assertNotNull(resp.getErrorMsg());
    assertTrue(
        resp.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy("").contains("not permitted"));
    verifyWordImportNotAttempted();
  }

  private void verifyWordImportNotAttempted() throws IOException {
    verify(fileimporter, never())
        .create(
            Mockito.any(InputStream.class),
            Mockito.any(User.class),
            Mockito.any(Folder.class),
            Mockito.isNull(),
            Mockito.any(String.class));
  }

  private List<MultipartFile> getOKFilesToUpload() throws IOException {
    MultipartFile multipart =
        new MockMultipartFile(
            "wordFile", RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("genFilesi.txt"));
    List<MultipartFile> files = toList(multipart);
    return files;
  }

  @Test
  public void importFromWordIntoNonSharedFolderAndErrorCases() throws IOException {

    messageSource.addMessage(
        "workspace.word.import.nofiles.error.msg", Locale.getDefault(), "empty");

    getAuthenticatedUser();
    List<MultipartFile> files = new ArrayList<>();
    Folder parentFolder = TestFactory.createAFolder("any", user);
    parentFolder.setId(1L);

    AjaxReturnObject<List<RecordInformation>> resp =
        strucDocCtrller.createSDFromWordFile(parentFolder.getId(), files, null, session);
    verifyWordImportNotAttempted();
    assertNull(resp.getData());
    assertNotNull(resp.getErrorMsg());
    assertEquals("empty", resp.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy(""));

    MultipartFile multipart =
        new MockMultipartFile(
            "wordFile",
            "genFilesi.txt",
            "text/text",
            RSpaceTestUtils.getResourceAsByteArray("genFilesi.txt"));
    files = toList(multipart);
    StructuredDocument created = TestFactory.createAnySD();
    created.setId(2L);

    when(fMgr.getFolder(parentFolder.getId(), user)).thenReturn(parentFolder);
    when(permissionUtils.isRecordAccessPermitted(user, parentFolder, PermissionType.READ))
        .thenReturn(TRUE);
    whenCreatingDoc(multipart).thenReturn(created);
    when(recordMgr.isSharedFolderOrSharedNotebookWithoutCreatePermssion(user, parentFolder))
        .thenReturn(false);

    AjaxReturnObject<List<RecordInformation>> res =
        strucDocCtrller.createSDFromWordFile(parentFolder.getId(), files, null, session);
    verifyFileImporterCalled(multipart);
    assertThat(res.getData(), contains(equalTo(created.toRecordInfo())));
    verifyNoInteractions(recordShareHandler);

    whenCreatingDoc(multipart).thenThrow(new RuntimeException());
    res = strucDocCtrller.createSDFromWordFile(parentFolder.getId(), files, null, session);
    assertEquals(0, res.getData().size());
    assertThat(res.getErrorMsg().getErrorMessages().size(), is(1));
    verifyNoInteractions(recordShareHandler);

    whenCreatingDoc(multipart).thenReturn(null);
    res = strucDocCtrller.createSDFromWordFile(parentFolder.getId(), files, null, session);
    assertEquals(0, res.getData().size());
    assertThat(res.getErrorMsg().getErrorMessages().size(), is(1));
    verifyNoInteractions(recordShareHandler);
  }

  @Test
  public void importFromWordIntoSharedFolderOrSharedNotebook() throws IOException {
    getAuthenticatedUser();
    Folder parentFolder = TestFactory.createAFolder("any", user);
    parentFolder.setId(1L);

    MultipartFile multipart =
        new MockMultipartFile(
            "wordFile",
            "genFilesi.txt",
            "text/text",
            RSpaceTestUtils.getResourceAsByteArray("genFilesi.txt"));
    List<MultipartFile> files = toList(multipart);
    StructuredDocument created = TestFactory.createAnySD();
    created.setId(2L);

    when(fMgr.getFolder(parentFolder.getId(), user)).thenReturn(parentFolder);
    when(permissionUtils.isRecordAccessPermitted(user, parentFolder, PermissionType.READ))
        .thenReturn(TRUE);
    whenCreatingDoc(multipart).thenReturn(created);
    when(recordMgr.isSharedFolderOrSharedNotebookWithoutCreatePermssion(user, parentFolder))
        .thenReturn(true);

    AjaxReturnObject<List<RecordInformation>> res =
        strucDocCtrller.createSDFromWordFile(parentFolder.getId(), files, null, session);
    verifyFileImporterCalled(multipart);
    assertThat(res.getData(), contains(equalTo(created.toRecordInfo())));
    verify(recordShareHandler).shareIntoSharedFolderOrNotebook(user, parentFolder, created.getId());
  }

  private void verifyFileImporterCalled(MultipartFile multipart) throws IOException {
    verify(fileimporter, times(1))
        .create(
            Mockito.any(InputStream.class),
            eq(user),
            Mockito.any(Folder.class),
            Mockito.isNull(),
            eq(multipart.getOriginalFilename()));
  }

  private OngoingStubbing<BaseRecord> whenCreatingDoc(MultipartFile multipart) throws IOException {
    return when(
        fileimporter.create(
            Mockito.any(InputStream.class),
            eq(user),
            Mockito.any(Folder.class),
            Mockito.isNull(),
            eq(multipart.getOriginalFilename())));
  }
}

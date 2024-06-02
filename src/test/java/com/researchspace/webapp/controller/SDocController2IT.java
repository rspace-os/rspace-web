package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.model.PaginationCriteria.createDefaultForClass;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_EDITED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_SHARED_PREF;
import static com.researchspace.session.UserSessionTracker.USERS_KEY;
import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.DuplicateAuditEvent;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.AuditManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.hibernate.query.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;

public class SDocController2IT extends RealTransactionSpringTestBase {

  private @Autowired StructuredDocumentController controller;
  private @Autowired RecordSharingController rsc;
  private @Autowired RecordEditorTracker recordEditorTracker;
  private @Autowired AuditManager auditMgr;
  private @Mock AuditTrailService auditTrailService;
  @Autowired private GroupManager groupManager;

  private MockServletContext sc;
  private static final int MIN_USERNAMELENGTH = 10;
  private MockHttpSession mockHttpSession;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
    sc = new MockServletContext();
    sc.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    mockHttpSession = new MockHttpSession();
    controller.setServletContext(sc);
    controller.setAuditService(auditTrailService);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  protected UserSessionTracker getCurrentActiveUsers() {
    return (UserSessionTracker) sc.getAttribute(UserSessionTracker.USERS_KEY);
  }

  @Test
  public void testInitOfDemoContent() throws Exception {
    // random username so we don't interfere with regular user accounts
    User user = createAndSaveUser(getRandomName(MIN_USERNAMELENGTH));
    logoutAndLoginAs(user);

    Folder root = contentInitializer.init(user.getId()).getUserRoot();

    // one for each of different content - image.audio etc
    assertExpectedNumberOfMediaFolders(user, root, 0);
    assertTemplateFolderExistsWithSharedFolderInside(user, root);
    // a comment
  }

  private void assertTemplateFolderExistsWithSharedFolderInside(User user, Folder root)
      throws Exception {
    doInTransaction(
        () -> {
          // count number of shared media folders - ignore shared folder in root folder
          Query<Folder> q =
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      "from Folder where editInfo.name=:name and editInfo.createdBy=:creator",
                      Folder.class);
          q.setParameter("name", Folder.TEMPLATE_MEDIA_FOLDER_NAME);
          q.setParameter("creator", user.getUsername());
          Folder f = q.uniqueResult();
          assertTrue(f.getChildrens().size() > 0);
        });
  }

  private void assertExpectedNumberOfMediaFolders(User user, Folder root, int expectedFoldersCount)
      throws Exception {
    doInTransaction(
        () -> {
          // count number of shared media folders - ignore shared folder in root folder
          Query<Long> q =
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      "select count(*) from BaseRecord br, RecordToFolder r2f where"
                          + " br.editInfo.name=:name  and br.id=r2f.record.id and r2f.folder.id !="
                          + " :folderId and br.editInfo.createdBy=:creator",
                      Long.class);
          q.setParameter("name", Folder.SHARED_FOLDER_NAME);
          q.setParameter("folderId", root.getId());
          q.setParameter("creator", user.getUsername());
          assertEquals(expectedFoldersCount, q.uniqueResult().intValue());
        });
  }

  @Test
  public void testPolling() throws Exception {
    StructuredDocument anySDoc = setUpLoginAsPIUserAndCreateADocument();
    // return null as noone is yet editing
    assertNull(controller.getOtherUserEditingRecord(anySDoc.getId(), mockPrincipal).getData());
    recordEditorTracker.attemptToEdit(
        anySDoc.getId(), piUser, anySessionTracker(), SessionAttributeUtils::getSessionId);

    // return null as we're editing ourselves
    assertNull(controller.getOtherUserEditingRecord(anySDoc.getId(), mockPrincipal).getData());

    // create other user, and login as them
    final User other = createAndSaveUser(getRandomName(10));
    logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    // now we get the user's name, who has locked the record
    AjaxReturnObject<UserPublicInfo> aro =
        controller.getOtherUserEditingRecord(anySDoc.getId(), other::getUsername);
    assertNotNull(aro);
    assertEquals(piUser.getUsername(), aro.getData().getUsername());

    // now simulate 'user' unlocking the record
    recordEditorTracker.unlockRecord(anySDoc, piUser, SessionAttributeUtils::getSessionId);

    // now we'll get null returned as the record has no editors.
    assertNull(controller.getOtherUserEditingRecord(anySDoc.getId(), mockPrincipal).getData());
  }

  @Test
  public void saveNewStructuredDocumentTest() throws Exception {

    User user = createAndSaveUser(getRandomName(MIN_USERNAMELENGTH));
    logoutAndLoginAs(user);
    Principal principal = user::getUsername;
    Folder root = initUser(user);
    StructuredDocument sd = createDocumentInFolder(root, createAnyForm(user), user);

    // return null as noone is yet editing
    assertNull(controller.getOtherUserEditingRecord(sd.getId(), mockPrincipal).getData());

    // and now make sure we can edit
    recordMgr.requestRecordEdit(sd.getId(), user, anySessionTracker());

    AjaxReturnObject<String> url = controller.saveNewStructuredDocument(sd.getId(), principal);
    assertNotNull(url.getData());
    Long fieldId = sd.getFields().iterator().next().getId();
    Long commentid = controller.insertComment(fieldId + "", "any").getData();
    assertEquals(1, controller.getComments(commentid, null, principal).size());
  }

  @Test
  public void saveCopyStructuredDocumentTest() throws Exception {

    User u = createAndSaveUser(getRandomName(MIN_USERNAMELENGTH));
    logoutAndLoginAs(u);

    Folder root = initUser(u);
    StructuredDocument sd = createDocumentInFolder(root, createAnyForm(u), u);

    // return null as noone is yet editing
    assertNull(controller.getOtherUserEditingRecord(sd.getId(), mockPrincipal).getData());

    // and now make sure we can edit
    recordMgr.requestRecordEdit(sd.getId(), u, anySessionTracker());

    AjaxReturnObject<String> url =
        controller.saveCopyStructuredDocument(
            sd.getId(), sd.getName(), new MockPrincipal(u.getUsername()));
    assertNotNull(url.getData());
    assertFalse(url.getData().contains(sd.getId().toString()));
    verify(auditTrailService).notify(any(DuplicateAuditEvent.class));
  }

  @Test
  public void saveNotebookEntryTest() throws Exception {

    User u = createAndSaveUser(getRandomName(MIN_USERNAMELENGTH));
    logoutAndLoginAs(u);

    Folder root = initUser(u);
    Notebook nb = createNotebookWithNEntries(root.getId(), u.getUsername() + "nb", 1, u);
    Long nbEntryId = folderMgr.getRecordIds(nb).get(0);

    // start editing
    recordMgr.requestRecordEdit(nbEntryId, u, anySessionTracker());

    // save and clone
    AjaxReturnObject<String> cloneUrl =
        controller.saveCopyStructuredDocument(
            nbEntryId, "entry", new MockPrincipal(u.getUsername()));
    Long clonedEntryId = folderMgr.getRecordIds(nb).get(1);
    assertNotEquals(nbEntryId, clonedEntryId);

    String expectedCloneUrl =
        StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
            + "/"
            + clonedEntryId
            + "?fromNotebook="
            + nb.getId();
    assertEquals(expectedCloneUrl, cloneUrl.getData());
    verify(auditTrailService).notify(any(DuplicateAuditEvent.class));

    // save and new
    AjaxReturnObject<String> newUrl =
        controller.saveNewStructuredDocument(nbEntryId, new MockPrincipal(u.getUsername()));
    Long newEntryId = folderMgr.getRecordIds(nb).get(2);
    assertNotEquals(clonedEntryId, newEntryId);

    String expectedNewUrl =
        StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
            + "/"
            + newEntryId
            + "?fromNotebook="
            + nb.getId();
    assertEquals(expectedNewUrl, newUrl.getData());
    String anyWorkspaceSettingsToken = "";
    // save and close
    AjaxReturnObject<String> closeUrl =
        controller.saveStructuredDocument(
            nbEntryId,
            anyWorkspaceSettingsToken,
            true,
            new MockPrincipal(u.getUsername()),
            mockHttpSession);
    String expectedCloseUrl =
        NotebookEditorController.getNotebookViewUrl(
            nb.getId(), nbEntryId, anyWorkspaceSettingsToken);
    assertEquals(expectedCloseUrl, closeUrl.getData());
  }

  @Test
  public void createAutosaveAndCancelStructuredDocument() throws Exception {

    final String textToAutosave = "text to be autosaved";

    // create
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    assertNull(sd.getTempRecord());

    Field field = sd.getFields().get(0);
    assertNull(field.getTempField());
    assertEquals("field should be initially empty", "", field.getData());

    // request edit
    EditStatus requestEdit = controller.requestEdit(sd.getId(), mockPrincipal);
    assertEquals("user should be able to edit created document", EditStatus.EDIT_MODE, requestEdit);

    // autosave
    controller.autosaveField(textToAutosave, field.getId(), mockPrincipal);

    StructuredDocument sdAfterAutosave =
        recordMgr.getRecordWithFields(sd.getId(), piUser).asStrucDoc();
    assertNotNull(sdAfterAutosave.getTempRecord());

    Field fieldAfterAutosave = sdAfterAutosave.getFields().get(0);
    assertEquals(
        "non-temp field should still be empty after autosave", "", fieldAfterAutosave.getData());

    Field tempField = fieldAfterAutosave.getTempField();
    assertNotNull("temp field should be present", tempField);
    assertEquals("temp field should hold autosaved text", textToAutosave, tempField.getData());

    // cancel
    controller.cancelAutosavedEdits(sd.getId(), mockPrincipal);

    StructuredDocument sdAfterCancel =
        recordMgr.getRecordWithFields(sd.getId(), piUser).asStrucDoc();
    assertNull("temp record should be gone after canceling", sdAfterCancel.getTempRecord());

    Field fieldAfterCancel = sdAfterAutosave.getFields().get(0);
    assertEquals("non-temp field should be empty after cancel", "", fieldAfterCancel.getData());
    assertNotNull("temp field should be gone after canceling", fieldAfterCancel.getTempField());
    assertNull(
        "the record should be unlocked after cancel",
        recordEditorTracker.getEditingUserForRecord(sd.getId()));
  }

  @Test
  public void deleteStructuredDocumentTest() throws Exception {

    setUpLoginAsPIUserAndCreateADocument();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(piUser, "test");
    Long parentId = doc.getParent().getId();

    User pi = createAndSaveUser("pi" + getRandomName(6));
    initUser(pi);
    Group g = createGroupForUsersWithDefaultPi(piUser, pi);

    // share the record
    ShareConfigElement gsce = new ShareConfigElement(g.getId(), "WRITE");
    sharingMgr.shareRecord(piUser, doc.getId(), new ShareConfigElement[] {gsce});

    // pi starts editing
    getCurrentActiveUsers().addUser(pi.getUsername(), mockHttpSession);
    EditStatus piEdit =
        recordEditorTracker.attemptToEdit(
            doc.getId(), pi, getCurrentActiveUsers(), SessionAttributeUtils::getSessionId);
    assertEquals(EditStatus.EDIT_MODE, piEdit);

    // user cannot delete document edited by pi
    AjaxReturnObject<String> deleteError =
        controller.deleteStructuredDocument(doc.getId(), mockPrincipal);
    assertEquals(1, deleteError.getError().getErrorMessages().size());
    assertTrue(
        deleteError
            .getError()
            .getErrorMessages()
            .get(0)
            .contains("cannot be deleted as it is currently edited by " + pi.getUsername()));

    // pi stops editing
    recordEditorTracker.unlockRecord(doc, pi, SessionAttributeUtils::getSessionId);

    // user can delete the document fine
    AjaxReturnObject<String> deleteOk =
        controller.deleteStructuredDocument(doc.getId(), mockPrincipal);
    assertNotNull(deleteOk.getData());
    assertTrue(
        "expected " + parentId + " , but got: " + deleteOk.getData(),
        deleteOk.getData().contains(parentId.toString()));
  }

  @Test(expected = RecordAccessDeniedException.class)
  public void testSharedRecordAccessThrowsExceptionIfNotAvailable() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser("pi");
    initUser(other);
    createGroupForUsersWithDefaultPi(piUser, other);
    logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    controller.openDocument(
        sd.getId(), "", false, false, null, modelTss, mockHttpSession, other::getUsername);
  }

  @Test
  public void testPublishedRecordAccess() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User anonymousUser = userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
    ConstraintBasedPermission cbp = parser.resolvePermission("RECORD:READ:");
    sd.getSharingACL().addACLElement(anonymousUser, cbp);
    recordMgr.save(sd, sd.getOwner());
    ModelAndView result =
        controller.openDocument(
            sd.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            sd.getOwner()::getUsername);
    assertTrue((Boolean) result.getModelMap().getAttribute("isPublished"));
  }

  @Test
  public void testEnforceOntologies() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    ModelAndView result =
        controller.openDocument(
            sd.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            sd.getOwner()::getUsername);
    assertFalse((Boolean) result.getModelMap().getAttribute("enforce_ontologies"));
    Group g = createGroupForUsersWithDefaultPi(piUser);
    g.setEnforceOntologies(true);
    groupManager.saveGroup(g, piUser);
    result =
        controller.openDocument(
            sd.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            sd.getOwner()::getUsername);
    assertTrue((Boolean) result.getModelMap().getAttribute("enforce_ontologies"));
  }

  @Test
  public void testAllowBioOntologies() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    ModelAndView result =
        controller.openDocument(
            sd.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            sd.getOwner()::getUsername);
    assertFalse((Boolean) result.getModelMap().getAttribute("allow_bioOntologies"));
    Group projectG = createGroupForUsersWithDefaultPi(piUser);
    projectG.setGroupType(GroupType.PROJECT_GROUP);
    groupManager.saveGroup(projectG, piUser);
    result =
        controller.openDocument(
            sd.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            sd.getOwner()::getUsername);
    assertFalse((Boolean) result.getModelMap().getAttribute("allow_bioOntologies"));
    Group g = createGroupForUsersWithDefaultPi(piUser);
    g.setAllowBioOntologies(true);
    groupManager.saveGroup(g, piUser);
    result =
        controller.openDocument(
            sd.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            sd.getOwner()::getUsername);
    assertTrue((Boolean) result.getModelMap().getAttribute("allow_bioOntologies"));
  }

  @Test
  public void testSharedRecordAccess() throws Exception {
    // there are 2 users here: 'user' who is the record owner, and 'other'
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser("pi2");
    Folder otherRoot = initUser(other);
    Group g = createGroupForUsersWithDefaultPi(piUser, other);
    Principal otherPcpal = other::getUsername;

    // users opt in for shared doc notifications
    userMgr.setPreference(
        NOTIFICATION_DOCUMENT_SHARED_PREF, Boolean.TRUE.toString(), other.getUsername());
    userMgr.setPreference(
        NOTIFICATION_DOCUMENT_EDITED_PREF, Boolean.TRUE.toString(), piUser.getUsername());

    logoutAndLoginAs(other);
    // the point here is just to perform an operation that will load and
    // cache 'other's permissions
    createDocumentInFolder(otherRoot, sd.getForm(), other);

    // user now shares record
    logoutAndLoginAs(piUser);
    Date currentSecond = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    ShareConfigElement gsec = new ShareConfigElement(g.getId(), "READ");
    sharingMgr.shareRecord(piUser, sd.getId(), new ShareConfigElement[] {gsec});

    // check 'other' receives notification
    assertUserHasNotificationOfTypeSince(
        other, NotificationType.NOTIFICATION_DOCUMENT_SHARED, currentSecond);

    logoutAndLoginAs(other);
    ModelAndView result =
        controller.openDocument(
            sd.getId(), "", false, false, null, modelTss, mockHttpSession, otherPcpal);
    assertFalse((Boolean) result.getModelMap().getAttribute("isPublished"));
    assertEquals(EditStatus.CANNOT_EDIT_NO_PERMISSION, getEditStatus());

    /* Login as user, who shared the document */
    // now update to write permission
    logoutAndLoginAs(piUser);
    updatePermissionsTo("WRITE");

    /* Login as sharee 'other' */
    logoutAndLoginAs(other);
    // open for view, can have potential to edit
    controller.openDocument(
        sd.getId(), "", false, false, null, modelTss, mockHttpSession, otherPcpal);
    assertEquals(EditStatus.VIEW_MODE, getEditStatus());
    // and can get edit permission if need be
    assertEquals(EditStatus.EDIT_MODE, controller.requestEdit(sd.getId(), otherPcpal));

    controller.unlockRecord(sd.getId(), otherPcpal);

    /* Login as user, who shared the document */
    // now update to write permission
    logoutAndLoginAs(piUser);
    // other still logged in with edit access
    getCurrentActiveUsers().addUser(other.getUsername(), new MockHttpSession());
    assertEquals(EditStatus.EDIT_MODE, controller.requestEdit(sd.getId(), otherPcpal));
    assertEquals(
        EditStatus.CANNOT_EDIT_OTHER_EDITING, controller.requestEdit(sd.getId(), mockPrincipal));
    controller.unlockRecord(sd.getId(), otherPcpal);

    // Now login again as 'other'; we'll save (relinquishing edit lock)
    logoutAndLoginAs(other);
    currentSecond = Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    assertEquals(EditStatus.EDIT_MODE, controller.requestEdit(sd.getId(), otherPcpal));
    controller.saveStructuredDocument(sd.getId(), "", true, otherPcpal, mockHttpSession);

    // as an aside, check that 'user' receieves a ' document edited' notification
    assertUserHasNotificationOfTypeSince(
        piUser, NotificationType.NOTIFICATION_DOCUMENT_EDITED, currentSecond);

    // now login again as 'user'; can now obtain edit lock
    logoutAndLoginAs(piUser);
    assertEquals(EditStatus.EDIT_MODE, controller.requestEdit(sd.getId(), mockPrincipal));

    removeUserFromServletContext(other);
  }

  @Test
  public void testOpeningDocumentSharedIntoOthersNotebook()
      throws InterruptedException, RecordAccessDeniedException {

    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomName(10));
    User secondUser = createAndSaveUser(getRandomName(10));
    initUsers(pi, user, secondUser);

    Group group = createGroupForUsersWithDefaultPi(pi, user, secondUser);
    group = grpMgr.getGroup(group.getId());

    // pi creates notebook and shares for edit
    logoutAndLoginAs(pi);
    Notebook notebook =
        createNotebookWithNEntries(pi.getRootFolder().getId(), "shared for edit", 0, pi);
    shareNotebookWithGroup(pi, notebook, group, "write");

    // user creates record and shares it into groups notebook
    logoutAndLoginAs(user);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "test");
    doc = shareRecordIntoGroupNotebook(doc, notebook, group, user).get().getShared().asStrucDoc();

    // also shares it with another user, who is not in a group
    User otherUser = createAndSaveUser(getRandomName(10));
    shareRecordWithUser(user, doc, otherUser);

    // for user, the doc should open it in document view
    String documentViewUrl = StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME;
    String notebookRedirectUrl = controller.redirectToNotebookView(doc, "");
    ModelAndView userOpensDocument =
        controller.openDocument(
            doc.getId(), "", false, false, null, modelTss, mockHttpSession, user::getUsername);
    assertEquals(documentViewUrl, userOpensDocument.getViewName());

    // for pi, who sees it as a part of their notebook, should open in notebook view
    logoutAndLoginAs(pi);
    ModelAndView piOpensDocument =
        controller.openDocument(
            doc.getId(), "", false, false, null, modelTss, mockHttpSession, pi::getUsername);
    assertEquals(notebookRedirectUrl, piOpensDocument.getViewName());

    // for second user, who sees it as a part of pi's shared notebook, should open in notebook view
    logoutAndLoginAs(secondUser);
    ModelAndView secondUserOpensDocument =
        controller.openDocument(
            doc.getId(),
            "",
            false,
            false,
            null,
            modelTss,
            mockHttpSession,
            secondUser::getUsername);
    assertEquals(notebookRedirectUrl, secondUserOpensDocument.getViewName());

    // for other user, who sees it as individually shared doc, should open in document view
    logoutAndLoginAs(otherUser);
    ModelAndView otherUserOpensDocument =
        controller.openDocument(
            doc.getId(), "", false, false, null, modelTss, mockHttpSession, otherUser::getUsername);
    assertEquals(documentViewUrl, otherUserOpensDocument.getViewName());
  }

  @Test
  public void testCannotDeleteIfSomeoneElseEditing() throws Exception {

    final GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord(true);

    final StructuredDocument sd = setup.structuredDocument;
    // login as u1 and start editing
    logoutAndLoginAs(piUser);
    EditStatus status =
        recordMgr.requestRecordEdit(setup.structuredDocument.getId(), piUser, activeUsers);
    assertTrue(EditStatus.EDIT_MODE.equals(status));

    // run as separate user in new thread
    Runnable runnable =
        () -> {
          RSpaceTestUtils.login(setup.user.getUsername(), TESTPASSWD);
          EditStatus editStatus = recordMgr.requestRecordEdit(sd.getId(), setup.user, activeUsers);
          assertFalse(EditStatus.EDIT_MODE.equals(editStatus));
          RSpaceTestUtils.logout();
        };
    Thread thread = new Thread(runnable);
    thread.start();
    thread.join();
  }

  private void assertUserHasNotificationOfTypeSince(User user, NotificationType type, Date date) {

    PaginationCriteria<CommunicationTarget> paginationCriteria =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    log.info("looking for notification " + type.toString() + " created after " + date.getTime());
    ISearchResults<Notification> res =
        communicationMgr.getNewNotificationsForUser(user.getUsername(), paginationCriteria);
    for (Notification n : res.getResults()) {
      log.info(
          "found new notification "
              + n.getNotificationType()
              + " from "
              + n.getCreationTime().getTime());
      if (!n.getCreationTime().before(date) && n.getNotificationType().equals(type)) {
        return;
      }
    }
    fail("No matching notification");
  }

  private void removeUserFromServletContext(User user) {
    UserSessionTracker unsmes = (UserSessionTracker) sc.getAttribute(USERS_KEY);
    unsmes.forceRemoveUser(user.getUsername());
  }

  EditStatus getEditStatus() {
    return (EditStatus) modelTss.get("editStatus");
  }

  @SuppressWarnings("unchecked")
  void updatePermissionsTo(String perm) throws Exception {
    rsc.list(modelTss, mockPrincipal, createDefaultForClass(RecordGroupSharing.class));
    List<RecordGroupSharing> sharedRecs =
        (List<RecordGroupSharing>) modelTss.get(RecordSharingController.SHARED_RECORDS_ATTR_NAME);
    RecordGroupSharing rgs = sharedRecs.get(0);
    rsc.updatePermissions(mockPrincipal, rgs.getId(), perm);
  }

  @Test
  public void testSaveTemplates() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    Long recordId = sd.getId();
    List<Field> flds = sd.getFields();
    String tids[] = new String[flds.size()];
    for (int i = 0; i < flds.size(); i++) {
      tids[i] = "template_" + Long.toString(flds.get(i).getId());
    }
    String ss = controller.saveTemplate(tids, recordId, "test_template", mockPrincipal);
    assertNotNull(ss);
  }

  @Test
  public void getUpdatedFieldsTest() throws Exception {

    User u = createAndSaveUser(getRandomName(MIN_USERNAMELENGTH));
    logoutAndLoginAs(u);
    Folder root = initUser(u);
    Long previousDate = new Date().getTime();
    StructuredDocument sd = createDocumentInFolder(root, createAnyForm(u), u);

    List<Field> list =
        controller.getUpdatedFields(sd.getId(), previousDate, u::getUsername).getData();
    assertFalse(list.isEmpty());
  }

  @Test
  public void signingDocumentAddsRevisionNumberToAttachmentLinks() throws Exception {
    StructuredDocument sdoc = setUpLoginAsPIUserAndCreateADocument();
    Field field = sdoc.getFields().get(0);
    EcatImage img = addImageToField(field, piUser);
    addAudioFileToField(field, piUser);
    String initFieldData = field.getFieldData();
    assertFalse(initFieldData.contains("revision="));

    SigningResult signResult = signingManager.signRecord(sdoc.getId(), piUser, null, "statement");
    assertTrue(signResult.getSignature().isPresent());

    // reload record from DB and ensure it's signed
    StructuredDocument signedDoc = (StructuredDocument) recordMgr.get(sdoc.getId());
    assertTrue(signedDoc.isSigned());
    assertTrue(signingManager.isSigned(signedDoc.getId()));

    // check the field content, revision parameter should be present for links
    String signedFieldData =
        fieldMgr.getFieldsByRecordId(sdoc.getId(), piUser).get(0).getFieldData();
    assertEquals(2, StringUtils.countOccurrencesOf(signedFieldData, "rsrevision="));
    assertEquals(4, StringUtils.countOccurrencesOf(signedFieldData, "revision="));
    List<AuditedRecord> docRevsAfterSign = auditMgr.getHistory(sdoc, null);

    // after signing, uploading new version of gallery file doesn't update attachment link nor
    // create new revision
    updateImageInGallery(img.getId(), piUser);
    String signedFieldDataAfterGalleryUpdate =
        fieldMgr.getFieldsByRecordId(sdoc.getId(), piUser).get(0).getFieldData();
    assertEquals(signedFieldData, signedFieldDataAfterGalleryUpdate);
    List<AuditedRecord> docRevsAfterSignAndUpload = auditMgr.getHistory(sdoc, null);
    assertEquals(docRevsAfterSign.size(), docRevsAfterSignAndUpload.size());

    // copying the signed document should clear the revision from new copy
    RecordCopyResult copyResult =
        recordMgr.copy(sdoc.getId(), "copy", piUser, piUser.getRootFolder().getId());
    StructuredDocument copiedSDoc = copyResult.getUniqueCopy().asStrucDoc();
    assertFalse(copiedSDoc.getFields().get(0).getData().contains("revision="));
  }
}

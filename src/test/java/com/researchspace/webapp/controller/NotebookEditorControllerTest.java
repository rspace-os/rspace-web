package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.field.TextField;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.security.Principal;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

public class NotebookEditorControllerTest extends SpringTransactionalTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  private final String TEXT_FIELD_TEST_DATA = "I AM A TEXT FIELD BELONING TO ";
  private final String TEXT_FIELD_NAME = "TEXT ";
  private final String TEST_DATA_RECORD_NAME = "RECORD NAME ";
  private final String TEST_DATA_FOLDER_NAME = "FOLDER NAME ";

  @Autowired private NotebookEditorController notebookEditorController;

  private MockServletContext servletContext;

  Model model;
  ExtendedModelMap tss;
  User user;

  NotebookEditorRecordManagerStub recordManagerStub;
  @Mock IPermissionUtils permissionUtils;
  @Mock BreadcrumbGenerator breadcrumbGenerator;
  MockHttpSession mockSession = null;
  @Mock private User anonymousUser;

  Principal mockPrincipal =
      new Principal() {

        @Override
        public String getName() {
          return user.getUsername();
        }
      };
  @Mock private User userMock;

  @Before
  public void setUp() throws Exception {
    openMocks(this);
    when(anonymousUser.getUniqueName()).thenReturn(RecordGroupSharing.ANONYMOUS_USER);
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("nbTestUser"));
    logoutAndLoginAs(user);

    tss = new ExtendedModelMap();
    model = tss;
    recordManagerStub = new NotebookEditorRecordManagerStub();
    servletContext = new MockServletContext();
    FolderManagerStub fStub = new FolderManagerStub();
    fStub.setNotebookOwner(user);
    notebookEditorController.setFolderManager(fStub);
    notebookEditorController.setRecordManager(recordManagerStub);
    notebookEditorController.setPermissionUtils(permissionUtils);
    notebookEditorController.setServletContext(servletContext);
    mockSession = new MockHttpSession();
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
    FolderManagerStub.noteBooksArePublished = false;
  }

  @Test(expected = AuthorizationException.class)
  public void handleRequestNoPermission() throws AuthorizationException {
    // what happens when you have no permission to edit record
    when(permissionUtils.isPermitted(
            any(BaseRecord.class), any(PermissionType.class), any(User.class)))
        .thenReturn(false);
    notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
  }

  @Test
  public void handleRequest() throws Exception {
    // when you have permission to edit record
    when(permissionUtils.isPermitted(
            any(BaseRecord.class), any(PermissionType.class), any(User.class)))
        .thenReturn(true);
    ModelAndView results =
        notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertNotNull(results);
  }

  @Test
  public void shouldEnforceOntologies() throws Exception {

    when(permissionUtils.isPermitted(
            any(BaseRecord.class), any(PermissionType.class), any(User.class)))
        .thenReturn(true);
    ModelAndView result =
        notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertFalse((Boolean) result.getModelMap().getAttribute("enforce_ontologies"));
    User aPI = createAndSaveAPi();
    Group g = createGroup("aGroup", aPI);
    g.setEnforceOntologies(true);
    grpMgr.addUserToGroup(user.getUsername(), g.getId(), RoleInGroup.DEFAULT);
    grpMgr.saveGroup(g, user);
    result = notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertTrue((Boolean) result.getModelMap().getAttribute("enforce_ontologies"));
  }

  @Test
  public void shouldAllowBioPortalOntologies() throws Exception {

    when(permissionUtils.isPermitted(
            any(BaseRecord.class), any(PermissionType.class), any(User.class)))
        .thenReturn(true);
    ModelAndView result =
        notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertFalse((Boolean) result.getModelMap().getAttribute("allow_bioOntologies"));
    User aPI = createAndSaveAPi();
    Group aProjectG = createGroup("aProjectGroup", aPI);
    aProjectG.setGroupType(GroupType.PROJECT_GROUP);
    grpMgr.addUserToGroup(user.getUsername(), aProjectG.getId(), RoleInGroup.DEFAULT);
    grpMgr.saveGroup(aProjectG, user);
    result = notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertFalse((Boolean) result.getModelMap().getAttribute("allow_bioOntologies"));
    Group g = createGroup("aGroup", aPI);
    g.setAllowBioOntologies(true);
    grpMgr.addUserToGroup(user.getUsername(), g.getId(), RoleInGroup.DEFAULT);
    grpMgr.saveGroup(g, user);
    result = notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertTrue((Boolean) result.getModelMap().getAttribute("allow_bioOntologies"));
  }

  @Test
  public void handleRequestWhenPublishedNotebook() throws Exception {
    FolderManagerStub.noteBooksArePublished = true;
    FolderManagerStub.anonymousUser = anonymousUser;
    // when you have permission to edit record
    when(permissionUtils.isPermitted(
            any(BaseRecord.class), any(PermissionType.class), any(User.class)))
        .thenReturn(true);
    ModelAndView results =
        notebookEditorController.openNotebook(1l, null, "", model, mockSession, mockPrincipal);
    assertNotNull(results);
    assertTrue((Boolean) results.getModelMap().getAttribute("isPublished"));
  }

  @Test
  public void deleteEntryTest() throws Exception {
    notebookEditorController.deleteEntry(1l, 1l, mockPrincipal);
  }

  @Test(expected = RecordAccessDeniedException.class)
  public void deleteEntryTestAccessDenied() throws Exception {
    recordManagerStub.canEdit(false);
    notebookEditorController.deleteEntry(1l, 1l, mockPrincipal);
  }

  private StructuredDocument createRecordWithId(Long id) {
    User user = TestFactory.createAnyUser("user");
    StructuredDocument sd = new StructuredDocument(TestFactory.createAnyForm());
    Folder folder = TestFactory.createAFolder(TEST_DATA_FOLDER_NAME, user);
    folder.setId(id);

    try {
      folder.addChild(sd, user);
    } catch (IllegalAddChildOperation e) {
      e.printStackTrace();
    }

    sd.setId(id);
    sd.setName(TEST_DATA_RECORD_NAME + id);
    // add some fake fields using id as unique identifier
    TextField tf = new TextField(new TextFieldForm());
    tf.setId(1L);
    tf.setName(TEXT_FIELD_NAME + id);
    tf.setFieldData(TEXT_FIELD_TEST_DATA + id);
    sd.addField(tf);
    return sd;
  }

  private class NotebookEditorRecordManagerStub extends RecordManagerStub {

    Boolean isEmpty = false;
    Boolean canEdit = true;

    // override so we can test what happens when no records are returned
    @SuppressWarnings("unused")
    public void makeEmpty(Boolean isEmpty) {
      this.isEmpty = isEmpty;
    }

    // override so we can test what happens when user can't edit
    public void canEdit(Boolean edit) {
      this.canEdit = edit;
    }

    @Override
    public Record get(long id) {
      if (isEmpty) {
        return null;
      }
      return createRecordWithId(id);
    }

    @Override
    public EditStatus requestRecordEdit(
        final Long id, User editor, UserSessionTracker activeUsers) {
      return canEdit ? EditStatus.EDIT_MODE : EditStatus.ACCESS_DENIED;
    }
  }
}

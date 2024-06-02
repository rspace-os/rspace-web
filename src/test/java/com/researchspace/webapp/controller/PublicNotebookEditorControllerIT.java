package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.Constants;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

public class PublicNotebookEditorControllerIT extends RealTransactionSpringTestBase {

  private @Autowired PublicNotebookEditorController controller;
  private @Autowired JdbcTemplate jdbcTemplate;
  private @Autowired RecordSharingManager recShareMgr;
  private MockServletContext sc;
  private MockHttpSession mockHttpSession;
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManagerMock;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    mockRequest = new MockHttpServletRequest();
    mockResponse = new MockHttpServletResponse();
    openMocks(this);
    sc = new MockServletContext();
    sc.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    mockHttpSession = new MockHttpSession();
    controller.setServletContext(sc);
    ReflectionTestUtils.setField(
        controller, "systemPropertyPermissionManager", systemPropertyPermissionManagerMock);
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            any(User.class), eq("public_sharing")))
        .thenReturn(true);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    JdbcTestUtils.deleteFromTables(jdbcTemplate, "RecordGroupSharing");
  }

  protected UserSessionTracker getCurrentActiveUsers() {
    return (UserSessionTracker) sc.getAttribute(UserSessionTracker.USERS_KEY);
  }

  @Test
  public void testPublishedNotebookAccessDoesNotRequireLoggedInUser() throws Exception {
    GroupSetUp setup =
        setUpNotebookWith2EntriesAndGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);

    Folder f = folderMgr.getFolder(setup.notebook.getId(), setup.user, SearchDepth.INFINITE);
    for (BaseRecord notebookEntry : f.getChildrens()) {
      assertFalse(notebookEntry.isPublished());
    }
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    User anonymous = userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    logoutCurrentUser();
    ModelAndView result =
        controller.openNotebook(rgs.getPublicLink(), null, modelTss, mockRequest, mockResponse);
    assertEquals(setup.notebook, result.getModelMap().getAttribute("notebook"));
    assertEquals(
        setup.notebook.getName(), result.getModelMap().getAttribute("selectedNotebookName"));
    assertFalse((Boolean) result.getModelMap().getAttribute("canEdit"));
    assertTrue((Boolean) result.getModelMap().getAttribute("canSeeNotebook"));
    assertEquals(setup.notebook.getId(), result.getModelMap().getAttribute("selectedNotebookId"));
    assertEquals(2L, result.getModelMap().getAttribute("entryCount"));
    assertEquals(anonymous, result.getModelMap().getAttribute("user"));

    ActionPermissionsDTO dto = (ActionPermissionsDTO) result.getModelMap().getAttribute("permDTO");
    makeAssertionsOnActionPermissions(dto);
  }

  @Test
  public void testResponseHasNegativePublicAttributesInModelMap() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, true, 2);
    publishDocumentForUser(setup.user, setup.notebook.getId());
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    ModelAndView result =
        controller.openNotebook(rgs.getPublicLink(), null, modelTss, mockRequest, mockResponse);
    assertEquals("", result.getModelMap().getAttribute("publicationSummary"));
    assertEquals(false, result.getModelMap().getAttribute("publishOnInternet"));
    assertEquals(null, result.getModelMap().getAttribute("contactDetails"));
  }

  @Test
  public void testResponseHasPositivePublicAttributesInModelMap() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, true, 2);
    publishDocumentForUser(
        setup.user, setup.notebook.getId(), true, true, "publication_summary_text");
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    ModelAndView result =
        controller.openNotebook(rgs.getPublicLink(), null, modelTss, mockRequest, mockResponse);
    assertEquals(
        "publication_summary_text", result.getModelMap().getAttribute("publicationSummary"));
    assertEquals(true, result.getModelMap().getAttribute("publishOnInternet"));
    assertEquals(setup.user.getEmail(), result.getModelMap().getAttribute("contactDetails"));
  }

  @Test
  public void testUnPublishedNotebookDoesNotUnpublishEntries() throws Exception {
    GroupSetUp setup =
        setUpNotebookWith2EntriesAndGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    RecordGroupSharing noteBookRgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    Folder f = folderMgr.getFolder(setup.notebook.getId(), setup.user, SearchDepth.INFINITE);
    for (BaseRecord notebookEntry : f.getChildrens()) {
      publishDocumentForUser(setup.user, notebookEntry.getId());
    }
    f = folderMgr.getFolder(setup.notebook.getId(), setup.user, SearchDepth.INFINITE);
    for (BaseRecord notebookEntry : f.getChildrens()) {
      assertTrue(notebookEntry.isPublished());
    }
    unPublishDocumentForUser(setup.user, noteBookRgs.getId());
    f = folderMgr.getFolder(setup.notebook.getId(), setup.user, SearchDepth.INFINITE);
    for (BaseRecord notebookEntry : f.getChildrens()) {
      assertTrue(notebookEntry.isPublished());
    }
  }

  @Test
  public void testSharingLinkNotFoundException() throws Exception {
    setUpNotebookWith2EntriesAndGroupAndPublishRecordWithPublisherHavingRole(
        "standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    assertThrows(
        PublicLinkNotFoundException.class,
        () -> {
          controller.openNotebook("randomstuffnotalink", null, modelTss, mockRequest, mockResponse);
        });
  }

  @Test
  public void testSharingLinkNotFoundExceptionTakesPrecendenceOverPublicViewingDisabled()
      throws Exception {
    setUpNotebookWith2EntriesAndGroupAndPublishRecordWithPublisherHavingRole(
        "standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            any(User.class), eq("public_sharing")))
        .thenReturn(false);
    assertThrows(
        PublicLinkNotFoundException.class,
        () -> {
          controller.openNotebook("randomstuffnotalink", null, modelTss, mockRequest, mockResponse);
        });
  }

  @Test
  public void testPublicViewingDisabledException() throws Exception {
    GroupSetUp setup =
        setUpNotebookWith2EntriesAndGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            any(User.class), eq("public_sharing")))
        .thenReturn(false);
    controller.openNotebook(rgs.getPublicLink(), null, modelTss, mockRequest, mockResponse);
    String redirect = getRedirectUrl(mockResponse);
    Assertions.assertEquals("/public/publishIsDisabled", redirect);
  }

  private String getRedirectUrl(MockHttpServletResponse resp) {
    return resp.getHeaderValue("Location").toString();
  }

  private void makeAssertionsOnActionPermissions(ActionPermissionsDTO dto) {
    assertFalse(dto.isCopy());
    assertFalse(dto.isCreateNotebook());
    assertFalse(dto.isCreateFolder());
    assertFalse(dto.isCreateRecord());
    assertFalse(dto.isDeleteRecord());
    assertFalse(dto.isRename());
    assertFalse(dto.isMove());
  }
}

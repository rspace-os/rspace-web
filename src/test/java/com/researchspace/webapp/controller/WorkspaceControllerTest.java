package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.session.UserSessionTracker.USERS_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.dtos.FormMenu;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.service.AuditManager;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FormManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.PostLoginHandler;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.RecordFavoritesManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionSettings;
import com.researchspace.service.impl.RecordManagerImpl;
import com.researchspace.service.impl.UserContentUpdater;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

// there is a mixture of mocks and stubs so until we can refactor this to use just mocks
// we need a guaranteed order
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WorkspaceControllerTest extends SpringTransactionalTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  @Mock AuditManager mockAuditMgr;
  @Mock UserManager mockUserMgr;
  @Mock GroupManager grpMgr;
  @Mock FormManager formMgr;
  @Mock private RSForm mockOntologyForm;
  @Mock private UserContentUpdater userContentUpdaterMock;
  @Autowired private PaginationSettingsPreferences paginationSettingsPreferences;
  private static final String WORKSPACE_AJAX_VIEWNAME = WorkspaceController.WORKSPACE_AJAX;

  private @Autowired WorkspaceController workspaceController;
  private @Autowired RecordManager recordManager;
  @Autowired private PostLoginHandler postLoginHandler;
  @Autowired private GroupManager groupManager;
  @Autowired private SystemPropertyManager systemPropertyManager;

  private Model model;
  private ExtendedModelMap tss;

  private HttpServletRequest request;
  private HttpServletResponse response;
  private HttpSession session;

  private RecordManagerStub recordManagerStub;
  private User sysadmin1;
  private SystemPropertyValue existingPublicSharingValue;
  private SystemPropertyValue existingPublicSeoValue;
  private User anyUser;

  PaginationCriteria<BaseRecord> getAPgCriteria() {
    return PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }

  private Principal mockPrincipal = () -> "user1a";
  private Principal mockPrincipalNonPI = () -> "user2b";

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    anyUser.setId(666L);
    ReflectionTestUtils.setField(postLoginHandler, "userContentUpdater", userContentUpdaterMock);
    ReflectionTestUtils.setField(paginationSettingsPreferences, "userManager", mockUserMgr);
    UserPreference up =
        new UserPreference(Preference.DELETED_RECORDS_RESULTS_PER_PAGE, anyUser, "10");
    when(mockUserMgr.getPreferenceForUser(any(User.class), any(Preference.class))).thenReturn(up);
    tss = new ExtendedModelMap();
    model = tss;
    workspaceController.setFormManager(formMgr);
    workspaceController.setGroupManager(grpMgr);
    workspaceController.setFolderManager(new FolderManagerStub());
    recordManagerStub = new RecordManagerStub();
    session = new MockHttpSession();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    setupSystemPropertyForPublishAllowedAndSeoAllowed();
    when(formMgr.findOldestFormByName(eq(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME)))
        .thenReturn(mockOntologyForm);
    when(mockOntologyForm.getStableID()).thenReturn("mockOntologyFormStableID");
  }

  private void setupSystemPropertyForPublishAllowedAndSeoAllowed() {
    sysadmin1 = workspaceController.getUserByUsername("sysadmin1");
    existingPublicSharingValue =
        systemPropertyManager.findByName(SystemPropertyName.PUBLIC_SHARING);
    existingPublicSharingValue.setValue(HierarchicalPermission.ALLOWED.name());
    existingPublicSeoValue =
        systemPropertyManager.findByName(SystemPropertyName.PUBLICDOCS_ALLOW_SEO);
    existingPublicSeoValue.setValue(HierarchicalPermission.ALLOWED.name());
    systemPropertyManager.save(existingPublicSharingValue, sysadmin1);
    systemPropertyManager.save(existingPublicSeoValue, sysadmin1);
  }

  @After
  public void tearDown() throws Exception {
    workspaceController.setRecordManager(recordManager);
    RSpaceTestUtils.logout();
  }

  /**
   * Test if the DetailedRecordInformation contains information about the template (not the form
   * template)
   */
  @Test
  public void testStructureDocumentFromTemplateDetailedInfo()
      throws DocumentAlreadyEditedException {
    User newUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newUser);

    logoutAndLoginAs(newUser);

    String newName = "copied_record";
    Long targetFolderId = newUser.getRootFolder().getId();

    // create document
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(newUser, "text");

    // create template from document
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(anyDoc.getId(), newUser);

    RecordCopyResult fromTemplateResult =
        recordMgr.createFromTemplate(template.getId(), newName, newUser, targetFolderId);
    StructuredDocument fromTemplate = (StructuredDocument) fromTemplateResult.getUniqueCopy();

    MockServletContext sc = new MockServletContext();
    UserSessionTracker tracker = new UserSessionTracker();
    tracker.addUser(newUser.getUsername(), session);
    sc.setAttribute(UserSessionTracker.USERS_KEY, tracker);

    workspaceController.setServletContext(sc);
    AjaxReturnObject<DetailedRecordInformation> info =
        workspaceController.getRecordInformation(fromTemplate.getId(), null, null);

    assertEquals(info.getData().getTemplateName(), template.getName());
    assertEquals(info.getData().getTemplateOid(), template.getOid().getIdString());
  }

  @Test
  public void testHandleRequestModel() throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    setUpCommonMocks();

    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    assertTrue(model.containsAttribute("searchResults"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipal.getName())));
  }

  @Test
  public void shouldAddPublishAllowedAndSeoAllowedBooleanBasedOnSystemProperty()
      throws IOException {
    workspaceController.setRecordManager(recordManagerStub);
    setUpCommonMocks();
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    assertTrue((Boolean) model.getAttribute("publish_allowed"));
    assertTrue((Boolean) model.getAttribute("publicdocs_allow_seo"));
    existingPublicSharingValue.setValue("DENIED");
    existingPublicSeoValue.setValue("DENIED");
    systemPropertyManager.save(existingPublicSharingValue, sysadmin1);
    systemPropertyManager.save(existingPublicSeoValue, sysadmin1);
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    assertFalse((Boolean) model.getAttribute("publish_allowed"));
    assertFalse((Boolean) model.getAttribute("publicdocs_allow_seo"));
    verify(userContentUpdaterMock, times(2))
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipal.getName())));
  }

  @Test
  public void shouldAddPublishOwnDocumentsAndSeoTrueToModelForPIWhenGroupDisallowsPublishAndSeo()
      throws Exception {

    workspaceController.setRecordManager(recordManagerStub);
    setUpGroupWithPiNonPIAllowPublishAndSeo(false, false);
    setUpCommonMocks();
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    assertTrue((Boolean) model.getAttribute("publish_own_documents"));
    assertTrue((Boolean) model.getAttribute("publicdocs_allow_seo"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipal.getName())));
  }

  @Test
  public void
      shouldAddPublishOwnDocumentsAndSEOFalseToModelForNonPIWhenGroupDisallowsPublishANDSEO()
          throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    setUpGroupWithPiNonPIAllowPublishAndSeo(false, false);
    setUpCommonMocks();
    workspaceController.listRootFolder(
        "", model, mockPrincipalNonPI, request, session, response, new WorkspaceSettings());
    assertFalse((Boolean) model.getAttribute("publish_own_documents"));
    assertFalse((Boolean) model.getAttribute("publicdocs_allow_seo"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipalNonPI.getName())));
  }

  private void setUpGroupWithPiNonPIAllowPublishAndSeo(boolean allowPublish, boolean allowSeo) {
    User pi = workspaceController.getUserByUsername("user1a");
    User nonPI = workspaceController.getUserByUsername("user2b");
    Group aGroup = TestFactory.createAnyGroup(pi, nonPI);
    aGroup.setPublicationAllowed(allowPublish);
    aGroup.setSeoAllowed(allowPublish);
    aGroup = groupManager.saveGroup(aGroup, pi);
    aGroup = groupManager.saveGroup(aGroup, nonPI);
  }

  @Test
  public void shouldAddPublishOwnDocumentsAndSEOTrueToModelForNonPIWhenGroupAllowsPublishAndSEO()
      throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    User nonPI = userMgr.getUserByUsername("user2b", true);
    if (nonPI.getGroups().size() == 0) {
      setUpGroupWithPiNonPIAllowPublishAndSeo(true, true);
    }
    // ensure all groups set allow seo/publish to true else tests become order dependant
    for (Group g : nonPI.getGroups()) {
      g.setSeoAllowed(true);
      g.setPublicationAllowed(true);
      groupManager.saveGroup(g, nonPI);
    }
    setUpCommonMocks();
    workspaceController.listRootFolder(
        "", model, mockPrincipalNonPI, request, session, response, new WorkspaceSettings());
    assertTrue((Boolean) model.getAttribute("publish_own_documents"));
    assertTrue((Boolean) model.getAttribute("publicdocs_allow_seo"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipalNonPI.getName())));
  }

  @Test
  public void
      shouldAddPublishOwnDocumentsAndSEOFalseToModelForNonPIWhenAnyGroupForbidsPublishAndSEO()
          throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    setUpGroupWithPiNonPIAllowPublishAndSeo(true, true);
    setUpGroupWithPiNonPIAllowPublishAndSeo(false, false);
    setUpCommonMocks();
    workspaceController.listRootFolder(
        "", model, mockPrincipalNonPI, request, session, response, new WorkspaceSettings());
    assertFalse((Boolean) model.getAttribute("publish_own_documents"));
    assertFalse((Boolean) model.getAttribute("publicdocs_allow_seo"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipalNonPI.getName())));
  }

  private void setUpCommonMocks() {
    Mockito.when(grpMgr.listGroupsForUser()).thenReturn(Collections.emptySet());
    Mockito.when(formMgr.generateFormMenu(Mockito.any(User.class))).thenReturn(new FormMenu());
  }

  @Test
  public void testValidateFolderName() throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    String tooLong = getRandomName(256);
    assertEquals(
        WorkspaceController.NAME_MAX_LENGTH, workspaceController.abbreviateName(tooLong).length());
  }

  @Test
  public void testHandleRequestLongIntegerModel() throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    Long recordId = getAValidRecordId();
    assertFalse(model.containsAttribute("searchResults"));
    workspaceController.listWorkspaceFolderById(
        recordId, new WorkspaceSettings(), "", model, request, session, response, mockPrincipal);
    assertTrue(model.containsAttribute("searchResults"));
    assertTrue((Boolean) model.getAttribute("publish_allowed"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipal.getName())));
  }

  @SuppressWarnings("unchecked")
  private Long getAValidRecordId() throws Exception {
    setUpCommonMocks();
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    assertTrue((Boolean) model.getAttribute("publish_allowed"));
    ISearchResults<Record> res = (ISearchResults<Record>) model.asMap().get("searchResults");
    Long recordId = res.getResults().get(0).getId();
    tss.clear();
    if (recordId == null) {
      recordId = 1L;
    }
    return recordId;
  }

  @Test
  public void testViewStringIntegerModel() throws Exception {
    workspaceController.setRecordManager(recordManagerStub);
    workspaceController.setFolderManager(new FolderManagerStub());
    Long recordId = getAValidRecordId();
    assertFalse(model.containsAttribute("searchResults"));
    WorkspaceSettings settings = new WorkspaceSettings();
    workspaceController.view(recordId, settings, model, mockPrincipal, session);
    assertTrue((Boolean) model.getAttribute("publish_allowed"));
    assertTrue(model.containsAttribute("searchResults"));
    verify(userContentUpdaterMock)
        .doUserContentUpdates(eq(userMgr.getUserByUsername(mockPrincipal.getName())));
  }

  @Test
  public void deleteActionRemovesOnlyUnlockedRecord() throws Exception {

    final AuditTrailService mockAuditService = Mockito.mock(AuditTrailService.class);
    final RecordDeletionManager mockDeleteManager = Mockito.mock(RecordDeletionManager.class);
    final RecordFavoritesManager mockFavoritesManager = Mockito.mock(RecordFavoritesManager.class);

    AuditTrailService orgAuditService = workspaceController.auditService;
    workspaceController.setAuditService(mockAuditService);
    workspaceController.setDeletionManager(mockDeleteManager);
    workspaceController.setFavoritesManager(mockFavoritesManager);
    workspaceController.setGroupManager(grpMgr);
    UserSessionTracker tracker = anySessionTracker();
    try {
      workspaceController.setRecordManager(recordManagerStub);
      MockServletContext sc = new MockServletContext();
      sc.setAttribute(USERS_KEY, tracker);
      workspaceController.setServletContext(sc);
      final Long anyId = 1L;
      final Long anyId2 = 2L;
      setUpCommonMocks();
      when(mockDeleteManager.deleteRecord(eq(anyId), eq(anyId), Mockito.any(User.class)))
          .thenReturn(new CompositeRecordOperationResult(null, null, null));

      final WorkspaceSettings srchInput = new WorkspaceSettings();
      srchInput.setParentFolderId(anyId);
      workspaceController.delete(
          new Long[] {anyId},
          null,
          model,
          srchInput,
          new MockHttpServletRequest(),
          mockPrincipal,
          session,
          response);
      verify(mockDeleteManager)
          .doDeletion(
              Mockito.any(Long[].class),
              Mockito.any(Supplier.class),
              Mockito.any(DeletionSettings.class),
              Mockito.any(User.class),
              Mockito.any(ProgressMonitor.class));
      assertTrue((Boolean) model.getAttribute("publish_allowed"));

    } finally {
      workspaceController.setRecordManager(recordManager);
      workspaceController.setAuditService(orgAuditService);
    }
  }

  @Test
  public void testMove() throws Exception {
    try {
      workspaceController.setRecordManager(recordManagerStub);
      setUpCommonMocks();
      WorkspaceSettings settings = new WorkspaceSettings();
      settings.setParentFolderId(2L);
      ModelAndView mav = basicMove(new Long[] {1L}, "/", settings);
      assertTrue(model.containsAttribute("recordId"));
      assertEquals(WORKSPACE_AJAX_VIEWNAME, mav.getViewName());
      assertTrue((Boolean) model.getAttribute("publish_allowed"));
    } finally {
      workspaceController.setRecordManager(new RecordManagerImpl());
    }
  }

  @SuppressWarnings("unused")
  @Test(expected = Exception.class)
  public void testMoveChildThrowsISExceptionForEmptyList() throws Exception {
    try {
      workspaceController.setRecordManager(recordManagerStub);
      WorkspaceSettings settings = new WorkspaceSettings();
      settings.setParentFolderId(2L);
      ModelAndView mav = basicMove(new Long[] {}, "/", settings);
    } finally {
      workspaceController.setRecordManager(recordManager);
    }
  }

  private ModelAndView basicMove(Long[] toMoveIds, String targetId, WorkspaceSettings settings)
      throws IOException, ParseException {
    return workspaceController.move(
        toMoveIds, targetId, null, settings, model, mockPrincipal, request, session, response);
  }

  @Test
  public void testHandleExceptionsForAjaxError() {
    MockHttpServletRequest mockRequest = new MockHttpServletRequest();
    MockHttpServletResponse mockResponse = new MockHttpServletResponse();

    setRequestAsAjaxRequest(mockRequest);
    Exception e = new Exception("Test message");
    ModelAndView mav = workspaceController.handleExceptions(mockRequest, mockResponse, e);
    assertEquals(ControllerExceptionHandler.AJAX_ERROR_VIEW_NAME, mav.getViewName());
  }

  @Test
  public void viewDeletedDocuments() {
    workspaceController.setUserManager(mockUserMgr);
    when(mockUserMgr.getUserByUsername(any())).thenReturn(anyUser);
    when(mockUserMgr.get(eq(666L))).thenReturn(anyUser);
    when(mockUserMgr.getUserByUsername(any(), eq(true))).thenReturn(anyUser);

    final AuditedRecord ar = new AuditedRecord();
    final ISearchResults<AuditedRecord> deleted =
        new SearchResultsImpl<AuditedRecord>(toList(ar), 0, 1L);
    final PaginationCriteria<AuditedRecord> pgcrit =
        PaginationCriteria.createDefaultForClass(AuditedRecord.class);
    workspaceController.setAuditManager(mockAuditMgr);
    when(mockAuditMgr.getDeletedDocuments(anyUser, "", pgcrit)).thenReturn(deleted);

    ModelAndView mav =
        workspaceController.listDeletedDocuments(
            new MockPrincipal(anyUser.getUsername()), "", pgcrit, model);
    assertEquals(WorkspaceController.WORKSPACE_DELETED_HISTORY_VIEW, mav.getViewName());
    assertNotNull(mav.getModel().get("deleted"));
    assertTrue((Boolean) model.getAttribute("publish_allowed"));
  }

  @Test
  public void testCopyDoesNotRenameToInvalidLEngth_RSPAC1558() {
    IntStream.range(250, 260)
        .forEach(
            i -> {
              String[] names = new String[1];
              names[0] = getRandomName(i);
              workspaceController.fitNameToMaxSize(names, 0);
              assertTrue(
                  "Failed for names string of length " + i,
                  names[0].length() <= BaseRecord.DEFAULT_VARCHAR_LENGTH);
            });
  }

  @Test
  public void testHandleExceptionsForNonAjaxError() {
    MockHttpServletRequest mockRequest = new MockHttpServletRequest();
    MockHttpServletResponse mockResponse = new MockHttpServletResponse();

    Exception e = new Exception("Test message");
    ModelAndView mav = workspaceController.handleExceptions(mockRequest, mockResponse, e);
    assertEquals(ControllerExceptionHandler.NON_AJAX_ERROR_VIEW_NAME, mav.getViewName());
  }

  private void setRequestAsAjaxRequest(MockHttpServletRequest mockRequest) {
    mockRequest.addHeader(RequestUtil.AJAX_REQUEST_HEADER_NAME, RequestUtil.AJAX_REQUEST_TYPE);
  }
}

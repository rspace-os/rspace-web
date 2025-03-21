package com.researchspace.webapp.controller;

import static com.axiope.search.SearchConstants.ALL_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.RECORDS_SEARCH_OPTION;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.SearchTestUtils.createSimpleNameSearchCfg;
import static com.researchspace.testutils.matchers.TotalSearchResults.totalSearchResults;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiope.search.SearchConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.model.Community;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.SignatureStatus;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.CreateAuditEvent;
import com.researchspace.model.audittrail.DuplicateAuditEvent;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.dtos.RecordTagData;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.dtos.WorkspaceSettings.WorkspaceViewMode;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.Breadcrumb.BreadcrumbElement;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.PostLoginHandler;
import com.researchspace.service.RecordFavoritesManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.impl.UserContentUpdater;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestGroup;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.ModelAndView;

@WebAppConfiguration
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
// 2 example import files for test testListFoldersWhenFirstSession_RSPAC1789
@TestPropertySource(
    properties = {
      "example.import.files=classpath:/deployments/dev/TestExampleImport-RSPAC-1789.zip,classpath:/deployments/dev/TestExampleImport-RSPAC-1789b.zip"
    })
public class WorkspaceControllerMVCIT extends MVCTestBase {
  private static boolean initialisedForms = false;
  private static final String VIEWABLE_ITEMS_FILTER = "viewableItemsFilter";
  private static final String FAVORITES_FILTER = "favoritesFilter";
  private static final String SHARED_FILTER = "sharedFilter";

  @Autowired
  @Qualifier("customFormAppInitialiser")
  private IApplicationInitialisor customFormAppInitialiser;

  private @Autowired RecordFavoritesManager favMgr;
  private @Autowired WorkspaceController workspaceController;
  private @Autowired RecordManager recordManager;
  private @Autowired AuditManager auditMgr;
  private @Autowired RecordSharingManager recShareMgr;
  @Mock private UserContentUpdater userContentUpdaterMock;
  @Autowired private PostLoginHandler postLoginHandler;
  @Mock private AuditTrailService auditService;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockHttpSession session;

  private ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    openMocks(this);
    ReflectionTestUtils.setField(postLoginHandler, "userContentUpdater", userContentUpdaterMock);
    MockServletContext mockServletCtxt = new MockServletContext();
    mockServletCtxt.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    workspaceController.setServletContext(mockServletCtxt);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    session = new MockHttpSession();
    workspaceController.setAuditService(auditService);
    // this is hacky but 1) CustomFormAppInitialiser onAppStartup doesnt get called in tests even
    // though the bean is present
    // and 2) calling onAppStartup() in this test on the CustomFormAppInitialiser bean fails due to
    // GlobalInitSysadminAuthenticationToken
    // being missing.
    if (initialisedForms == false) {
      initialisedForms = true;
      Object realBean = AopProxyUtils.getSingletonTarget(customFormAppInitialiser);
      RSForm ontologiesForm =
          ((CustomFormAppInitialiser) realBean)
              .createOntologiesForm(
                  CustomFormAppInitialiser.ONTOLOGY_FORM_NAME,
                  CustomFormAppInitialiser.ONTOLOGY_DESCRIPTION,
                  userMgr.getUserByUsername("sysadmin1"));
      formMgr.save(ontologiesForm);
    }
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testListFoldersWhenFirstSession_RSPAC1789() throws IOException {
    User anyUser = createAndSaveUser(getRandomName(10));
    logoutAndLoginAs(anyUser);
    session.setAttribute(SessionAttributeUtils.FIRST_LOGIN, Boolean.TRUE);
    mockPrincipal = anyUser::getUsername;
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());

    Folder exampleFolder = getExampleFolderForUser(anyUser);
    workspaceController.listWorkspaceFolderById(
        exampleFolder.getId(),
        new WorkspaceSettings(),
        "",
        model,
        request,
        session,
        response,
        mockPrincipal);
    ISearchResults<BaseRecord> res2 = (ISearchResults) modelTss.get("searchResults");
    final int EXPECTED_EXAMPLE_COUNT = 2;
    assertEquals(EXPECTED_EXAMPLE_COUNT, res2.getTotalHits().intValue());
    verify(userContentUpdaterMock).doUserContentUpdates(eq(anyUser));
  }

  private Folder getExampleFolderForUser(User anyUser) throws IOException {
    ISearchResults<BaseRecord> res2 =
        searchMgr.searchWorkspaceRecords(createSimpleNameSearchCfg("Examples"), anyUser);

    BaseRecord exampleFolder =
        res2.getResults().stream().filter(f -> f.getName().equals("Examples")).findFirst().get();
    return (Folder) exampleFolder;
  }

  @Test
  public void PIsCanBeAddedToEachOthersGroups() throws Exception {
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    // setup a shared group folder
    grpMgr.createSharedCommunalGroupFolders(setup.group.getId(), piUser.getUsername());

    logoutAndLoginAs(setup.user);
    // creating a reciprocal group not allowed.
    Group other =
        createGroupForUsers(setup.user, setup.user.getUsername(), null, piUser, setup.user);
    grpMgr.createSharedCommunalGroupFolders(other.getId(), setup.user.getUsername());
    doInTransaction(
        () -> {
          Folder userRoot = folderDao.getRootRecordForUser(piUser);
          Folder otherRoot = folderDao.getRootRecordForUser(setup.user);

          assertFalse(
              folderDao.getLabGroupFolderForUser(setup.user).getChildrens().contains(userRoot));
          assertFalse(
              folderDao.getLabGroupFolderForUser(piUser).getChildrens().contains(otherRoot));
        });
  }

  private long getRecordCountInLabGrpFolder(User user) throws Exception {

    Folder labGrFlder = doInTransaction(() -> folderDao.getLabGroupFolderForUser(user));

    // need this as we have > 10 now
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setResultsPerPage(20);
    ISearchResults<BaseRecord> rootListing =
        recordMgr.listFolderRecords(labGrFlder.getId(), pgCrit);
    return rootListing.getTotalHits();
  }

  @Test
  public void addingOrRemovingUserFromGroupUpdatesFolderAccess() throws Exception {
    // 'user' is a pi here, 'other' has a pi role but not in this group
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    logoutAndLoginAs(piUser);

    // setup a shared group folder
    grpMgr.createSharedCommunalGroupFolders(setup.group.getId(), piUser.getUsername());
    // Group folder. Because 'other' user is a PI, their folder does not appear (RSPAC-373)
    assertEquals(1, getRecordCountInLabGrpFolder(piUser));
    logoutAndLoginAs(setup.user);
    assertEquals(1, getRecordCountInLabGrpFolder(setup.user));

    // now we create a 3rd user, and add him into the group
    User extra = createAndSaveUser(CoreTestUtils.getRandomName(5));
    initUser(extra);
    logoutAndLoginAs(extra);
    // initially no group folder and no access to record
    assertFalse(
        "user not in group can see the doc",
        permissionUtils.isPermitted(setup.structuredDocument, PermissionType.READ, extra));
    assertEquals(0, getRecordCountInLabGrpFolder(extra));

    // adding extra user to the group
    grpMgr.addUserToGroup(extra.getUsername(), setup.group.getId(), RoleInGroup.DEFAULT);
    permissionUtils.refreshCache();
    // new member can see the shared record, and the communal group folder
    assertTrue(permissionUtils.isPermitted(setup.structuredDocument, PermissionType.READ, extra));

    assertEquals(1, getRecordCountInLabGrpFolder(extra));

    // let's also add a lab admin with 'view all' permission
    logoutAndLoginAs(piUser);
    User labAdmin = createAndSaveUser(CoreTestUtils.getRandomName(5) + "LabAdmin");
    initUser(labAdmin);
    grpMgr.addUserToGroup(labAdmin.getUsername(), setup.group.getId(), RoleInGroup.RS_LAB_ADMIN);
    labAdmin =
        grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), piUser, setup.group.getId(), true);

    // group pi should see two member folders + group folder
    assertEquals(3, getRecordCountInLabGrpFolder(piUser));
    // lab admin should see one member folder + group folder
    logoutAndLoginAs(labAdmin);
    assertEquals(2, getRecordCountInLabGrpFolder(labAdmin));
    // other user, even though has a PI role, shouldn't see user folders, as is just a member in
    // this group
    logoutAndLoginAs(setup.user);
    assertEquals(1, getRecordCountInLabGrpFolder(setup.user));

    // now we'll remove 'extra' user
    User pi = userMgr.getUserByUsername(piUser.getUsername(), true);
    logoutAndLoginAs(pi);
    grpMgr.removeUserFromGroup(extra.getUsername(), setup.group.getId(), pi);
    // extra can no longer see group records/folders
    logoutAndLoginAs(extra);
    assertFalse(permissionUtils.isPermitted(setup.structuredDocument, PermissionType.READ, extra));
    assertEquals(0, getRecordCountInLabGrpFolder(extra));
    // PI and LA can't see ex-member's folder either
    logoutAndLoginAs(piUser);
    assertEquals(2, getRecordCountInLabGrpFolder(piUser));
    logoutAndLoginAs(labAdmin);
    assertEquals(1, getRecordCountInLabGrpFolder(labAdmin));
  }

  @Test
  public void onlyPIcanDeleteContentThatIsntTheirsFromGroupSharedFolderTest() throws Exception {

    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    final User extra = createAndSaveUser(CoreTestUtils.getRandomName(10));
    initUser(extra);

    grpMgr.addUserToGroup(extra.getUsername(), setup.group.getId(), RoleInGroup.DEFAULT);

    // now we have completed the set up----3 users in group
    // need to reload here as group in setup is stale and
    // does not have communal folderID
    Long grpFlderId = grpMgr.getGroup(setup.group.getId()).getCommunalGroupFolderId();

    // let 'other' user create a doc and share a document in the group
    // folder by sharing
    logoutAndLoginAs(setup.user);
    final StructuredDocument sharedRecord =
        createBasicDocumentInRootFolderWithText(setup.user, "any");

    sharingMgr.shareRecord(
        setup.user,
        sharedRecord.getId(),
        new ShareConfigElement[] {new ShareConfigElement(setup.group.getId(), "read")});

    logoutAndLoginAs(extra);
    // extra can't delete shared record - he's not the owner of the record, or the PI
    // 2 shared documents
    assertEquals(2, getRecordCountInFolderForUser(grpFlderId));
    assertFalse(permissionUtils.isPermitted(sharedRecord, PermissionType.DELETE, extra));
    // attempting to delete has no effect - permissions block this
    assertAuthorisationExceptionThrown(
        () -> recordDeletionMgr.deleteRecord(null, sharedRecord.getId(), extra));

    // still 2 shared records
    assertEquals(2, getRecordCountInFolderForUser(grpFlderId));

    piUser = userMgr.get(piUser.getId()); // refresh user
    logoutAndLoginAs(piUser);

    // but user can - he is PI??
    assertFalse(permissionUtils.isPermitted(sharedRecord, PermissionType.DELETE, piUser));
    // attempting to delete does actually delete
    recordDeletionMgr.deleteRecord(grpFlderId, sharedRecord.getId(), piUser);
    assertEquals(1, getRecordCountInFolderForUser(grpFlderId));
    // these permissions are all set up at group creation time.
  }

  @Test
  public void simpleDeleteRecordTest() throws Exception {
    final GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    WorkspaceSettings settings = new WorkspaceSettings();
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    long initRecordCount = getNumberOfRecordsInRootFolder(settings);

    // other user attempts to delete - this should fail
    RSpaceTestUtils.logoutCurrUserAndLoginAs(setup.user.getUsername(), TESTPASSWD);
    final WorkspaceSettings srchInput = new WorkspaceSettings();
    srchInput.setParentFolderId(root.getId());
    assertAuthorisationExceptionThrown(
        () ->
            workspaceController.delete(
                new Long[] {setup.structuredDocument.getId()},
                null,
                model,
                srchInput,
                request,
                new MockPrincipal(setup.user.getUsername()),
                session,
                response));

    // now relog in as original user; their doc is still there
    RSpaceTestUtils.logoutCurrUserAndLoginAs(piUser.getUsername(), TESTPASSWD);
    assertEquals(initRecordCount, getNumberOfRecordsInRootFolder(settings));

    // now delete for real.
    workspaceController.delete(
        new Long[] {setup.structuredDocument.getId()},
        null,
        model,
        srchInput,
        request,
        mockPrincipal,
        session,
        response);
    assertEquals(initRecordCount - 1, getNumberOfRecordsInRootFolder(settings));
  }

  /**
   * NOT a UNIT test - run this in debug mode with a breakpoint at the final line of code System.out
   * You can then run rspace log in as the random user using "testpass" as password You can then
   * navigate to deleted items page and check it peforms with 1000 items.
   *
   * @throws Exception
   */
  @Test
  @Ignore
  public void testBulkDeletionPerformance() throws Exception {
    final GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    System.out.println(piUser.getUsername());
    piUser.getPassword();
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());

    final WorkspaceSettings srchInput = new WorkspaceSettings();
    srchInput.setParentFolderId(root.getId());
    System.out.println(piUser.getUsername());
    // now delete for real.
    workspaceController.delete(
        new Long[] {setup.structuredDocument.getId()},
        null,
        model,
        srchInput,
        request,
        mockPrincipal,
        session,
        response);
    RSForm form = createAnyForm(piUser);
    for (int i = 0; i < 1000; i++) { // will take several minutes to run
      StructuredDocument doc = createDocumentInFolder(root, form, piUser);
      workspaceController.delete(
          new Long[] {doc.getId()},
          null,
          model,
          srchInput,
          request,
          mockPrincipal,
          session,
          response);
    }
    System.out.println(piUser.getUsername());
  }

  @Test
  public void deleteFolderWithAlreadyDeletedItems_RSPAC1637() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    long initialCount = getRecordCountInRootFolderForUser(anyUser);
    Folder root = getRootFolderForUser(anyUser);
    Folder parent = createSubFolder(root, "parent", anyUser);
    Folder child = createSubFolder(parent, "child", anyUser);
    // assert parent added ok
    assertEquals(initialCount + 1, getRecordCountInRootFolderForUser(anyUser));
    final WorkspaceSettings srchInput = new WorkspaceSettings();
    srchInput.setParentFolderId(parent.getId());

    // delete child
    workspaceController.delete(
        new Long[] {child.getId()},
        null,
        model,
        srchInput,
        request,
        new MockPrincipal(anyUser.getUsername()),
        session,
        response);

    // parent should still be able to be deleted.
    srchInput.setParentFolderId(parent.getId());
    workspaceController.delete(
        new Long[] {parent.getId()},
        null,
        model,
        new WorkspaceSettings(),
        request,
        new MockPrincipal(anyUser.getUsername()),
        session,
        response);
    // back to initial count
    assertEquals(initialCount, getRecordCountInRootFolderForUser(anyUser));
  }

  private long getNumberOfRecordsInRootFolder(WorkspaceSettings settings) throws Exception {
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, settings);
    ISearchResults res2 = (ISearchResults) modelTss.get("searchResults");
    return res2.getResults().size();
  }

  @Test
  public void testPdfJsoup() throws Exception {
    String htmlStr = "<p>aaaaaa <img id=\"1\" class=\"commentIcon\"  /> this is image </p>";
    org.jsoup.nodes.Document dc = Jsoup.parse(htmlStr);
    Elements cms = dc.getElementsByClass("commentIcon");
    for (org.jsoup.nodes.Element cm : cms) {
      String cmmId = cm.attr("id");
      int cid = Integer.parseInt(cmmId);
      assertTrue(cid == 1);
      String idc = "[1]";
      cm.replaceWith(new org.jsoup.nodes.Element(Tag.valueOf("span"), "").text(idc));
    }
    String str1 = dc.toString();
    int idx = str1.indexOf("commentIcon");
    assertTrue(idx <= 0);
  }

  @Test
  public void shareRecordTest() throws Exception {

    GroupSetUp gs = setUpDocumentGroupForPIUserAndShareRecord(false);

    ShareConfigCommand shareConfigCommand = new ShareConfigCommand();
    shareConfigCommand.setIdsToShare(new Long[] {gs.structuredDocument.getId()});
    ShareConfigElement gcse = new ShareConfigElement(gs.group.getId(), "read");
    shareConfigCommand.setValues(new ShareConfigElement[] {gcse});

    RSpaceTestUtils.logoutCurrUserAndLoginAs(piUser.getUsername(), TESTPASSWD);
    AjaxReturnObject<SharingResult> result =
        workspaceController.shareRecord(
            shareConfigCommand, new MockPrincipal(piUser.getUsername()));
    assertNotNull(result.getData());
    assertEquals(1, result.getData().getSharedIds().size());
    assertFalse(result.getErrorMsg().hasErrorMessages());
  }

  @Test
  public void publishRecordTestShouldAddShareConfigWithAnonymousUser() throws Exception {

    GroupSetUp gs = setUpDocumentGroupForPIUserAndShareRecord(false);
    assertFalse(userHasRecordSharedWithAnonymous(piUser, "summary", true));

    ShareConfigCommand shareConfigCommand = new ShareConfigCommand();
    ShareConfigElement existingAnonymousShareConfig = new ShareConfigElement();
    existingAnonymousShareConfig.setPublicationSummary("summary");
    existingAnonymousShareConfig.setDisplayContactDetails(true);
    shareConfigCommand.setValues(new ShareConfigElement[] {existingAnonymousShareConfig});
    shareConfigCommand.setIdsToShare(new Long[] {gs.structuredDocument.getId()});
    shareConfigCommand.setPublish(true);
    RSpaceTestUtils.logoutCurrUserAndLoginAs(piUser.getUsername(), TESTPASSWD);
    AjaxReturnObject<SharingResult> result =
        workspaceController.shareRecord(
            shareConfigCommand, new MockPrincipal(piUser.getUsername()));
    assertNotNull(result.getData());
    assertEquals(1, result.getData().getSharedIds().size());
    assertEquals(1, result.getData().getPublicLinks().size());
    assertTrue(
        result
            .getData()
            .getPublicLinks()
            .get(0)
            .startsWith("Untitled document_&_&_/public/publishedView/document/"));
    assertFalse(result.getErrorMsg().hasErrorMessages());
    boolean sharedWithAnonymous = userHasRecordSharedWithAnonymous(piUser, "summary", true);
    if (!sharedWithAnonymous) {
      fail("publication must share record with anonymous user");
    }
  }

  private boolean userHasRecordSharedWithAnonymous(
      User user, String publicationSummary, boolean displayContactDetails) {
    List<RecordGroupSharing> sharedByPI = recShareMgr.getSharedRecordsForUser(user);
    boolean sharedWithAnonymous = false;
    for (RecordGroupSharing rgs : sharedByPI) {
      if (rgs.getSharee().asUser().getUsername().equals(RecordGroupSharing.ANONYMOUS_USER)
          && rgs.getPublicationSummary().equals(publicationSummary)
          && rgs.isDisplayContactDetails() == displayContactDetails) {
        sharedWithAnonymous = true;
        break;
      }
    }
    return sharedWithAnonymous;
  }

  @Test
  public void testSearchTextSimple() throws Exception {
    // ensure a unique string to search for
    final String[] searchTerm = {CoreTestUtils.getRandomName(25)};
    String[] option = {SearchConstants.FULL_TEXT_SEARCH_OPTION};

    String[] noResult = {"TTTTTT"};
    StructuredDocument dcx = setUpLoginAsPIUserAndCreateADocument();

    /* This is a basic document with a single text field */
    dcx.getFields().get(0).setFieldData("xxx " + searchTerm[0] + " xxx  at");
    recordMgr.save(dcx, piUser);

    WorkspaceSettings filters = new WorkspaceSettings();
    filters.setParentFolderId(-1L);
    /* Basic search */
    doSearch(option, searchTerm, filters);

    ISearchResults<BaseRecord> rcds = getSearchResultsFromModel();
    assertEquals(1, rcds.getHits().intValue());
    assertEquals(1, rcds.getTotalHits().intValue());
    assertEquals(1, rcds.getResults().size());

    /**
     * Now we search empty folder, we get the same results because we are using a global search
     * where we don't filter the results by parent folder.
     */
    clearModel();
    // this was modified in previous search
    option = new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION};
    Folder emptyFolder = createSubFolder(dcx.getParent(), "F1", piUser);
    filters.setParentFolderId(emptyFolder.getId());

    doSearch(option, searchTerm, filters);
    ISearchResults<BaseRecord> rcds3 = getSearchResultsFromModel();
    assertEquals(1, rcds3.getHits().intValue());
    assertEquals(1, rcds3.getTotalHits().intValue());
    assertEquals(1, rcds3.getResults().size());

    clearModel();
    option = new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION};
    /** This search should have no hits */
    filters.setParentFolderId(-1L);
    doSearch(option, noResult, filters);

    assertTrue(getSearchResultsFromModel().getHits() == 0);

    clearModel();
    option = new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION};

    /**
     * now let's log in as another user, this should retrieve no hits as they don't have permission
     * to access the record
     */
    User other = createAndSaveUser(CoreTestUtils.getRandomName(10));
    initUser(other);
    logoutAndLoginAs(other);

    workspaceController.searchAjax(option, searchTerm, filters, model, request, session, response);
    // these results are as expected
    assertTrue(getSearchResultsFromModel().getHits() == 0);
    RSpaceTestUtils.logout();
  }

  @Test
  public void testSearchTextWithPagination() throws Exception {
    // Ensure a unique string to search for that won't occur in any default content.
    final String[] searchTerm = {CoreTestUtils.getRandomName(25)};
    final String[] option = {SearchConstants.FULL_TEXT_SEARCH_OPTION};
    final String[] option2 = {SearchConstants.TAG_SEARCH_OPTION};

    StructuredDocument dcx = setUpLoginAsPIUserAndCreateADocument();
    dcx.setDocTag(searchTerm[0]);
    recordMgr.save(dcx, piUser);

    // This should make 16 documents in total, all owned by 'user'
    createNDocumentsInFolderForUserFor(15, piUser, dcx.getParent(), dcx.getForm());
    // This should be 16 but may vary depending on preceding tests
    openTransaction();
    int numRecordsWithText =
        addTextToAllDocsInFolder(folderMgr.getRootFolderForUser(piUser), searchTerm[0]);
    commitTransaction();
    log.info("There are " + numRecordsWithText + " records with the search term in  root folder");

    WorkspaceSettings filters = new WorkspaceSettings();
    filters.setParentFolderId(-1L);
    // This should get 16 total hits
    doSearch(option, searchTerm, filters);

    ISearchResults<BaseRecord> rcds2 = getSearchResultsFromModel();
    assertEquals(numRecordsWithText, rcds2.getTotalHits().intValue());
    assertEquals(10, rcds2.getHits().intValue());
    assertEquals(16, rcds2.getTotalHits().intValue());

    // For Tag option testing, it should get just 1 element.
    // The first document (dcx) created where we set a tag.

    doSearch(option2, searchTerm, filters);

    ISearchResults<BaseRecord> rcds3 = getSearchResultsFromModel();
    assertEquals(1, rcds3.getTotalHits().intValue());
    assertEquals(1, rcds3.getHits().intValue());
    assertEquals(1, rcds3.getResults().size());
  }

  @Test
  public void testMustAdvancedSearch() throws Exception {

    final String[] searchTerms = {CoreTestUtils.getRandomName(25), piUser.getUsername()};
    String[] searchOptions = {
      SearchConstants.FULL_TEXT_SEARCH_OPTION, SearchConstants.OWNER_SEARCH_OPTION
    };

    String[] noTextSearchTerms = {"TTTTTT", piUser.getUsername()};
    StructuredDocument dcx = setUpLoginAsPIUserAndCreateADocument();

    /* This is a basic document with a single text field */
    dcx.getFields().get(0).setFieldData("xxx " + searchTerms[0] + " xxx  at");
    recordMgr.save(dcx, piUser);

    WorkspaceSettings filters = new WorkspaceSettings();
    filters.setParentFolderId(-1L);
    filters.setAdvancedSearch(true);

    doSearch(searchOptions, searchTerms, filters);
    ISearchResults<BaseRecord> rcds = getSearchResultsFromModel();
    assertEquals(1, rcds.getHits().intValue());
    assertEquals(1, rcds.getTotalHits().intValue());
    assertEquals(1, rcds.getResults().size());

    clearModel();
    /** This search should have no hits */
    doSearch(searchOptions, noTextSearchTerms, filters);
    // searchResults attribute is null if there are no hits
    // assertNull(getSearchResultsFromModel());
    assertTrue(getSearchResultsFromModel().getHits() == 0);
  }

  @Test
  public void testShouldAdvancedSearch() throws Exception {
    final int EXPECTED_RESULTS = 10;
    final String[] searchTerms = {CoreTestUtils.getRandomName(25), piUser.getUsername()};
    String[] searchOptions = {
      SearchConstants.FULL_TEXT_SEARCH_OPTION, SearchConstants.OWNER_SEARCH_OPTION
    };

    String[] noTextSearchTerms = {"TTTTTT", piUser.getUsername()};
    StructuredDocument dcx = setUpLoginAsPIUserAndCreateADocument();
    /* This is a basic document with a single text field */
    dcx.getFields().get(0).setFieldData("xxx " + searchTerms[0] + " xxx  at");
    recordMgr.save(dcx, piUser);

    WorkspaceSettings filters = new WorkspaceSettings();
    filters.setParentFolderId(-1L);
    filters.setAdvancedSearch(true);
    filters.setOperator(SearchOperator.OR);
    doSearch(searchOptions, searchTerms, filters);
    ISearchResults<BaseRecord> rcds = getSearchResultsFromModel();
    assertEquals(EXPECTED_RESULTS, rcds.getHits().intValue());
    assertEquals(EXPECTED_RESULTS, rcds.getTotalHits().intValue());
    assertEquals(EXPECTED_RESULTS, rcds.getResults().size());

    clearModel();

    /** This search should have no difference because we are using an "OR" operator between term. */
    workspaceController.searchAjax(
        searchOptions, noTextSearchTerms, filters, model, request, session, response);

    rcds = getSearchResultsFromModel();
    assertEquals(EXPECTED_RESULTS, rcds.getHits().intValue());
    assertEquals(EXPECTED_RESULTS, rcds.getTotalHits().intValue());
    assertEquals(EXPECTED_RESULTS, rcds.getResults().size());
  }

  /**
   * adds text to every record in the specified folder, and returns number of records that this text
   * was added to. this is the benchmark
   */
  private int addTextToAllDocsInFolder(Folder root, String SEARCHTEXT) throws Exception {
    int count = 0;
    for (RecordToFolder r2f : root.getChildren()) {
      if (r2f.getRecord().isStructuredDocument()) {
        StructuredDocument sd =
            recordMgr.getRecordWithFields(r2f.getRecord().getId(), piUser).asStrucDoc();
        sd.getFields().get(0).setFieldData(SEARCHTEXT);
        recordMgr.save(sd, piUser);
        count++;
      }
    }
    return count;
  }

  // clears all model attributes
  private void clearModel() {
    modelTss.clear();
  }

  private ISearchResults<BaseRecord> getSearchResultsFromModel() {
    return (ISearchResults<BaseRecord>) model.asMap().get("searchResults");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testSearchText() throws Exception {
    final String[] option = {SearchConstants.FULL_TEXT_SEARCH_OPTION};
    StructuredDocument dcx = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomName(10));
    initUser(other);
    String[] term = {"img"};

    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setParentFolderId(-1L);
    doSearch(option, term, settings);

    Map<String, Object> mp = model.asMap();
    String rst = (String) mp.get("empty");
    ISearchResults<BaseRecord> rcds = (ISearchResults<BaseRecord>) mp.get("searchResults");
    assertNotNull(rcds);
  }

  @Test
  public void testSearchTag() throws Exception {
    final String[] option = {SearchConstants.TAG_SEARCH_OPTION};
    User other = createAndSaveUser(getRandomName(10));
    initUser(other);
    logoutAndLoginAs(other);
    String[] term = {"img"};
    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setParentFolderId(-1L);
    ModelAndView mv = doSearch(option, term, settings);
    assertTrue(mv != null);
  }

  private ModelAndView doSearch(String[] options, String[] terms, WorkspaceSettings settings)
      throws IOException, ParseException {
    return workspaceController.searchAjax(
        options, terms, settings, model, request, session, response);
  }

  @Test
  public void testMove() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    String SUBFOLDER1 = "Subfolder1";
    String SUBFOLDER2 = "Subfolder2";
    Folder Sub1 = createSubFolder(root, SUBFOLDER1, piUser);
    Folder Sub2 = createSubFolder(root, SUBFOLDER2, piUser);

    ISearchResults<BaseRecord> rcds = listRootFolder();
    //  subfolders and SD
    final int initialCount = 8;
    assertEquals(initialCount, rcds.getTotalHits().intValue());
    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setParentFolderId(sd.getParent().getId());
    // move record into subfolder
    basicMove(new Long[] {sd.getId()}, Sub1.getId(), settings);
    workspaceController.move(
        new Long[] {sd.getId()},
        "" + Sub1.getId(),
        null,
        settings,
        model,
        mockPrincipal,
        request,
        session,
        response);

    ISearchResults<BaseRecord> rcds2 = listRootFolder();
    assertEquals(initialCount - 1, rcds2.getTotalHits().intValue());
    for (BaseRecord rc : rcds2.getResults()) {
      assertTrue(rc.isFolder());
    }

    // now we'll move subfolder 2 into subfolder1
    basicMove(new Long[] {Sub2.getId()}, Sub1.getId(), settings);
    ISearchResults<BaseRecord> rcds3 = listRootFolder();
    assertEquals(initialCount - 2, rcds3.getTotalHits().intValue());

    // and check subfolder 1 has 2 hits now - SF2 and SD1
    workspaceController.view(Sub1.getId(), settings, model, mockPrincipal, session);
    ISearchResults<BaseRecord> rcds4 = (ISearchResults<BaseRecord>) modelTss.get("searchResults");
    assertEquals(2, rcds4.getTotalHits().intValue());
  }

  private void basicMove(Long[] toMoveIds, Long targetId, WorkspaceSettings settings)
      throws IOException, ParseException {
    workspaceController.move(
        toMoveIds, "" + targetId, null, settings, model, mockPrincipal, request, session, response);
  }

  private ISearchResults<BaseRecord> listRootFolder() throws Exception {
    // check record has been moved, only 2 subfolders now
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, new WorkspaceSettings());
    ISearchResults<BaseRecord> rcds2 = (ISearchResults<BaseRecord>) modelTss.get("searchResults");
    return rcds2;
  }

  @Test
  public void testCommaSearchIsEscaped_RSPAC1483() throws Exception {
    final String[] nameWithCommas = {getRandomName(25) + "," + getRandomName(25)};
    final String[] option = {SearchConstants.NAME_SEARCH_OPTION};
    StructuredDocument usersDcx = setUpLoginAsPIUserAndCreateADocument();
    usersDcx.setName(nameWithCommas[0]);
    recordMgr.save(usersDcx, piUser);
    // if commas not escaped, get server error
    mockMvc
        .perform(
            get("/workspace/ajax/search")
                .param("options[]", option)
                .param("terms[]", nameWithCommas)
                .param("isAdvancedSearch", "false")
                .principal(mockPrincipal))
        .andExpect(status().is5xxServerError());
    // commas are escaped in client
    final String[] nameWithEscapedCommas = {nameWithCommas[0].replaceAll(",", "<<>>")};
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/search")
                    .param("options[]", option)
                    .param("terms[]", nameWithEscapedCommas)
                    .param("isAdvancedSearch", "false")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    ISearchResults<BaseRecord> res = getSearchResultsFromMvcResult(result);
    assertEquals(Long.valueOf(1), res.getTotalHits());

    // check we've got the right document
    assertEquals(nameWithCommas[0], res.getResults().get(0).getName());
  }

  @Test
  public void testSearchMVCWithSimpleFilterSearch() throws Exception {
    final String[] searchTerm = {getRandomName(25)};
    final String[] option = {SearchConstants.NAME_SEARCH_OPTION};
    StructuredDocument usersDcx = setUpLoginAsPIUserAndCreateADocument();
    usersDcx.setName(searchTerm[0]);
    recordMgr.save(usersDcx, piUser);
    // now do name search
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/search")
                    .param("options[]", option)
                    .param("terms[]", searchTerm)
                    .param("isAdvancedSearch", "false")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(
                model()
                    .attributeExists(
                        "orderByDate", "orderByName", "numRecordsPerPage", "paginationList"))
            .andReturn();

    ISearchResults<BaseRecord> res = getSearchResultsFromMvcResult(result);

    assertEquals(Long.valueOf(1), res.getTotalHits());

    // check we've got the right document
    assertEquals(searchTerm[0], res.getResults().get(0).getName());

    // now login as another user and try to access wituser's root folder id;
    // should throw auth exception
    User other = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(other);
    Long userRootFolderId = usersDcx.getParent().getId();
    MvcResult result2 =
        mockMvc
            .perform(
                get("/workspace/ajax/search")
                    .param("options[]", option)
                    .param("terms[]", searchTerm)
                    .param("recordId", userRootFolderId + "")
                    .param("isAdvancedSearch", "false")
                    .principal(new MockPrincipal(other.getUsername())))
            .andExpect(status().isOk())
            .andReturn();

    ISearchResults<BaseRecord> res2 = getSearchResultsFromMvcResult(result2);
    assertEquals(Long.valueOf(0), res2.getTotalHits());
  }

  @Test
  public void testSearchMVCWithInvalidURLReturnsErrorCode() throws Exception {
    final String[] SEARCHTERM = {CoreTestUtils.getRandomName(25)};
    String[] option = {"x2fdsfdsfds"}; // unknown search option
    mockMvc
        .perform(
            get("/workspace/ajax/search")
                .param("options[]", option)
                .param("terms[]", SEARCHTERM)
                .param("isAdvancedSearch", "false")
                .principal(mockPrincipal))
        .andExpect(status().isInternalServerError())
        .andReturn();
  }

  @Test
  public void testMVCGallerySearch() throws Exception {
    User u = createAndSaveUser(getRandomName(10));
    setUpUserWithoutCustomContent(u);
    String[] option = {SearchConstants.NAME_SEARCH_OPTION};
    InputStream is = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("testTxt.txt");
    EcatDocumentFile file = mediaMgr.saveNewDocument("testTxt.txt", is, u, null, null);
    logoutAndLoginAs(u);
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/search")
                    .param("options[]", option)
                    .param("terms[]", new String[] {"testTxt.txt"})
                    .param("isAdvancedSearch", "false")
                    .principal(new MockPrincipal(u.getUniqueName())))
            .andExpect(status().isOk())
            .andExpect(
                model()
                    .attributeExists(
                        "orderByDate", "orderByName", "numRecordsPerPage", "paginationList"))
            .andReturn();

    ISearchResults<BaseRecord> res = getSearchResultsFromMvcResult(result);
    assertEquals(Long.valueOf(1), res.getTotalHits());
  }

  @Ignore
  @Test
  public void testMVCAttachmentSearch() throws Exception {
    final int SleepTime = 50;
    User anyUser = createAndSaveUser(getRandomName(10));
    setUpUserWithoutCustomContent(anyUser);
    logoutAndLoginAs(anyUser);
    // initialiseFileIndexer();
    String[] option = {ALL_SEARCH_OPTION};
    InputStream is = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("testTxt.txt");
    EcatDocumentFile file = mediaMgr.saveNewDocument("testTxt.txt", is, anyUser, null, null);

    // Allow time to index, basic search finds attachment
    Thread.sleep(SleepTime);
    String[] terms = new String[] {"testing"};

    searchAndExpectNHits(anyUser, option, terms, 1);

    Folder rootFolder = getRootFolderForUser(anyUser);
    Folder subFolder = createSubFolder(rootFolder, "subfolder", anyUser);
    createBasicDocumentInFolder(anyUser, subFolder, "I am testing something");
    Thread.sleep(SleepTime);
    searchAndExpectNHits(anyUser, option, terms, 2);

    // search by subfolder, gallery Item should be excluded
    String[] option2 = {ALL_SEARCH_OPTION, RECORDS_SEARCH_OPTION};
    String[] terms2 = {"testing", subFolder.getGlobalIdentifier()};
    searchAndExpectNHits(anyUser, option2, terms2, 1);
  }

  private void searchAndExpectNHits(User anyUser, String[] option, String[] terms, int expectedhits)
      throws Exception {
    MvcResult result = doSearch(option, terms, anyUser, terms.length < 2);
    ISearchResults<BaseRecord> res = getSearchResultsFromMvcResult(result);
    assertEquals(Long.valueOf(expectedhits), res.getTotalHits());
  }

  private MvcResult doSearch(String[] option, String[] terms, User anyUser, boolean isAdvanced)
      throws Exception {
    return mockMvc
        .perform(
            get("/workspace/ajax/search")
                .param("options[]", option)
                .param("terms[]", terms)
                .param("isAdvancedSearch", isAdvanced + "")
                .principal(anyUser::getUniqueName))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void testSearchMVCWithSimpleFullTextSearch() throws Exception {
    final int n25 = 25;
    // set up a document
    final String[] searchTerm = {CoreTestUtils.getRandomName(n25)};
    String[] option = {SearchConstants.FULL_TEXT_SEARCH_OPTION};
    StructuredDocument dcx = setUpLoginAsPIUserAndCreateADocument();
    // this is a basic document with a single text field
    dcx.getFields().get(0).setFieldData("xxx " + searchTerm[0] + " xxx  at");
    recordMgr.save(dcx, piUser);

    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/search")
                    .param("options[]", option)
                    .param("terms[]", searchTerm)
                    .param("isAdvancedSearch", "false")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(
                model()
                    .attributeExists(
                        "orderByDate", "orderByName", "numRecordsPerPage", "paginationList"))
            .andReturn();

    ISearchResults<BaseRecord> res = getSearchResultsFromMvcResult(result);

    assertEquals(Long.valueOf(1), res.getTotalHits());
    // now lets simulate clicking on each link and resubmit to
    PaginationObject po = getOrderByDatePO(result);
    mockMvc
        .perform(get("/" + po.getLink()).principal(mockPrincipal))
        .andExpect(status().isOk())
        .andExpect(
            model()
                .attributeExists(
                    "orderByDate", "orderByName", "numRecordsPerPage", "paginationList"))
        .andReturn();
    res = getSearchResultsFromMvcResult(result);
    assertEquals(Long.valueOf(1), res.getTotalHits());
  }

  private PaginationObject getOrderByDatePO(MvcResult result) {
    return (PaginationObject) result.getModelAndView().getModelMap().get("orderByDate");
  }

  @Test
  public void testCreateFolder() throws Exception {

    User user = createAndSaveUser(getRandomName(10));
    initUser(user);
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);

    openTransaction();
    Folder rootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> orgChildren = rootFolder.getChildrens();
    commitTransaction();

    String testName = "newTestFolder";
    MvcResult result =
        this.mockMvc
            .perform(
                post("/workspace/ajax/create_folder/{recordid}", rootFolder.getId())
                    .principal(new MockPrincipal(user.getUsername()))
                    .param("folderNameField", testName))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    openTransaction();
    Folder refreshedRootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> newChildren = refreshedRootFolder.getChildrens();
    newChildren.removeAll(orgChildren);
    commitTransaction();

    assertEquals("no new child in root folder", 1, newChildren.size());
    BaseRecord newRecord = (BaseRecord) (newChildren.toArray())[0];
    assertTrue("new record should be a folder", newRecord.isFolder());
    assertFalse("new record shouldn't be a notebook", newRecord.isNotebook());
    assertEquals("created folder has different name", testName, newRecord.getName());
    assertEquals(
        "new folder has a different id than in the response",
        Long.valueOf((Integer) getJsonPathValue(result, "$.data")),
        newRecord.getId());

    RSpaceTestUtils.logout();
  }

  @Test
  public void createNestedFoldersMVC() throws Exception {
    User user = createAndSaveUser(getRandomName(10));
    initUser(user);
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);

    openTransaction();
    Folder rootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> orgChildren = rootFolder.getChildrens();
    commitTransaction();

    String NESTED_FOLDER_PATH = "a/b//c";
    // Create subfolders and check that it navigates into the newly created folder if
    // navigateIntoNewFolder is true
    MvcResult result =
        this.mockMvc
            .perform(
                post("/workspace/ajax/create_folder/{recordid}", rootFolder.getId())
                    .principal(new MockPrincipal(user.getUsername()))
                    .param("folderNameField", NESTED_FOLDER_PATH))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    List<String> EXPECTED_SUBFOLDER_NAMES = toList("a", "b", "c");
    openTransaction();
    Folder currentFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> currentFolderChildren = currentFolder.getChildrens();
    currentFolderChildren.removeAll(orgChildren);
    commitTransaction();

    // Check subfolders
    for (String expectedSubfolderName : EXPECTED_SUBFOLDER_NAMES) {
      assertEquals("no new child in folder", 1, currentFolderChildren.size());
      BaseRecord newRecord = (BaseRecord) (currentFolderChildren.toArray())[0];
      assertTrue("new record should be a folder", newRecord.isFolder());
      assertFalse("new record shouldn't be a notebook", newRecord.isNotebook());
      assertEquals("created folder has different name", expectedSubfolderName, newRecord.getName());

      openTransaction();
      currentFolder = folderDao.get(newRecord.getId());
      currentFolderChildren = currentFolder.getChildrens();
      commitTransaction();
    }

    assertEquals(
        "new folder has a different id than in the response",
        Long.valueOf((Integer) getJsonPathValue(result, "$.data")),
        currentFolder.getId());

    RSpaceTestUtils.logout();
  }

  @Test
  public void testCreateNotebookMVC() throws Exception {
    User user = createAndSaveUser(getRandomName(10));
    initUser(user);
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);

    openTransaction();
    Folder rootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> orgChildren = rootFolder.getChildrens();
    commitTransaction();

    String testName = "newTestNotebook";
    this.mockMvc
        .perform(
            post("/workspace/create_notebook/{rootid}", rootFolder.getId())
                .principal(new MockPrincipal(user.getUsername()))
                .param("notebookNameField", testName))
        .andExpect(status().isFound())
        .andReturn();

    openTransaction();
    Folder refreshedRootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> newChildren = refreshedRootFolder.getChildrens();
    newChildren.removeAll(orgChildren);
    commitTransaction();

    assertEquals("no new child in root folder", 1, newChildren.size());
    BaseRecord newRecord = (BaseRecord) (newChildren.toArray())[0];
    assertTrue("new record should be a folder", newRecord.isFolder());
    assertTrue("new record should be a notebook", newRecord.isNotebook());
    assertEquals("created notebook has different name", testName, newRecord.getName());

    RSpaceTestUtils.logout();
  }

  @Test
  public void testCreateNotebookIntoSharedFolderMVC() throws Exception {
    TestGroup group = createTestGroup(2);
    User user = group.u1();
    initUser(user);
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);

    openTransaction();
    Long sharedFolderId = group.getGroup().getCommunalGroupFolderId();
    Folder sharedFolder = folderDao.get(sharedFolderId);
    Set<BaseRecord> orgChildren = folderDao.getRootRecordForUser(user).getChildrens();
    commitTransaction();

    String testName = "newTestNotebook";
    this.mockMvc
        .perform(
            post("/workspace/create_notebook/{rootid}", sharedFolder.getId())
                .principal(new MockPrincipal(user.getUsername()))
                .param("notebookNameField", testName))
        .andExpect(status().isFound())
        .andReturn();

    openTransaction();
    Folder refreshedSharedFolder = folderDao.get(sharedFolderId);
    Set<BaseRecord> newSharedChildren = refreshedSharedFolder.getChildrens();

    Folder refreshedRootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> newRootChildren = refreshedRootFolder.getChildrens();
    newRootChildren.removeAll(orgChildren);
    commitTransaction();

    assertEquals("no new child in root folder", 1, newRootChildren.size());
    assertEquals("no new child in shared folder", 1, newSharedChildren.size());
    BaseRecord newRecord = (BaseRecord) (newRootChildren.toArray())[0];
    assertTrue("new record should be a folder", newRecord.isFolder());
    assertTrue("new record should be a notebook", newRecord.isNotebook());
    assertEquals("created notebook has different name", testName, newRecord.getName());

    RSpaceTestUtils.logout();
  }

  @Test
  public void testCreateNotebooksInRootFolderWithAjax() throws Exception {
    User user = createAndSaveUser(getRandomName(10));
    initUser(user);
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);

    openTransaction();
    Folder rootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> orgChildren = rootFolder.getChildrens();
    commitTransaction();

    MvcResult defaultNotebook =
        mockMvc
            .perform(
                post("/workspace/ajax/createNotebook")
                    .principal(new MockPrincipal(user.getUsername())))
            .andReturn();
    Long defaultNotebookId = getFromJsonResponseBody(defaultNotebook, Long.class);
    assertNotNull(defaultNotebookId);

    String testName = "newTestNotebook";
    MvcResult namedNotebook =
        mockMvc
            .perform(
                post("/workspace/ajax/createNotebook")
                    .param("notebookName", testName)
                    .principal(new MockPrincipal(user.getUsername())))
            .andReturn();
    Long namedNotebookId = getFromJsonResponseBody(namedNotebook, Long.class);
    assertNotNull(namedNotebookId);

    openTransaction();
    Folder refreshedRootFolder = folderDao.getRootRecordForUser(user);
    Set<BaseRecord> newChildren = refreshedRootFolder.getChildrens();
    newChildren.removeAll(orgChildren);
    commitTransaction();

    assertEquals("two new notebooks were expected ", 2, newChildren.size());
    BaseRecord createdNotebook1 =
        newChildren.stream().filter(b -> b.getId().equals(defaultNotebookId)).findFirst().get();
    assertTrue(createdNotebook1.isNotebook());
    assertEquals(defaultNotebookId, createdNotebook1.getId());
    assertEquals(Notebook.DEFAULT_NOTEBOOK_NAME, createdNotebook1.getName());

    BaseRecord createdNotebook2 =
        newChildren.stream().filter(b -> b.getId().equals(namedNotebookId)).findFirst().get();
    assertTrue(createdNotebook2.isNotebook());
    assertEquals(namedNotebookId, createdNotebook2.getId());
    assertEquals(testName, createdNotebook2.getName());

    RSpaceTestUtils.logout();
  }

  // RSPAC-363
  @Test
  public void reloadingOfWorkspaceForSharedNotebooks() throws Exception {
    // tests that a notebook can be shared, and that it can be accessed from
    // Shared folder of sharee

    // basic setup of a group with 2 users
    User admin = logoutAndLoginAsSysAdmin();
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    initUsers(pi, u1, u2);
    Group group = createGroupForUsers(admin, pi.getUsername(), "", u1, u2);
    // share NB between u1 and u2
    logoutAndLoginAs(u1);
    Folder u1RootFolder = folderMgr.getRootRecordForUser(u1, u1);
    Notebook nb = createNotebookWithNEntries(u1RootFolder.getId(), "toShare", 2, u1);
    shareNotebookWithGroupMember(u1, nb, u2);
    // this is just u1 going into their notebook
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", nb.getId() + "")
                    .principal(new MockPrincipal(u1.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();
    // and with parent ID set as if browsing folder tree
    MvcResult result2 =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", nb.getId() + "")
                    .principal(new MockPrincipal(u1.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    // now lets log in as U2 and check the notebook can be accessed from the shared folder
    logoutAndLoginAs(u2);
    Optional<Folder> indivSFolder =
        folderMgr.getGroupOrIndividualShrdFolderRootFromSharedSubfolder(nb.getId(), u2);
    assertTrue(indivSFolder.isPresent());

    // now we'll browse to notebook from this folder as if navigating from the shared root
    MvcResult result3 =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", nb.getId() + "")
                    .principal(new MockPrincipal(u2.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    ModelMap map = result3.getModelAndView().getModelMap();
    Breadcrumb bc = (Breadcrumb) map.get("bcrumb");
    // navigate up the breadcrum tree, clicking on each element, make sure sure all ids reload OK
    for (int i = bc.getElements().size() - 1; i >= 0; i--) {
      BreadcrumbElement element = bc.getElements().get(i);
      mockMvc
          .perform(
              get("/workspace/ajax/view/{recordId}", element.getId() + "")
                  .principal(new MockPrincipal(u2.getUsername())))
          .andExpect(modelHasValidAttributes())
          .andReturn();
    }
  }

  /**
   * Reference RSPAC-527.
   *
   * @throws Exception
   */
  @Test
  public void workspaceViewWithFiltersTest() throws Exception {

    User admin = logoutAndLoginAsSysAdmin();
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    initUsers(pi, u1, u2);
    Group group = createGroupForUsers(admin, pi.getUsername(), "", u1, u2);

    logoutAndLoginAs(u1);
    StructuredDocument doc =
        createBasicDocumentInRootFolderWithText(u1, "shared structured document");
    shareRecordWithUser(u1, doc, u2);

    logoutAndLoginAs(u2);
    Folder rootFolderU2 = folderMgr.getRootRecordForUser(u2, u2);

    // No filters
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolderU2.getId() + "")
                    .param(SHARED_FILTER, "false")
                    .principal(new MockPrincipal(u2.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    ISearchResults<BaseRecord> searchResults = getSearchResultsFromMvcResult(result);
    assertTrue(searchResults.getResults().size() > 1);

    // shared filter activated
    MvcResult filterSharedRecordsResult =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolderU2.getId() + "")
                    .param(SHARED_FILTER, "true")
                    .principal(new MockPrincipal(u2.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();
    searchResults = getSearchResultsFromMvcResult(filterSharedRecordsResult);
    assertEquals(1, searchResults.getResults().size());

    Long[] recordsIds = new Long[] {doc.getId()};
    workspaceController.addToFavorites(recordsIds, new MockPrincipal(u2.getUsername()));

    // Shared Filter + Favorites Filter activated
    MvcResult filterSharedFavoritesRecordsResult =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolderU2.getId() + "")
                    .param(SHARED_FILTER, "true")
                    .param(FAVORITES_FILTER, "true")
                    .principal(new MockPrincipal(u2.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();
    searchResults = getSearchResultsFromMvcResult(filterSharedFavoritesRecordsResult);
    assertEquals(1, searchResults.getResults().size());
  }

  /**
   * Reference RSPAC-525 + RSPAC-526.
   *
   * @throws Exception
   */
  @Test
  public void addToFavoritesAndRemoveFromFavoritesTest() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    Folder rootFolder = initUser(user);

    logoutAndLoginAs(user);
    StructuredDocument basicDocument = createBasicDocumentInRootFolderWithText(user, "Some text");
    Long[] recordsIds = new Long[] {basicDocument.getId()};

    workspaceController.addToFavorites(recordsIds, new MockPrincipal(user.getUsername()));

    // Favorites Filter activated
    MvcResult results =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolder.getId() + "")
                    .param(FAVORITES_FILTER, "true")
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    ModelMap map = results.getModelAndView().getModelMap();
    ISearchResults<BaseRecord> searchResults = getSearchResults(map);
    assertEquals(1, searchResults.getResults().size());

    workspaceController.removeFromFavorites(recordsIds, new MockPrincipal(user.getUsername()));

    // Favorites Filter activated
    results =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolder.getId() + "")
                    .param(FAVORITES_FILTER, "true")
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    map = results.getModelAndView().getModelMap();
    searchResults = getSearchResults(map);
    assertEquals(0, searchResults.getResults().size());
  }

  /** RSPAC-1105. */
  @Test
  public void subfolderBreadcrumbsAndFilters() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    Folder rootFolder = initUser(user);
    logoutAndLoginAs(user);

    Folder subFolder1 = createSubFolder(rootFolder, "subfolder1", user);
    Folder subFolder2 = createSubFolder(rootFolder, "subfolder2", user);

    // open workspace view on subFolder. breadcrumbs should appear.
    MvcResult results =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", subFolder1.getId() + "")
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    ModelMap map = results.getModelAndView().getModelMap();
    Breadcrumb bcrumb = (Breadcrumb) map.get("bcrumb");
    assertEquals(2, bcrumb.getElements().size());
    assertEquals(subFolder1.getName(), bcrumb.getElements().get(1).getDisplayname());

    // open subfolder but with favorites filter activated. breadcrumbs should be empty (only Home
    // breadcrumb).
    results =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", subFolder1.getId() + "")
                    .param(FAVORITES_FILTER, "true")
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    map = results.getModelAndView().getModelMap();
    bcrumb = (Breadcrumb) map.get("bcrumb");
    assertEquals(1, bcrumb.getElements().size()); // just 'Home' breadcrumb

    // now try opening subfolder2, but with parent set to subfolder1 (that simulates click in
    // filtered result scenario)
    results =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", subFolder2.getId() + "")
                    .param("parentFolderId", subFolder1.getId() + "")
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    // should returned breadcrumbs to subFolder2
    map = results.getModelAndView().getModelMap();
    bcrumb = (Breadcrumb) map.get("bcrumb");
    assertEquals(2, bcrumb.getElements().size());
    assertEquals(subFolder2.getName(), bcrumb.getElements().get(1).getDisplayname());
  }

  /**
   * Reference RSPAC-589
   *
   * @throws Exception
   */
  @Test
  public void workspaceAllVieweableItemsByUserTest() throws Exception {
    final int n = 10;
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    initUsers(u1);

    logoutAndLoginAs(u1);
    createBasicDocumentInRootFolderWithText(u1, getRandomName(n));
    Folder rootFolterU1 = folderMgr.getRootRecordForUser(u1, u1);

    // No filters
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolterU1.getId() + "")
                    .principal(new MockPrincipal(u1.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    ModelMap map = result.getModelAndView().getModelMap();
    ISearchResults<BaseRecord> searchResults = getSearchResults(map);
    // Including folders
    assertTrue(searchResults.getResults().size() > 1);

    // View all viewable items activated
    MvcResult viewableItemsResult = workspaceViewAll(u1, rootFolterU1);

    map = viewableItemsResult.getModelAndView().getModelMap();
    searchResults = getSearchResults(map);
    // Just documents, entries and notebooks, in our case the basic document
    // previously created
    assertEquals(1, searchResults.getResults().size());
  }

  /**
   * Reference RSPAC-589.
   *
   * @throws Exception
   */
  @Test
  public void workspaceAllVieweableItemsByPiTest() throws Exception {

    final int n = 10;
    User sysadmin = logoutAndLoginAsSysAdmin();
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    initUsers(pi, u1);
    createGroupForUsers(sysadmin, pi.getUsername(), "", u1, pi);

    logoutAndLoginAs(u1);
    createBasicDocumentInRootFolderWithText(u1, getRandomName(n));
    createBasicDocumentInRootFolderWithText(u1, getRandomName(n));
    Folder rootFolderU1 = folderMgr.getRootRecordForUser(u1, u1);

    // No filters
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", rootFolderU1.getId() + "")
                    .principal(new MockPrincipal(u1.getUsername())))
            .andExpect(modelHasValidAttributes())
            .andReturn();

    ISearchResults<BaseRecord> searchResults = getSearchResultsFromMvcResult(result);
    assertTrue(searchResults.getResults().size() > 1);

    logoutAndLoginAs(pi);
    Folder rootFolderPI = folderMgr.getRootRecordForUser(pi, pi);

    // View all viewable items activated
    MvcResult viewableItemsResult = workspaceViewAll(pi, rootFolderPI);
    searchResults = getSearchResultsFromMvcResult(viewableItemsResult);
    // PI user can see two basic documents (u1).
    assertEquals(2, searchResults.getTotalHits().intValue());
    StructuredDocument createdByPi = createBasicDocumentInRootFolderWithText(pi, "any");
    viewableItemsResult = workspaceViewAll(pi, rootFolderPI);

    ISearchResults<BaseRecord> searchResults2 = getSearchResultsFromMvcResult(viewableItemsResult);
    // PI user can see two basic documents (u1).
    assertEquals(3, searchResults2.getTotalHits().intValue());
    // now delet with filter, should still be two //RSPAC-749
    MvcResult deleteResult =
        mockMvc
            .perform(
                post("/workspace/ajax/delete")
                    .param(VIEWABLE_ITEMS_FILTER, "true")
                    .param("recordId", rootFolderPI.getId() + "")
                    .param("toDelete[]", createdByPi.getId() + "")
                    .principal(new MockPrincipal(pi.getUsername())))
            .andReturn();
    ISearchResults<BaseRecord> searchResults3 = getSearchResultsFromMvcResult(deleteResult);
    // PI user can see two basic documents (u1) still
    assertEquals(2, searchResults3.getTotalHits().intValue());
    assertTrue(
        CollectionUtils.isEqualCollection(searchResults.getResults(), searchResults3.getResults()));
  }

  private MvcResult workspaceViewAll(User pi, Folder rootFolderPI) throws Exception {
    return mockMvc
        .perform(
            get("/workspace/ajax/view/{recordId}", rootFolderPI.getId() + "")
                .param(VIEWABLE_ITEMS_FILTER, "true")
                .principal(new MockPrincipal(pi.getUsername())))
        .andExpect(modelHasValidAttributes())
        .andReturn();
  }

  /**
   * Reference RSPAC-589.
   *
   * @throws Exception
   */
  @Test
  public void workspaceAllVieweableItemsByAdminTest() throws Exception {
    final int charLength = 10;

    User sysadmin = logoutAndLoginAsSysAdmin();
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);

    User pi1 = createAndSaveUser(getRandomAlphabeticString("pi1"), Constants.PI_ROLE);
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User pi2 = createAndSaveUser(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    initUsers(pi1, pi2, u1, u2, admin);
    Group group1 = createGroupForUsers(sysadmin, pi1.getUsername(), "", u1, pi1);
    Group group2 = createGroupForUsers(sysadmin, pi2.getUsername(), "", u2, pi2);

    // Creating community for admin
    logoutAndLoginAs(admin);
    Community community = createAndSaveCommunity(admin, getRandomName(charLength));
    communityMgr.addGroupToCommunity(group1.getId(), community.getId(), admin);
    communityMgr.addGroupToCommunity(group2.getId(), community.getId(), admin);

    // Creating basic documents for user1 and user2
    logoutAndLoginAs(u1);
    createBasicDocumentInRootFolderWithText(u1, getRandomName(charLength));
    createBasicDocumentInRootFolderWithText(u1, getRandomName(charLength));

    logoutAndLoginAs(u2);
    createBasicDocumentInRootFolderWithText(u2, getRandomName(charLength));
    createBasicDocumentInRootFolderWithText(u2, getRandomName(charLength));

    logoutAndLoginAs(admin);
    Folder rootFolderAdmin = folderMgr.getRootRecordForUser(admin, admin);

    // View all viewable items activated
    MvcResult viewableItemsResult = workspaceViewAll(admin, rootFolderAdmin);

    ModelMap map = viewableItemsResult.getModelAndView().getModelMap();
    ISearchResults<BaseRecord> searchResults = getSearchResults(map);
    // Admin user can see four basic documents (two documents created by u1
    // and two documents created by u2).
    assertEquals(4, searchResults.getResults().size());
  }

  @Test
  public void getViewablePublicUserInfoListTest() {
    User user = createAndSaveUser(getRandomName(10));
    initUser(user);
    logoutAndLoginAs(user);
    AjaxReturnObject<List<UserPublicInfo>> result =
        workspaceController.getViewablePublicUserInfoList();
    assertNotNull(result.getData());
  }

  private ResultMatcher modelHasValidAttributes() {
    return model().attributeExists("bcrumb", "movetargetRoot");
  }

  @Test
  public void getMyAndSharedTemplatesTest() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("any"));
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi, user);

    // set up two templates owned by pi, one shared with group
    logoutAndLoginAs(pi);
    Group group = createGroupForUsers(pi, pi.getUsername(), "", user, pi);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(pi, "template_test1");
    StructuredDocument template1 =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc1.getId(), pi);

    shareRecordWithGroup(pi, group, doc1);
    shareRecordWithGroup(pi, group, template1);

    // check requests as pi, should see  own template only
    MvcResult piOwnResults =
        this.mockMvc
            .perform(get("/workspace/ajax/getMyTemplates").principal(pi::getUsername))
            .andReturn();

    List<Map> piOwnList = getFromJsonAjaxReturnObject(piOwnResults, List.class);
    assertNotNull(piOwnList);
    assertEquals(1, piOwnList.size());

    MvcResult piSharedResults =
        this.mockMvc
            .perform(get("/workspace/ajax/getTemplatesSharedWithMe").principal(pi::getUsername))
            .andReturn();
    List<Map> piSharedList = getFromJsonAjaxReturnObject(piSharedResults, List.class);
    assertNotNull(piSharedList);
    assertEquals(0, piSharedList.size());

    // check requests as user, should see a single shared template
    logoutAndLoginAs(user);
    MvcResult userOwnResults =
        this.mockMvc
            .perform(get("/workspace/ajax/getMyTemplates").principal(pi::getUsername))
            .andReturn();

    List<Map> userOwnList = getFromJsonAjaxReturnObject(userOwnResults, List.class);
    assertNotNull(userOwnList);
    assertEquals(0, userOwnList.size());

    MvcResult userSharedResults =
        this.mockMvc
            .perform(get("/workspace/ajax/getTemplatesSharedWithMe").principal(pi::getUsername))
            .andReturn();
    List<Map> userSharedList = getFromJsonAjaxReturnObject(userSharedResults, List.class);
    assertNotNull(userSharedList);
    assertEquals(1, userSharedList.size());
    assertEquals(template1.getId().intValue(), userSharedList.get(0).get("id"));
  }

  /**
   * Reference RSPAC-641
   *
   * @throws Exception
   */
  @Test
  public void testMakeFavourite() throws Exception {
    User u2 = createAndSaveUser(getRandomAlphabeticString("any"));
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi, u2);
    logoutAndLoginAs(pi);
    Group g1 = createGroupForUsers(pi, pi.getUsername(), "", u2, pi);
    pi = userMgr.get(pi.getId());
    Folder root = folderMgr.getRootRecordForUser(pi, u2);
    Folder piroot = folderMgr.getRootRecordForUser(pi, pi);
    Long u2Id = root.getId();
    favMgr.saveFavoriteRecord(u2Id, pi.getId());
    assertNotNull(favMgr.get(u2Id, pi.getId()));
    // create a doc & mark as favourite
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi, "any");
    favMgr.saveFavoriteRecord(doc.getId(), pi.getId());
    Folder moveTarget = createSubFolder(piroot, "subfolder", pi);
    mockPrincipal = pi::getUsername;
    // rspac -749
    long numRecords = getNumberOfRecordsInRootFolder(new WorkspaceSettings());
    assertTrue(numRecords > 1);
    // we have 2 favourites, and we're passing in that favourites filter is set, so we should just
    // get 2 results
    MvcResult moveResult =
        mockMvc
            .perform(
                post("/workspace/ajax/move")
                    .param(FAVORITES_FILTER, "true")
                    .param("parentFolderId", piroot.getId() + "")
                    .param("toMove[]", doc.getId() + "")
                    .param("target", moveTarget.getId() + "")
                    .principal(pi::getUsername))
            .andReturn();
    assertThat(getSearchResultsFromMvcResult(moveResult), totalSearchResults(2));
    // now, stay with favorites filter, but delete 1, we should get 1 favourite returned, i.e.
    // filter is still applied.
    // RSPAC-749
    MvcResult deleteResult =
        mockMvc
            .perform(
                post("/workspace/ajax/delete")
                    .param(FAVORITES_FILTER, "true")
                    .param("recordId", moveTarget.getId() + "")
                    .param("toDelete[]", doc.getId() + "")
                    .principal(pi::getUsername))
            .andReturn();

    assertThat(getSearchResultsFromMvcResult(deleteResult), totalSearchResults(1));
  }

  @Test
  public void testCopySuccessfully() throws Exception {
    User u2 = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(u2);
    logoutAndLoginAs(u2);
    mockPrincipal = new MockPrincipal(u2.getUsername());
    Folder root = folderMgr.getRootRecordForUser(u2, u2);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u2, "any");
    int initialRecordCount = (int) getNumberOfRecordsInRootFolder(new WorkspaceSettings());

    MvcResult copyResult =
        mockMvc
            .perform(
                post("/workspace/ajax/copy")
                    .param("parentFolderId", root.getId() + "")
                    .param("idToCopy[]", doc.getId() + "")
                    .param("newName[]", doc.getId() + "")
                    .principal(mockPrincipal))
            .andReturn();
    ISearchResults<BaseRecord> results = getSearchResultsFromMvcResult(copyResult);
    // after copy we now have 1 more results.
    assertThat(results, totalSearchResults(initialRecordCount + 1));
    verify(auditService).notify(any(DuplicateAuditEvent.class));
    verify(auditService).notify(any(CreateAuditEvent.class));
  }

  /** Reference RSPAC-639 */
  @Test
  public void workspaceLabGroupFilterTest() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();

    TestGroup testgroup = createTestGroup(2);
    Folder groupFolderPi = folderMgr.getLabGroupsFolderForUser(testgroup.getPi());
    Folder groupFolderUser = folderMgr.getLabGroupsFolderForUser(testgroup.u1());

    // PI user will see all the user folders (group) + group folder.
    logoutAndLoginAs(testgroup.getPi());

    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", groupFolderPi.getId() + "")
                    .principal(() -> testgroup.getPi().getUsername()))
            .andExpect(modelHasValidAttributes())
            .andReturn();
    assertNResults(result, 3); // 2 users + group folder

    // Default user will see the shared group folder.
    logoutAndLoginAs(testgroup.u1());

    result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", groupFolderUser.getId() + "")
                    .principal(() -> testgroup.u1().getUsername()))
            .andExpect(modelHasValidAttributes())
            .andReturn();
    assertNResults(result, 1);

    // now let's add in another PI as group member RSPAC-625
    logoutAndLoginAsSysAdmin();
    User otherPI = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(otherPI);
    grpMgr.addUserToGroup(otherPI.getUsername(), testgroup.getGroup().getId(), RoleInGroup.DEFAULT);
    logoutAndLoginAs(testgroup.getPi());

    result =
        mockMvc
            .perform(
                get("/workspace/ajax/view/{recordId}", groupFolderPi.getId() + "")
                    .principal(() -> testgroup.getPi().getUsername()))
            .andExpect(modelHasValidAttributes())
            .andReturn();
    assertNResults(result, 3); // can't see new PI, still 2 users + grp folder
  }

  @Test
  public void displayMediaFileWithRevisions() throws Exception {
    User any = createInitAndLoginAnyUser();
    EcatImage image = addImageToGallery(any);
    Principal principal = new MockPrincipal(any.getUsername());

    MvcResult result =
        mockMvc
            .perform(get("/workspace/getEcatMediaFile/{id}", image.getId()).principal(principal))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString(image.getFileName())))
            .andReturn();
    assertNull(result.getResolvedException());
  }

  @Test
  public void testGettingRecordInfoForSharedAttachment() throws Exception {

    User owner = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    initUsers(owner, other);

    // create a document, with file attachment
    logoutAndLoginAs(owner);
    createGroupForUsers(owner, owner.getUsername(), "", owner, other);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(owner, "any");
    Field first = sd.getFields().get(0);
    EcatDocumentFile docFile =
        addAttachmentDocumentToField(RSpaceTestUtils.getResource("MSattachment.doc"), first, owner);

    // check details of the attachment
    MvcResult result = getRecordInfo(docFile.getId(), owner);
    assertNotNull(result);
    DetailedRecordInformation docFileInfo =
        getFromJsonAjaxReturnObject(result, DetailedRecordInformation.class);
    assertNotNull(docFileInfo);
    assertEquals(docFile.getId(), docFileInfo.getId());
    assertEquals(docFile.getVersion(), docFileInfo.getVersion());

    // other user can't access attachment details
    logoutAndLoginAs(other);
    result = getRecordInfo(docFile.getId(), other);
    assertAuthorizationException(result);

    // now share the document containing an attachment
    logoutAndLoginAs(owner);
    shareRecordWithUser(owner, sd, other);

    // other user can access attachment details now
    logoutAndLoginAs(other);
    result = getRecordInfo(docFile.getId(), other);
    assertNull(result.getResolvedException());
  }

  @Test
  public void getRecordInformationForRevisionOfAttachment() throws Exception {
    User any = createInitAndLoginAnyUser();
    EcatImage image = addImageToGallery(any);
    EcatImage updatedImage = updateImageInGallery(image.getId(), any);

    List<AuditedEntity<EcatImage>> imageRevs =
        auditMgr.getRevisionsForEntity(EcatImage.class, image.getId());
    assertEquals(2, imageRevs.size());
    Long firstRevId = imageRevs.get(0).getRevision().longValue();
    Long secondRevId = imageRevs.get(1).getRevision().longValue();

    MvcResult firstRevResult =
        mockMvc
            .perform(
                get("/workspace/getRecordInformation")
                    .param("recordId", image.getId().toString())
                    .param("revision", firstRevId + "")
                    .principal(new MockPrincipal(any.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(firstRevResult.getResolvedException());
    DetailedRecordInformation firstRevImgInfo =
        getFromJsonAjaxReturnObject(firstRevResult, DetailedRecordInformation.class);
    assertEquals(firstRevId, firstRevImgInfo.getRevision());
    assertEquals(image.getName(), firstRevImgInfo.getName());

    MvcResult secondRevResult =
        mockMvc
            .perform(
                get("/workspace/getRecordInformation")
                    .param("recordId", image.getId().toString())
                    .param("revision", secondRevId + "")
                    .principal(new MockPrincipal(any.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(secondRevResult.getResolvedException());
    DetailedRecordInformation secondRevImgInfo =
        getFromJsonAjaxReturnObject(secondRevResult, DetailedRecordInformation.class);
    assertEquals(secondRevId, secondRevImgInfo.getRevision());
    assertEquals(updatedImage.getName(), secondRevImgInfo.getName());

    // asking for record info without specific revision should return latest version
    MvcResult currentRevResult = getRecordInfo(image.getId(), any);
    assertNull(currentRevResult.getResolvedException());
    DetailedRecordInformation curretnRevImgInfo =
        getFromJsonAjaxReturnObject(currentRevResult, DetailedRecordInformation.class);
    assertNull(curretnRevImgInfo.getRevision());
    assertEquals(updatedImage.getName(), curretnRevImgInfo.getName());
  }

  @Test
  public void getDetailedRecordInformationTest() throws Exception {

    User owner = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    initUsers(owner, other);
    logoutAndLoginAs(owner);

    Group group = createGroupForUsers(owner, owner.getUsername(), null, other, owner);
    group.setDisplayName(owner.getUsername() + "_group");
    grpMgr.saveGroup(group, owner);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "any");
    doc.setDocTag("testTags");
    recordManager.save(doc, owner);

    MvcResult result1 = getRecordInfo(doc.getId(), owner);
    assertNull(result1.getResolvedException());

    DetailedRecordInformation detailedInfo1 =
        getFromJsonAjaxReturnObject(result1, DetailedRecordInformation.class);
    assertEquals(doc.getId(), detailedInfo1.getId());
    assertEquals("Structured Document", detailedInfo1.getType());
    assertEquals("testTags", detailedInfo1.getTags());
    assertEquals("/" + doc.getName(), detailedInfo1.getPath());
    assertEquals(null, detailedInfo1.getCurrentEditor());
    assertEquals(EditStatus.VIEW_MODE.toString(), detailedInfo1.getStatus());
    assertFalse(detailedInfo1.isShared());
    assertFalse(detailedInfo1.getSigned());
    assertFalse(detailedInfo1.getWitnessed());
    assertEquals(SignatureStatus.UNSIGNED, detailedInfo1.getSignatureStatus());

    shareRecordWithGroup(owner, group, doc);
    shareRecordWithUser(owner, doc, other);
    String[] witnesses = {other.getUsername()};
    signingManager.signRecord(doc.getId(), owner, witnesses, "some statement");

    MvcResult result2 = getRecordInfo(doc.getId(), owner);
    assertNull(result2.getResolvedException());

    DetailedRecordInformation detailedInfo2 =
        getFromJsonAjaxReturnObject(result2, DetailedRecordInformation.class);
    assertEquals(doc.getId(), detailedInfo2.getId());
    assertTrue(detailedInfo2.isShared());
    Map<String, String> retrievedSharedGroups = detailedInfo2.getSharedGroupsAndAccess();
    assertEquals(1, retrievedSharedGroups.size());
    assertEquals("READ", retrievedSharedGroups.get(group.getDisplayName()));
    Map<String, String> retrievedSharedUsers = detailedInfo2.getSharedUsersAndAccess();
    assertEquals(1, retrievedSharedUsers.size());
    assertEquals("READ", retrievedSharedUsers.get(other.getDisplayName()));
    Map<String, String> retrievedSharedNotebooks = detailedInfo2.getSharedNotebooksAndOwners();
    assertEquals(0, retrievedSharedNotebooks.size());
    assertTrue(detailedInfo2.getSigned());
    assertFalse(detailedInfo2.getWitnessed());
    assertEquals(SignatureStatus.AWAITING_WITNESS, detailedInfo2.getSignatureStatus());
  }

  @Test
  public void getDetailedRecordInformationDeclinedToWitness() throws Exception {
    User owner = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    initUsers(owner, other);
    logoutAndLoginAs(owner);

    Group group = createGroupForUsers(owner, owner.getUsername(), null, other, owner);
    group.setDisplayName(owner.getUsername() + "_group");
    grpMgr.saveGroup(group, owner);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "any");
    doc.setDocTag("testTags");
    recordManager.save(doc, owner);

    // Share, sign and ask to witness
    shareRecordWithGroup(owner, group, doc);
    shareRecordWithUser(owner, doc, other);
    String[] witnesses = {other.getUsername()};
    signingManager.signRecord(doc.getId(), owner, witnesses, "some statement");

    // Witness declines to sign
    signingManager.updateWitness(doc.getId(), other, false, "not witnessing this");

    // Get detailed record info
    MvcResult result = getRecordInfo(doc.getId(), owner);
    assertNull(result.getResolvedException());
    DetailedRecordInformation detailedInfo =
        getFromJsonAjaxReturnObject(result, DetailedRecordInformation.class);

    assertTrue(detailedInfo.getSigned());
    assertFalse(detailedInfo.getWitnessed());
    assertEquals(
        SignatureStatus.SIGNED_AND_LOCKED_WITNESSES_DECLINED, detailedInfo.getSignatureStatus());
  }

  private MvcResult getRecordInfo(Long recordId, User user) throws Exception {
    MvcResult result;
    result =
        mockMvc
            .perform(
                get("/workspace/getRecordInformation")
                    .param("recordId", recordId.toString())
                    .principal(new MockPrincipal(user.getUsername())))
            .andReturn();
    return result;
  }

  private void assertNResults(MvcResult result, final int expectedResultCount) {
    ModelMap map = result.getModelAndView().getModelMap();
    ISearchResults<BaseRecord> searchResults = getSearchResults(map);
    assertEquals(expectedResultCount, searchResults.getResults().size());
  }

  private ISearchResults<BaseRecord> getSearchResultsFromMvcResult(MvcResult result) {
    return getSearchResults(result.getModelAndView().getModelMap());
  }

  private ISearchResults<BaseRecord> getSearchResults(ModelMap map) {
    return (ISearchResults<BaseRecord>) map.get("searchResults");
  }

  @Test
  public void testWorkspaceItemsPerPagePreference() throws Exception {

    StructuredDocument doc = setUpLoginAsPIUserAndCreateADocument();
    createNDocumentsInFolderForUserFor(20, piUser, doc.getParent(), doc.getForm());

    String defaultResultsPerPage = Preference.WORKSPACE_RESULTS_PER_PAGE.getDefaultValue();
    UserPreference pref =
        userMgr.getPreferenceForUser(piUser, Preference.WORKSPACE_RESULTS_PER_PAGE);
    assertEquals(defaultResultsPerPage, pref.getValue());

    WorkspaceSettings settings = new WorkspaceSettings();
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, settings);
    ISearchResults results = (ISearchResults) modelTss.get("searchResults");
    assertEquals(defaultResultsPerPage, results.getResults().size() + "");

    pref = userMgr.getPreferenceForUser(piUser, Preference.WORKSPACE_RESULTS_PER_PAGE);
    assertEquals(defaultResultsPerPage, pref.getValue());

    // listing again, with 25 results per page set in request
    settings.setResultsPerPage(25);
    workspaceController.listRootFolder(
        "", model, mockPrincipal, request, session, response, settings);
    ISearchResults results2 = (ISearchResults) modelTss.get("searchResults");
    assertEquals(25, results2.getResults().size());

    pref = userMgr.getPreferenceForUser(piUser, Preference.WORKSPACE_RESULTS_PER_PAGE);
    assertEquals(25, pref.getValueAsNumber().intValue());
  }

  @Test
  public void testWorkspaceViewModePreference() throws Exception {
    User user = createInitAndLoginAnyUser();
    MockPrincipal principal = new MockPrincipal(user.getUsername());

    // MockHttpSession session = new MockHttpSession();

    // Test that default value is set in database
    String defaultViewMode = Preference.CURRENT_WORKSPACE_VIEW_MODE.getDefaultValue();
    UserPreference pref =
        userMgr.getPreferenceForUser(user, Preference.CURRENT_WORKSPACE_VIEW_MODE);
    assertEquals(defaultViewMode, pref.getValue());

    // Test that the database value is returned from the controller
    Map<String, Object> initialModel =
        mockMvc
            .perform(get("/workspace").session(session).principal(principal))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView()
            .getModel();

    WorkspaceSettings initialSettings =
        mapper.readValue((String) initialModel.get("workspaceConfigJson"), WorkspaceSettings.class);
    String settingsKey = (String) initialModel.get("settingsKey");

    assertEquals(pref.getValue(), initialSettings.getCurrentViewMode().toString());

    // Test that changing the value works when saving preferences
    mockMvc
        .perform(
            post("/workspace/ajax/saveWorkspaceSettings")
                .session(session)
                .param("settingsKey", settingsKey)
                .param("currentViewMode", WorkspaceViewMode.TREE_VIEW.toString())
                .principal(principal))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    pref = userMgr.getPreferenceForUser(user, Preference.CURRENT_WORKSPACE_VIEW_MODE);
    assertEquals(WorkspaceViewMode.TREE_VIEW.toString(), pref.getValue());

    // Test that the new value is returned
    WorkspaceSettings settings =
        mapper.readValue(
            ((String)
                mockMvc
                    .perform(get("/workspace").session(session).principal(principal))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getModelAndView()
                    .getModel()
                    .get("workspaceConfigJson")),
            WorkspaceSettings.class);
    assertEquals(WorkspaceViewMode.TREE_VIEW.toString(), settings.getCurrentViewMode().toString());
  }

  @Test
  public void saveRetrieveRecordTags() throws Exception {
    User user = createInitAndLoginAnyUser();
    MockPrincipal principal = new MockPrincipal(user.getUsername());
    // create 1st doc with tag
    StructuredDocument testDoc = createBasicDocumentInRootFolderWithText(user, "testDoc1");
    documentTagManager.saveTag(testDoc.getId(), "testTag", user);
    // create 2nd doc without tag
    StructuredDocument testDoc2 = createBasicDocumentInRootFolderWithText(user, "testDoc2");
    // create notebook
    BaseRecord workspace = testDoc.getParent();
    BaseRecord notebook = createNotebookWithNEntries(workspace.getId(), "testNotebook", 1, user);

    // tag 2nd doc and notebook
    String simplestTagRecordJSON =
        "[ { \"recordId\": "
            + testDoc2.getId()
            + ", \"tagMetaData\": \"docTag\"}, "
            + "{ \"recordId\": "
            + notebook.getId()
            + ", \"tagMetaData\": \"notebookTag\"}"
            + " ]";
    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/workspace/saveTagsForRecords")
                    .content(simplestTagRecordJSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(principal))
            .andReturn();
    assertNull(mvcResult.getResolvedException());

    // check tags for all 3 items
    mvcResult =
        mockMvc
            .perform(
                get("/workspace/getTagsForRecords")
                    .param(
                        "recordIds",
                        testDoc.getId() + "," + notebook.getId() + "," + testDoc2.getId())
                    .principal(principal))
            .andReturn();
    assertNull(mvcResult.getResolvedException());
    List<RecordTagData> foundTagDTOs =
        mvcUtils.getFromJsonResponseBodyByTypeRef(mvcResult, new TypeReference<>() {});
    assertEquals(3, foundTagDTOs.size());
    assertEquals(testDoc.getId(), foundTagDTOs.get(0).getRecordId());
    assertEquals("testTag", foundTagDTOs.get(0).getTagMetaData());
    assertEquals(notebook.getId(), foundTagDTOs.get(1).getRecordId());
    assertEquals("notebookTag", foundTagDTOs.get(1).getTagMetaData());
    assertEquals(testDoc2.getId(), foundTagDTOs.get(2).getRecordId());
    assertEquals("docTag", foundTagDTOs.get(2).getTagMetaData());
  }
}

package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.model.EditStatus;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Signature;
import com.researchspace.model.SignatureInfo;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.UserSessionTracker;
import java.util.Iterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

public class PublicStructuredDocumentControllerIT extends MVCTestBase {

  private @Autowired PublicStructuredDocumentController controller;
  private @Autowired JdbcTemplate jdbcTemplate;
  private @Autowired RecordSharingManager recShareMgr;
  private MockServletContext sc;
  private MockHttpSession mockHttpSession;
  @Mock private RecordSigningManager recordSigningManagerMock;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManagerMock;
  private SignatureInfo signatureInfo = new SignatureInfo();
  private Signature signature = new Signature();
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    mockRequest = new MockHttpServletRequest();
    mockResponse = new MockHttpServletResponse();
    openMocks(this);
    sc = new MockServletContext();
    sc.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    mockHttpSession = new MockHttpSession();
    controller.setServletContext(sc);
    ReflectionTestUtils.setField(controller, "signingManager", recordSigningManagerMock);
    when(recordSigningManagerMock.getSignatureForRecord(any(Long.class))).thenReturn(signature);
    ReflectionTestUtils.setField(
        controller, "systemPropertyPermissionManager", systemPropertyPermissionManagerMock);
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            any(User.class), eq("public_sharing")))
        .thenReturn(true);
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    JdbcTestUtils.deleteFromTables(jdbcTemplate, "RecordGroupSharing");
  }

  protected UserSessionTracker getCurrentActiveUsers() {
    return (UserSessionTracker) sc.getAttribute(UserSessionTracker.USERS_KEY);
  }

  @Test
  public void testPublishedRecordAccessDoesNotRequireLoggedInUser() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    User anonymous = userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    setup.structuredDocument.setSigned(true);
    recordMgr.save(setup.structuredDocument, setup.user);
    logoutCurrentUser();
    ModelAndView result =
        controller.openDocument(rgs.getPublicLink(), modelTss, mockResponse, mockRequest);
    assertEquals(
        setup.structuredDocument,
        (StructuredDocument) result.getModelMap().getAttribute("structuredDocument"));
    assertEquals(
        setup.structuredDocument.getName(), result.getModelMap().getAttribute("documentName"));
    assertEquals(EditStatus.CAN_NEVER_EDIT, result.getModelMap().getAttribute("editStatus"));
    assertEquals(setup.structuredDocument.getId(), result.getModelMap().getAttribute("id"));
    assertEquals(anonymous, result.getModelMap().getAttribute("user"));
    assertEquals(signature.toSignatureInfo(), result.getModelMap().getAttribute("signatureInfo"));
    assertEquals(
        setup.structuredDocument.getModificationDate(),
        result.getModelMap().getAttribute("modificationDate"));
  }

  @Test
  public void testResponseHasNegativePublicAttributesInModelMap() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, false, 1);
    publishDocumentForUser(setup.user, setup.structuredDocument.getId());
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    ModelAndView result =
        controller.openDocument(rgs.getPublicLink(), modelTss, mockResponse, mockRequest);
    assertEquals("", result.getModelMap().getAttribute("publicationSummary"));
    assertEquals(false, result.getModelMap().getAttribute("publishOnInternet"));
    assertEquals(null, result.getModelMap().getAttribute("contactDetails"));
  }

  @Test
  public void testResponseHasPositivePublicAttributesInModelMap() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, false, 1);
    publishDocumentForUser(
        setup.user, setup.structuredDocument.getId(), true, true, "publication_summary_text");
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    ModelAndView result =
        controller.openDocument(rgs.getPublicLink(), modelTss, mockResponse, mockRequest);
    assertEquals(
        "publication_summary_text", result.getModelMap().getAttribute("publicationSummary"));
    assertEquals(true, result.getModelMap().getAttribute("publishOnInternet"));
    assertEquals(setup.user.getEmail(), result.getModelMap().getAttribute("contactDetails"));
  }

  @Test
  public void testSharingLinkNotFoundException() throws Exception {
    setUpDocumentGroupAndPublishRecordWithPublisherHavingRole("standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    assertThrows(
        PublicLinkNotFoundException.class,
        () -> controller.openDocument("randomstuffnotalink", modelTss, mockResponse, mockRequest));
  }

  @Test
  public void testSharingLinkNotFoundExceptionTakesPrecendenceOverPublicViewingDisabledException()
      throws Exception {
    setUpDocumentGroupAndPublishRecordWithPublisherHavingRole("standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            any(User.class), eq("public_sharing")))
        .thenReturn(false);
    assertThrows(
        PublicLinkNotFoundException.class,
        () -> controller.openDocument("randomstuffnotalink", modelTss, mockResponse, mockRequest));
  }

  @Test
  public void testPublicViewingDisabledException() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    when(systemPropertyPermissionManagerMock.isPropertyAllowed(
            any(User.class), eq("public_sharing")))
        .thenReturn(false);
    controller.openDocument(rgs.getPublicLink(), modelTss, mockResponse, mockRequest);
    String redirect = getRedirectUrl(mockResponse);
    Assertions.assertEquals("/public/publishIsDisabled", redirect);
  }

  private String getRedirectUrl(MockHttpServletResponse resp) {
    return resp.getHeaderValue("Location").toString();
  }

  @Test
  public void shouldReturnPublicLinkWhenExists() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    assertEquals(
        rgs.getPublicLink(),
        controller.getPublicDocForRecordOrParentOfRecord(
            setup.structuredDocument.getGlobalIdentifier()));
  }

  @Test
  public void shouldReturnEmptyStringWhenNoPublicLinkForDocExists() throws Exception {
    // this shares but doesn't publish
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    assertThat(
            controller.getPublicDocForRecordOrParentOfRecord(
                setup.structuredDocument.getGlobalIdentifier()))
        .isEmpty();
  }

  @Test
  public void shouldReturnParentPublicLinkWhenNoPublicLinkForDocExistsButDoesForParent()
      throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE, true);
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    Long notebookEntryID = folderMgr.getFolderChildrenIds(setup.notebook).iterator().next();
    assertEquals(
        rgs.getPublicLink(),
        controller.getPublicDocForRecordOrParentOfRecord(setup.notebook.getGlobalIdentifier()));
    assertEquals(
        rgs.getPublicLink() + "?initialRecordToDisplay=" + notebookEntryID,
        controller.getPublicDocForRecordOrParentOfRecord("SD" + notebookEntryID));
  }

  @Test
  public void anonymousCallerGetsNoLinkForLinkOnlyPublication() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    assertEquals(
        "",
        controller.getPublicDocForRecordOrParentOfRecord(
            setup.structuredDocument.getGlobalIdentifier()));
  }

  @Test
  public void anonymousGuestGetsNoLinkForLinkOnlyPublication() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    PublicDocumentsUtilities.loginAnonymousUser();
    assertEquals(
        "",
        controller.getPublicDocForRecordOrParentOfRecord(
            setup.structuredDocument.getGlobalIdentifier()));
  }

  @Test
  public void anonymousCallerGetsNoLinkForLinkOnlyNotebookOrItsEntry() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE, true);
    Long entryId = folderMgr.getFolderChildrenIds(setup.notebook).iterator().next();
    logoutCurrentUser();
    assertEquals(
        "", controller.getPublicDocForRecordOrParentOfRecord(setup.notebook.getGlobalIdentifier()));
    assertEquals("", controller.getPublicDocForRecordOrParentOfRecord("SD" + entryId));
  }

  @Test
  public void anonymousCallerGetsLinkForInternetPublication() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, false, 1);
    publishDocumentForUser(setup.user, setup.structuredDocument.getId(), true, false, "");
    String link =
        recShareMgr.getRecordSharingInfo(setup.structuredDocument.getId()).get(0).getPublicLink();
    logoutCurrentUser();
    assertEquals(
        link,
        controller.getPublicDocForRecordOrParentOfRecord(
            setup.structuredDocument.getGlobalIdentifier()));
  }

  @Test
  public void anonymousCallerGetsInternetNotebookLinkForItsEntries() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, true, 2);
    publishDocumentForUser(setup.user, setup.notebook.getId(), true, false, "");
    String notebookLink =
        recShareMgr.getRecordSharingInfo(setup.notebook.getId()).get(0).getPublicLink();
    Iterator<Long> entryIds = folderMgr.getFolderChildrenIds(setup.notebook).iterator();
    Long entryWithOwnLink = entryIds.next();
    Long plainEntry = entryIds.next();
    // a link-only publication of the entry itself, inside the internet notebook
    publishDocumentForUser(setup.user, entryWithOwnLink);
    // precondition: without the entry's own withheld link, the second assertion repeats the first
    assertNotNull(recShareMgr.getRecordSharingInfo(entryWithOwnLink).get(0).getPublicLink());
    logoutCurrentUser();
    assertEquals(
        notebookLink + "?initialRecordToDisplay=" + plainEntry,
        controller.getPublicDocForRecordOrParentOfRecord("SD" + plainEntry));
    assertEquals(
        notebookLink + "?initialRecordToDisplay=" + entryWithOwnLink,
        controller.getPublicDocForRecordOrParentOfRecord("SD" + entryWithOwnLink));
  }

  @Test
  public void anonymousCallerGetsInternetNotebookLinkForEntrySharedWithGroup() throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(
            "standardUser", Constants.USER_ROLE, true, 1);
    publishDocumentForUser(setup.user, setup.notebook.getId(), true, false, "");
    String notebookLink =
        recShareMgr.getRecordSharingInfo(setup.notebook.getId()).get(0).getPublicLink();
    Long entryId = folderMgr.getFolderChildrenIds(setup.notebook).iterator().next();
    publishDocumentForUser(setup.user, entryId);
    StructuredDocument entry = (StructuredDocument) recordMgr.get(entryId);
    shareRecordWithGroup(setup.user, setup.group, entry);
    // precondition: sharing gives the entry a second parent, so getParent() is null
    assertNull(recordMgr.get(entryId).getParent());
    logoutCurrentUser();
    mockMvc
        .perform(get("/public/publishedView/publiclink").param("globalId", "SD" + entryId))
        .andExpect(status().isOk())
        .andExpect(content().string(notebookLink + "?initialRecordToDisplay=" + entryId));
  }

  @Test
  public void containerPrincipalWithoutRSpaceLoginGetsNoLinkForLinkOnlyPublication()
      throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    logoutCurrentUser();
    // under SSO the servlet container can supply a user who never signed in to RSpace
    mockMvc
        .perform(
            get("/public/publishedView/publiclink")
                .param("globalId", setup.structuredDocument.getGlobalIdentifier())
                .principal(new MockPrincipal("ssoOnlyUser")))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  public void otherSignedInUserGetsLinkForLinkOnlyPublication() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE);
    String link =
        recShareMgr.getRecordSharingInfo(setup.structuredDocument.getId()).get(0).getPublicLink();
    User other = createAndSaveUser("other" + getRandomName(10), Constants.USER_ROLE);
    logoutAndLoginAs(other);
    assertEquals(
        link,
        controller.getPublicDocForRecordOrParentOfRecord(
            setup.structuredDocument.getGlobalIdentifier()));
  }
}

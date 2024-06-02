package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

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

public class PublicStructuredDocumentControllerIT extends RealTransactionSpringTestBase {

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
    ReflectionTestUtils.setField(controller, "signingManager", recordSigningManagerMock);
    when(recordSigningManagerMock.getSignatureForRecord(any(Long.class))).thenReturn(signature);
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
        () -> {
          controller.openDocument("randomstuffnotalink", modelTss, mockResponse, mockRequest);
        });
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
        () -> {
          controller.openDocument("randomstuffnotalink", modelTss, mockResponse, mockRequest);
        });
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
    assertEquals(
        "",
        controller.getPublicDocForRecordOrParentOfRecord(
            setup.structuredDocument.getGlobalIdentifier()));
  }

  @Test
  public void shouldReturnParentPublicLinkWhenNoPublicLinkForDocExistsButDoesForParent()
      throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "standardUser", Constants.USER_ROLE, true);
    RecordGroupSharing rgs = recShareMgr.getSharedRecordsForUser(setup.user).get(0);
    Long notebookEntryID = folderMgr.getRecordIds(setup.notebook).iterator().next();
    assertEquals(
        rgs.getPublicLink(),
        controller.getPublicDocForRecordOrParentOfRecord(setup.notebook.getGlobalIdentifier()));
    assertEquals(
        rgs.getPublicLink() + "?initialRecordToDisplay=" + notebookEntryID,
        controller.getPublicDocForRecordOrParentOfRecord("SD" + notebookEntryID));
  }
}

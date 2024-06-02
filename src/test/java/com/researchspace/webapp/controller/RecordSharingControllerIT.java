package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.Constants;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.model.Community;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.web.servlet.ModelAndView;

public class RecordSharingControllerIT extends RealTransactionSpringTestBase {

  private RecordSharingController recordSharingCtrller;
  private String standardUser;
  private Principal mockSysadminPrincipal;

  @Autowired
  public void setCtrller(RecordSharingController ctrller) {
    this.recordSharingCtrller = ctrller;
  }

  @Autowired public CommunityServiceManager communityServiceManager;
  private @Autowired JdbcTemplate jdbcTemplate;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    standardUser = "standardUser";
    mockSysadminPrincipal = () -> "sysadmin1";
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    JdbcTestUtils.deleteFromTables(jdbcTemplate, "RecordGroupSharing");
  }

  @Test
  public void testAjaxList() throws Exception {
    setUpDocumentGroupForPIUserAndShareRecord();
    recordSharingCtrller.ajaxlist(
        model,
        mockPrincipal,
        PaginationCriteria.createForClass(RecordGroupSharing.class, null, "ASC", 0L, 5),
        new SharedRecordSearchCriteria());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    assertEquals(1, rgs.size());
  }

  @Test
  public void testList() throws Exception {
    // create document, other user and group
    // create document, other user and group
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();

    recordSharingCtrller.list(model, mockPrincipal, createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    assertEquals(1, rgs.size());
    assertEquals(setup.structuredDocument, rgs.get(0).getShared());
    assertEquals(setup.group, rgs.get(0).getSharee());
    assertEquals(PermissionType.WRITE, rgs.get(0).getPermType());
  }

  @Test
  public void testListPublishedForStandardUser() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            standardUser, Constants.USER_ROLE);
    ModelAndView mv =
        recordSharingCtrller.listPublished(model, () -> setup.user.getUsername(), createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    assertEquals(1, rgs.size());
    assertEquals(setup.structuredDocument, rgs.get(0).getShared());
    assertNotNull(rgs.get(0).getPublicLink());
    assertEquals(PermissionType.READ, rgs.get(0).getPermType());
    assertNull(mv.getModelMap().getAttribute(RecordSharingController.SYSADMIN_PUBLISHED_LINKS));
    assertEquals(
        setup.user.getUsername(), mv.getModelMap().getAttribute(RecordSharingController.SHAREE));
    makeStandardAssertions(mv);
  }

  private void makeStandardAssertions(ModelAndView mv) {
    assertTrue(
        (Boolean) mv.getModelMap().getAttribute(RecordSharingController.CONTAINS_PUBLISHED_LINKS));
    assertEquals(
        "/record/share/ajax/publiclinks/manage?pageNumber=0&resultsPerPage=10&sortOrder=DESC&orderBy=creationDate",
        ((PaginationObject) mv.getModelMap().getAttribute("orderByCreationDateLink")).getLink());
    assertEquals(
        "/record/share/ajax/publiclinks/manage?pageNumber=0&resultsPerPage=10&sortOrder=DESC&orderBy=name",
        ((PaginationObject) mv.getModelMap().getAttribute("orderByNameLink")).getLink());
    assertEquals(
        "/record/share/ajax/publiclinks/manage?pageNumber=0&resultsPerPage=10&sortOrder=DESC&orderBy=sharee",
        ((PaginationObject) mv.getModelMap().getAttribute("orderByShareeLink")).getLink());
  }

  @Test
  public void testListPublishedForPIUser() throws Exception {
    GroupSetUp setup =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(standardUser, Constants.PI_ROLE);
    // also publish a document belonging to a member of the PI's group
    User member = createAndSaveUser("member", Constants.USER_ROLE);
    logoutAndLoginAs(member);
    root = initUser(member);
    RSForm form = createAnyForm(member);
    StructuredDocument memberDoc = createDocumentInFolder(root, form, member);
    addUserToGroup(member, setup.group, RoleInGroup.DEFAULT, setup.user);
    publishDocumentForUser(member, memberDoc.getId());
    ModelAndView mv =
        recordSharingCtrller.listPublished(model, () -> setup.user.getUsername(), createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    // PI can see own doc and member's published doc
    RecordGroupSharing piCreated, memberCreated;
    if (setup.structuredDocument.equals(rgs.get(1).getShared())) {
      piCreated = rgs.get(1);
      memberCreated = rgs.get(0);
    } else {
      piCreated = rgs.get(0);
      memberCreated = rgs.get(1);
    }
    assertEquals(2, rgs.size());
    assertEquals(setup.structuredDocument, piCreated.getShared());
    assertNotNull(piCreated.getPublicLink());
    assertEquals(PermissionType.READ, piCreated.getPermType());
    assertEquals(memberDoc, memberCreated.getShared());
    assertNotNull(memberCreated.getPublicLink());
    assertEquals(PermissionType.READ, memberCreated.getPermType());
    assertEquals(
        setup.user.getUsername(), mv.getModelMap().getAttribute(RecordSharingController.SHAREE));
    assertNull(mv.getModelMap().getAttribute(RecordSharingController.SYSADMIN_PUBLISHED_LINKS));
    makeStandardAssertions(mv);
  }

  @Test
  public void testListPublishedForSysadminUserCanSeeAllPublishedDocs() throws Exception {
    GroupSetUp setup1 =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            standardUser, Constants.USER_ROLE);
    // also publish a document belonging to another user in a different group
    GroupSetUp setup2 =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "anotherUser", Constants.USER_ROLE);

    ModelAndView mv =
        recordSharingCtrller.listPublished(model, mockSysadminPrincipal, createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    Collections.sort(
        rgs, (r1, r2) -> r2.getSharedBy().getUsername().compareTo(r1.getSharedBy().getUsername()));
    // Sysadmin can see all published docs
    // Results are ordered by created descending
    assertEquals(2, rgs.size());
    assertEquals(setup1.structuredDocument, rgs.get(0).getShared());
    assertNotNull(rgs.get(1).getPublicLink());
    assertEquals(PermissionType.READ, rgs.get(0).getPermType());
    assertEquals(setup2.structuredDocument, rgs.get(1).getShared());
    assertNotNull(rgs.get(0).getPublicLink());
    assertEquals(PermissionType.READ, rgs.get(1).getPermType());
    assertEquals("sysadmin1", mv.getModelMap().getAttribute(RecordSharingController.SHAREE));
    assertTrue(
        (Boolean) mv.getModelMap().getAttribute(RecordSharingController.SYSADMIN_PUBLISHED_LINKS));
    makeStandardAssertions(mv);
  }

  @Test
  public void testListPublishedForCommunityAdminUserCanSeeAllPublishedDocsInCommunity()
      throws Exception {
    GroupSetUp setup1 =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            standardUser, Constants.USER_ROLE);
    // also publish a document belonging to another user in a different group
    GroupSetUp setup2 =
        setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
            "anotherUser", Constants.USER_ROLE);
    User communityAdmin = userMgr.getUserByUsername("dev4");
    User otherAdmin = userMgr.getUserByUsername("dev1");
    Community community1 = new Community();
    community1.addAdmin(communityAdmin);

    Community community2 = new Community();
    community2.addAdmin(otherAdmin);
    communityServiceManager.saveNewCommunity(community1, communityAdmin);
    communityServiceManager.saveNewCommunity(community2, otherAdmin);
    // You must be logged in as sysadmin else you dont have write permission to the communities
    // and addGroup will fail
    User sysadmin = logoutAndLoginAsSysAdmin();

    communityServiceManager.addGroupToCommunity(setup1.group.getId(), community1.getId(), sysadmin);
    communityServiceManager.addGroupToCommunity(setup2.group.getId(), community2.getId(), sysadmin);
    ModelAndView mv = recordSharingCtrller.listPublished(model, () -> "dev4", createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    assertEquals(1, rgs.size());
    assertEquals(setup1.structuredDocument, rgs.get(0).getShared());
    assertNotNull(rgs.get(0).getPublicLink());
    assertEquals(PermissionType.READ, rgs.get(0).getPermType());
    assertEquals("dev4", mv.getModelMap().getAttribute(RecordSharingController.SHAREE));
    assertTrue(
        (Boolean) mv.getModelMap().getAttribute(RecordSharingController.SYSADMIN_PUBLISHED_LINKS));
    makeStandardAssertions(mv);
  }

  private PaginationCriteria<RecordGroupSharing> createPagCrit() {
    return PaginationCriteria.createDefaultForClass(RecordGroupSharing.class);
  }

  List<RecordGroupSharing> getListOfSharedRecords() {
    List<RecordGroupSharing> rgs =
        (List) modelTss.get(RecordSharingController.SHARED_RECORDS_ATTR_NAME);
    return rgs;
  }

  @Test
  public void testUnshare() throws Exception {
    // create document, other user and group
    setUpDocumentGroupForPIUserAndShareRecord();
    recordSharingCtrller.list(model, mockPrincipal, createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();

    recordSharingCtrller.unshare(mockPrincipal, rgs.get(0).getId());
    recordSharingCtrller.list(model, mockPrincipal, createPagCrit());
    List<RecordGroupSharing> rgs2 = getListOfSharedRecords();
    // no longer in listing
    assertEquals(0, rgs2.size());
  }

  @Test
  public void testUpdatePermissions() throws Exception {
    /*
     * This test creates a shared document in a group with edit permissions. Then
     * using the record sharing controller, the permission is changed to read-only.
     * This is results in the 'other' user no longer being able to edit the
     * document.
     */
    // create document, other user and group
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();

    Thread.sleep(100);
    // doc was shared with edit permission, so we assert that this is true.
    logoutAndLoginAs(setup.user);
    assertEquals(
        EditStatus.EDIT_MODE,
        recordMgr.requestRecordEdit(
            setup.structuredDocument.getId(), setup.user, anySessionTracker()));
    logoutAndLoginAs(piUser);
    recordSharingCtrller.list(model, mockPrincipal, createPagCrit());
    List<RecordGroupSharing> rgs = getListOfSharedRecords();
    AjaxReturnObject<String> aro =
        recordSharingCtrller.updatePermissions(mockPrincipal, rgs.get(0).getId(), "read");
    assertNull(aro.getErrorMsg());
    recordSharingCtrller.list(model, mockPrincipal, createPagCrit());
    List<RecordGroupSharing> rgs2 = getListOfSharedRecords();

    assertEquals(PermissionType.READ, rgs2.get(0).getPermType());
    // and 'other' now can't edit the document
    logoutAndLoginAs(setup.user);
    assertEquals(
        EditStatus.CANNOT_EDIT_NO_PERMISSION,
        recordMgr.requestRecordEdit(
            setup.structuredDocument.getId(), setup.user, anySessionTracker()));
  }
}

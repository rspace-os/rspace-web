package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.TestGroup;
import java.security.Principal;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Pins down the externally visible behaviour of a group shared-folder listing: which records each
 * kind of user sees, the per-record action-permission flags (rename, delete, copy, export, send) in
 * the {@link ActionPermissionsDTO}, and move semantics through the real move endpoint.
 *
 * <p>These tests are the safety net for performance work on the listing path: any change to how the
 * listing computes permissions must keep every assertion here green.
 */
@WebAppConfiguration
public class WorkspaceSharedListingPermissionsMVCIT extends MVCTestBase {

  // the move-rule rejection, as opposed to an earlier read-permission failure
  private static final String MOVE_REJECTED = "does not have permission to move record";

  private @Autowired WorkspaceController workspaceController;
  @Mock private AuditTrailService auditService;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockHttpSession session;

  private TestGroup group;
  private User pi;
  private User owner;
  private User sharee;
  private StructuredDocument docSharedRead;
  private StructuredDocument docSharedEdit;
  private Long sharedFolderId;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    openMocks(this);
    MockServletContext mockServletCtxt = new MockServletContext();
    mockServletCtxt.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    workspaceController.setServletContext(mockServletCtxt);
    workspaceController.setAuditService(auditService);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    session = new MockHttpSession();
  }

  /**
   * A LabGroup of a PI and two members; member 'owner' shares one document read-only and one
   * edit-permission with the whole group.
   */
  private void createSharedDocsFixture() throws Exception {
    group = createTestGroup(2);
    pi = group.getPi();
    owner = group.u1();
    sharee = group.u2();
    logoutAndLoginAs(owner);
    docSharedRead = createBasicDocumentInRootFolderWithText(owner, "shared for read");
    docSharedEdit = createBasicDocumentInRootFolderWithText(owner, "shared for edit");
    shareRecordWithGroup(owner, group.getGroup(), docSharedRead);
    sharingMgr.shareRecord(
        owner,
        docSharedEdit.getId(),
        new ShareConfigElement[] {new ShareConfigElement(group.getGroup().getId(), "write")});
    sharedFolderId = group.getGroup().getCommunalGroupFolderId();
  }

  private ISearchResults<BaseRecord> listSharedFolderAs(User subject) throws Exception {
    return listFolderAs(subject, sharedFolderId);
  }

  @SuppressWarnings("unchecked")
  private ISearchResults<BaseRecord> listFolderAs(User subject, Long folderId) throws Exception {
    logoutAndLoginAs(subject);
    modelTss.clear();
    mockPrincipal = subject::getUsername;
    workspaceController.listWorkspaceFolderById(
        folderId, new WorkspaceSettings(), "", model, request, session, response, mockPrincipal);
    return (ISearchResults<BaseRecord>) modelTss.get("searchResults");
  }

  private ActionPermissionsDTO permissionsDto() {
    return (ActionPermissionsDTO) modelTss.get("createPermission");
  }

  private boolean listingContains(ISearchResults<BaseRecord> listing, BaseRecord record) {
    return listing.getResults().stream().anyMatch(br -> br.getId().equals(record.getId()));
  }

  private void assertFlags(
      ActionPermissionsDTO dto,
      BaseRecord record,
      boolean rename,
      boolean delete,
      boolean copy,
      boolean export,
      boolean send) {
    Map<String, Boolean> flags = dto.getInstancePermissions().get(record.getId());
    assertNotNull(flags, "no action permissions for " + record.getName());
    assertEquals(rename, flags.get("RENAME"), "RENAME flag for " + record.getName());
    assertEquals(delete, flags.get("DELETE"), "DELETE flag for " + record.getName());
    assertEquals(copy, flags.get("COPY"), "COPY flag for " + record.getName());
    assertEquals(export, flags.get("EXPORT"), "EXPORT flag for " + record.getName());
    assertEquals(send, flags.get(PermissionType.SEND.name()), "SEND flag for " + record.getName());
  }

  @Test
  public void ownerSeesSharedDocsWithOwnerOptionsButNoMove() throws Exception {
    createSharedDocsFixture();
    ISearchResults<BaseRecord> listing = listSharedFolderAs(owner);
    assertTrue(listingContains(listing, docSharedRead));
    assertTrue(listingContains(listing, docSharedEdit));
    ActionPermissionsDTO dto = permissionsDto();
    // the owner manages the record itself, but organising the group folder (move) is not
    // theirs: sharing controls where the shared copy lives
    assertFlags(dto, docSharedRead, true, true, true, true, false);
    assertFlags(dto, docSharedEdit, true, true, true, true, false);
  }

  @Test
  public void shareeSeesSharedDocsWithExportOnlyOptions() throws Exception {
    createSharedDocsFixture();
    ISearchResults<BaseRecord> listing = listSharedFolderAs(sharee);
    assertTrue(listingContains(listing, docSharedRead));
    assertTrue(listingContains(listing, docSharedEdit));
    ActionPermissionsDTO dto = permissionsDto();
    // a sharee is not the owner: export only, whatever the share permission
    assertFlags(dto, docSharedRead, false, false, false, true, false);
    assertFlags(dto, docSharedEdit, false, false, false, true, false);
  }

  @Test
  public void groupPiSeesSharedDocsAndCanMoveThemWithinGroup() throws Exception {
    createSharedDocsFixture();
    ISearchResults<BaseRecord> listing = listSharedFolderAs(pi);
    assertTrue(listingContains(listing, docSharedRead));
    assertTrue(listingContains(listing, docSharedEdit));
    ActionPermissionsDTO dto = permissionsDto();
    // the group PI organises the group folder: rename/delete/move, but no copy (not the owner)
    assertFlags(dto, docSharedRead, true, true, false, true, true);
    assertFlags(dto, docSharedEdit, true, true, false, true, true);
  }

  @Test
  public void nonMemberCannotListSharedFolder() throws Exception {
    createSharedDocsFixture();
    User nonMember = createInitAndLoginAnyUser();
    assertThrows(AuthorizationException.class, () -> listSharedFolderAs(nonMember));
  }

  @Test
  public void piCanMoveSharedDocWithinGroupSharedFolderThroughMoveEndpoint() throws Exception {
    createSharedDocsFixture();
    logoutAndLoginAs(pi);
    Folder subfolder = folderMgr.createNewFolder(sharedFolderId, "groupSubfolder", pi);

    moveAs(pi, docSharedRead, sharedFolderId, subfolder.getId());

    assertNotNull(modelTss.get("successMsg"), "move within the group shared folder failed");
    assertNull(modelTss.get("errorMsg"));
    assertTrue(listingContains(listFolderAs(pi, subfolder.getId()), docSharedRead));
    assertFalse(listingContains(listSharedFolderAs(pi), docSharedRead));
  }

  @Test
  public void ownerCannotMoveSharedDocWithinGroupSharedFolder() throws Exception {
    createSharedDocsFixture();
    logoutAndLoginAs(pi);
    Folder subfolder = folderMgr.createNewFolder(sharedFolderId, "groupSubfolder", pi);

    // matches the SEND=false flag the listing shows the owner: the endpoint rejects too
    AuthorizationException rejected =
        assertThrows(
            AuthorizationException.class,
            () -> moveAs(owner, docSharedRead, sharedFolderId, subfolder.getId()));
    assertThat(rejected.getMessage()).contains(MOVE_REJECTED);
    assertTrue(listingContains(listSharedFolderAs(owner), docSharedRead));
  }

  @Test
  public void moveAcrossGroupSharedFoldersIsRejectedThroughMoveEndpoint() throws Exception {
    createSharedDocsFixture();
    // the same PI runs a second group, so has full powers over both group folders
    logoutAndLoginAs(pi);
    Group otherGroup = createGroupForUsers(pi, pi.getUsername(), null, pi);
    Long otherSharedFolderId = otherGroup.getCommunalGroupFolderId();
    permissionUtils.refreshCache();

    // the PI may move the doc within the group it was shared with, but not into another group's
    // folder, even though they could receive records there
    AuthorizationException rejected =
        assertThrows(
            AuthorizationException.class,
            () -> moveAs(pi, docSharedRead, sharedFolderId, otherSharedFolderId));
    assertThat(rejected.getMessage()).contains(MOVE_REJECTED);
    assertTrue(listingContains(listSharedFolderAs(pi), docSharedRead));
    assertFalse(listingContains(listFolderAs(pi, otherSharedFolderId), docSharedRead));
  }

  private void moveAs(User subject, BaseRecord toMove, Long sourceFolderId, Long targetFolderId)
      throws Exception {
    logoutAndLoginAs(subject);
    modelTss.clear();
    Principal principal = subject::getUsername;
    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setParentFolderId(sourceFolderId);
    workspaceController.move(
        new Long[] {toMove.getId()},
        targetFolderId.toString(),
        null,
        settings,
        model,
        principal,
        request,
        session,
        response);
  }
}

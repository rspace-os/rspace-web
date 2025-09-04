package com.researchspace.service.impl;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiShareInfo;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.dao.DataAccessException;

public class ShareApiServiceTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Mock private SharingHandler recordShareHandler;
  @Mock private RecordSharingManager recordShareMgr;
  @Mock private FolderDao folderDao;
  @Mock private RecordGroupSharingDao recordGroupSharingDao;

  @InjectMocks ShareApiServiceImpl shareApiService;

  User sharer = TestFactory.createAnyUserWithRole("any", Role.PI_ROLE.getName());

  @Test
  public void testGetAllSharesForDocPopulatesNullTargetFolderForGroup() throws Exception {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing groupSharing = createMockGroupSharing(docId, null);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    Folder mockGroupFolder = new Folder();
    mockGroupFolder.setId(456L);
    mockGroupFolder.setName("Group Shared Folder");

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    when(folderDao.getSharedFolderForGroup(groupSharing.getSharee().asGroup()))
        .thenReturn(mockGroupFolder);

    List<ApiShareInfo> result = shareApiService.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Target folder should be populated", groupSharing.getTargetFolder());
    assertEquals(mockGroupFolder.getId(), groupSharing.getTargetFolder().getId());
  }

  @Test
  public void testGetAllSharesForDocPopulatesNullTargetFolderForUser() throws Exception {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing userSharing = createMockUserSharing(docId);
    List<RecordGroupSharing> sharingList = List.of(userSharing);
    BaseRecord sharedRecord = userSharing.getShared();

    Folder mockUserFolder = new Folder();
    mockUserFolder.setId(789L);
    mockUserFolder.setName("Individual Shared Folder");

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    when(folderDao.getIndividualSharedFolderForUsers(
            userSharing.getSharedBy(), userSharing.getSharee().asUser(), sharedRecord))
        .thenReturn(mockUserFolder);

    List<ApiShareInfo> result = shareApiService.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Target folder should be populated", userSharing.getTargetFolder());
    assertEquals(mockUserFolder.getId(), userSharing.getTargetFolder().getId());
  }

  @Test
  public void testGetAllSharesForDocHandlesFolderLookupFailure() throws Exception {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing groupSharing = createMockGroupSharing(docId, null);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    when(folderDao.getSharedFolderForGroup(groupSharing.getSharee().asGroup()))
        .thenThrow(new RuntimeException("Database error"));

    List<ApiShareInfo> result = shareApiService.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNull(
        "Target folder should remain null when lookup fails", groupSharing.getTargetFolder());
  }

  @Test
  public void testGetAllSharesForDocPreservesExistingTargetFolder() throws Exception {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    Folder existingFolder = new Folder();
    existingFolder.setId(999L);
    existingFolder.setName("Existing Target Folder");

    RecordGroupSharing groupSharing = createMockGroupSharing(docId, existingFolder);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);

    List<ApiShareInfo> result = shareApiService.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Target folder should be preserved", groupSharing.getTargetFolder());
    assertEquals(existingFolder.getId(), groupSharing.getTargetFolder().getId());

    // Verify that folder lookup was not called since folder already exists
    verify(folderDao, times(0)).getSharedFolderForGroup(any());
  }

  @Test
  public void testGetAllSharesForDocPopulatesSnippetFolder() throws Exception {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing snippetSharing = createMockGroupSharingForSnippet(docId);
    List<RecordGroupSharing> sharingList = List.of(snippetSharing);

    Folder mockSnippetFolder = new Folder();
    mockSnippetFolder.setId(777L);
    mockSnippetFolder.setName("Snippet Shared Folder");

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    when(folderDao.getSharedSnippetFolderForGroup(snippetSharing.getSharee().asGroup()))
        .thenReturn(mockSnippetFolder);

    List<ApiShareInfo> result = shareApiService.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Snippet folder should be populated", snippetSharing.getTargetFolder());
    assertEquals(mockSnippetFolder.getId(), snippetSharing.getTargetFolder().getId());
  }

  @Test
  public void testUpdateSharePermissionOnlyForGroupShare() throws Exception {
    Long shareId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing existingShare = createMockGroupSharing(456L, null);
    SharePost updatePost = createSharePostForPermissionUpdate("EDIT", existingShare);

    when(recordShareMgr.get(shareId)).thenReturn(existingShare);
    when(recordShareMgr.updatePermissionForRecord(eq(shareId), eq("EDIT"), eq(user.getUsername())))
        .thenReturn(null); // No errors

    RecordGroupSharing updatedShare = createMockGroupSharing(456L, null);
    when(recordShareMgr.get(shareId)).thenReturn(updatedShare);

    ApiSharingResult result = shareApiService.updateShare(shareId, updatePost, user);

    assertNotNull(result);
    assertEquals(1, result.getShareInfos().size());
    assertEquals(0, result.getFailedShares().size());
  }

  @Test
  public void testUpdateSharePermissionOnlyForUserShare() throws Exception {
    Long shareId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing existingShare = createMockUserSharing(456L);
    SharePost updatePost = createUserSharePostForPermissionUpdate("READ", existingShare);

    when(recordShareMgr.get(shareId)).thenReturn(existingShare);
    when(recordShareMgr.updatePermissionForRecord(eq(shareId), eq("READ"), eq(user.getUsername())))
        .thenReturn(null); // No errors

    RecordGroupSharing updatedShare = createMockUserSharing(456L);
    when(recordShareMgr.get(shareId)).thenReturn(updatedShare);

    ApiSharingResult result = shareApiService.updateShare(shareId, updatePost, user);

    assertNotNull(result);
    assertEquals(1, result.getShareInfos().size());
    assertEquals(0, result.getFailedShares().size());
  }

  @Test
  public void testUpdateShareFolderLocationForGroupShare() throws Exception {
    Long shareId = 123L;
    User user = TestFactory.createAnyUser("testuser");
    Long newFolderId = 999L;

    RecordGroupSharing existingShare = createMockGroupSharing(456L, null);
    SharePost updatePost = createSharePostForFolderUpdate(newFolderId, existingShare);

    Folder newTargetFolder = new Folder();
    newTargetFolder.setId(newFolderId);
    newTargetFolder.setName("New Target Folder");

    when(recordShareMgr.get(shareId)).thenReturn(existingShare);
    when(folderDao.get(newFolderId)).thenReturn(newTargetFolder);
    when(recordGroupSharingDao.save(existingShare)).thenReturn(existingShare);

    ApiSharingResult result = shareApiService.updateShare(shareId, updatePost, user);

    assertNotNull(result);
    assertEquals(1, result.getShareInfos().size());
    assertEquals(0, result.getFailedShares().size());
    assertEquals(newTargetFolder, existingShare.getTargetFolder());
  }

  @Test
  public void testUpdateSharePermissionError() throws Exception {
    Long shareId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing existingShare = createMockGroupSharing(456L, null);
    SharePost updatePost = createSharePostForPermissionUpdate("EDIT", existingShare);

    ErrorList errors = new ErrorList();
    errors.addErrorMsg("Permission update failed");

    when(recordShareMgr.get(shareId)).thenReturn(existingShare);
    when(recordShareMgr.updatePermissionForRecord(eq(shareId), eq("EDIT"), eq(user.getUsername())))
        .thenReturn(errors);

    try {
      shareApiService.updateShare(shareId, updatePost, user);
    } catch (IllegalArgumentException e) {
      assertEquals("Could not update permission: Permission update failed", e.getMessage());
    }
  }

  @Test(expected = NotFoundException.class)
  public void testUpdateShareNonExistentShare() throws Exception {
    Long shareId = 999L;
    User user = TestFactory.createAnyUser("testuser");
    SharePost updatePost = createValidSharePost();

    when(recordShareMgr.get(shareId)).thenReturn(null);

    shareApiService.updateShare(shareId, updatePost, user);
  }

  @Test(expected = NotFoundException.class)
  public void testDeleteShareNonExistentShare() throws Exception {
    Long shareId = 999L;
    User user = TestFactory.createAnyUser("testuser");

    when(recordShareHandler.unshare(shareId, user))
        .thenThrow(new DataAccessException("Share not found") {});

    shareApiService.deleteShare(shareId, user);
  }

  @Test
  public void testUpdateShareFolderNotFound() throws Exception {
    Long shareId = 123L;
    User user = TestFactory.createAnyUser("testuser");
    Long nonExistentFolderId = 999L;

    RecordGroupSharing existingShare = createMockGroupSharing(456L, null);
    SharePost updatePost = createSharePostForFolderUpdate(nonExistentFolderId, existingShare);

    when(recordShareMgr.get(shareId)).thenReturn(existingShare);
    when(folderDao.get(nonExistentFolderId)).thenReturn(null);

    try {
      shareApiService.updateShare(shareId, updatePost, user);
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Could not update folder location: Target folder with id 999 not found", e.getMessage());
    }
  }

  // Helper methods to create mock objects
  private RecordGroupSharing createMockGroupSharing(Long docId, Folder targetFolder) {
    RecordGroupSharing sharing = mock(RecordGroupSharing.class);
    BaseRecord sharedRecord = mock(BaseRecord.class);
    Group mockGroup = mock(Group.class);
    User mockGroupAsUser = mock(User.class);

    when(sharedRecord.getId()).thenReturn(docId);
    when(sharedRecord.isSnippet()).thenReturn(false);
    when(sharing.getShared()).thenReturn(sharedRecord);
    when(sharing.getSharee()).thenReturn(mockGroupAsUser);
    when(mockGroupAsUser.isGroup()).thenReturn(true);
    when(mockGroupAsUser.asGroup()).thenReturn(mockGroup);
    when(sharing.getTargetFolder()).thenReturn(targetFolder);

    return sharing;
  }

  private RecordGroupSharing createMockUserSharing(Long docId) {
    RecordGroupSharing sharing = mock(RecordGroupSharing.class);
    BaseRecord sharedRecord = mock(BaseRecord.class);
    User mockSharee = mock(User.class);
    User mockSharedBy = mock(User.class);

    when(sharedRecord.getId()).thenReturn(docId);
    when(sharing.getShared()).thenReturn(sharedRecord);
    when(sharing.getSharee()).thenReturn(mockSharee);
    when(mockSharee.isGroup()).thenReturn(false);
    when(mockSharee.isUser()).thenReturn(true);
    when(mockSharee.asUser()).thenReturn(mockSharee);
    when(sharing.getSharedBy()).thenReturn(mockSharedBy);
    when(sharing.getTargetFolder()).thenReturn(null);

    return sharing;
  }

  private RecordGroupSharing createMockGroupSharingForSnippet(Long docId) {
    RecordGroupSharing sharing = createMockGroupSharing(docId, null);
    BaseRecord sharedRecord = sharing.getShared();
    when(sharedRecord.isSnippet()).thenReturn(true);
    return sharing;
  }

  private SharePost createSharePostForPermissionUpdate(
      String permission, RecordGroupSharing existingShare) {
    SharePost post = new SharePost();
    post.setItemsToShare(List.of(existingShare.getShared().getId()));

    GroupSharePostItem groupItem = new GroupSharePostItem();
    groupItem.setId(existingShare.getSharee().getId());
    groupItem.setPermission(permission);
    post.setGroupSharePostItems(List.of(groupItem));
    post.setUserSharePostItems(emptyList());

    return post;
  }

  private SharePost createUserSharePostForPermissionUpdate(
      String permission, RecordGroupSharing existingShare) {
    SharePost post = new SharePost();
    post.setItemsToShare(List.of(existingShare.getShared().getId()));

    UserSharePostItem userItem = new UserSharePostItem();
    userItem.setId(existingShare.getSharee().getId());
    userItem.setPermission(permission);
    post.setUserSharePostItems(List.of(userItem));
    post.setGroupSharePostItems(emptyList());

    return post;
  }

  private SharePost createSharePostForFolderUpdate(
      Long newFolderId, RecordGroupSharing existingShare) {
    SharePost post = new SharePost();
    post.setItemsToShare(List.of(existingShare.getShared().getId()));

    GroupSharePostItem groupItem = new GroupSharePostItem();
    groupItem.setId(existingShare.getSharee().getId());
    groupItem.setSharedFolderId(newFolderId);
    groupItem.setPermission("READ"); // Keep existing permission
    post.setGroupSharePostItems(List.of(groupItem));
    post.setUserSharePostItems(emptyList());

    return post;
  }

  private SharePost createValidSharePost() {
    SharePost post = new SharePost();
    post.setItemsToShare(List.of(1L));
    GroupSharePostItem gsi = new GroupSharePostItem();
    gsi.setId(2L);
    gsi.setPermission("READ");
    post.setGroupSharePostItems(List.of(gsi));
    post.setUserSharePostItems(emptyList());
    return post;
  }
}

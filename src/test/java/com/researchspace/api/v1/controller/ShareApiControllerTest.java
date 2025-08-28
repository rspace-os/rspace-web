package com.researchspace.api.v1.controller;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiShareInfo;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.RecordSharingManager;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ShareApiControllerTest extends JavaxValidatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock RecordSharingManager recordShareMgr;
  @Mock DetailedRecordInformationProvider detailedRecordInformationProvider;
  @Mock FolderDao folderDao;

  @InjectMocks ShareApiController controller;
  User sharer = TestFactory.createAnyUserWithRole("any", Role.PI_ROLE.getName());

  @Test
  public void testShareItems() {
    SharePost post = createValidSharePost();
    assertNErrors(post, 0);
    setPermission(post, "EDIT");
    assertNErrors(post, 0);
    setPermission(post, "edit");
    assertNErrors(post, 0);

    post.setItemsToShare(Collections.emptyList());
    assertNErrors(post, 1);

    post = createValidSharePost();
    setPermission(post, "unknown permission type");
    assertNErrors(post, 1);

    // >= 1 user or groups required
    post = createValidSharePost();
    post.setGroupSharePostItems(emptyList());
    post.setUserSharePostItems(emptyList());

    assertNErrors(post, 1);
  }

  @Test
  public void testGetAllSharesForDocPopulatesNullTargetFolderForGroup() {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing groupSharing = createMockGroupSharing(docId, null);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    Folder mockGroupFolder = new Folder();
    mockGroupFolder.setId(456L);
    mockGroupFolder.setName("Group Shared Folder");

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    DetailedRecordInformation mockDetailedInfo = mock(DetailedRecordInformation.class);
    when(detailedRecordInformationProvider.getDetailedRecordInformation(
            eq(docId), eq(user), any(), any()))
        .thenReturn(mockDetailedInfo);
    when(folderDao.getSharedFolderForGroup(groupSharing.getSharee().asGroup()))
        .thenReturn(mockGroupFolder);

    List<ApiShareInfo> result = controller.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Target folder should be populated", groupSharing.getTargetFolder());
    assertEquals(mockGroupFolder.getId(), groupSharing.getTargetFolder().getId());
  }

  @Test
  public void testGetAllSharesForDocPopulatesNullTargetFolderForUser() {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing userSharing = createMockUserSharing(docId);
    List<RecordGroupSharing> sharingList = List.of(userSharing);
    BaseRecord sharedRecord = userSharing.getShared();

    Folder mockUserFolder = new Folder();
    mockUserFolder.setId(789L);
    mockUserFolder.setName("Individual Shared Folder");

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    DetailedRecordInformation mockDetailedInfo = mock(DetailedRecordInformation.class);
    when(detailedRecordInformationProvider.getDetailedRecordInformation(
            eq(docId), eq(user), any(), any()))
        .thenReturn(mockDetailedInfo);
    when(folderDao.getIndividualSharedFolderForUsers(
            userSharing.getSharedBy(), userSharing.getSharee().asUser(), sharedRecord))
        .thenReturn(mockUserFolder);

    List<ApiShareInfo> result = controller.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Target folder should be populated", userSharing.getTargetFolder());
    assertEquals(mockUserFolder.getId(), userSharing.getTargetFolder().getId());
  }

  @Test
  public void testGetAllSharesForDocHandlesFolderLookupFailure() {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing groupSharing = createMockGroupSharing(docId, null);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    DetailedRecordInformation mockDetailedInfo = mock(DetailedRecordInformation.class);
    when(detailedRecordInformationProvider.getDetailedRecordInformation(
            eq(docId), eq(user), any(), any()))
        .thenReturn(mockDetailedInfo);
    when(folderDao.getSharedFolderForGroup(groupSharing.getSharee().asGroup()))
        .thenThrow(new RuntimeException("Database error"));

    List<ApiShareInfo> result = controller.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNull(
        "Target folder should remain null when lookup fails", groupSharing.getTargetFolder());
  }

  @Test
  public void testGetAllSharesForDocPreservesExistingTargetFolder() {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    Folder existingFolder = new Folder();
    existingFolder.setId(999L);
    existingFolder.setName("Existing Folder");
    RecordGroupSharing groupSharing = createMockGroupSharing(docId, existingFolder);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    // Setup mocks
    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    DetailedRecordInformation mockDetailedInfo = mock(DetailedRecordInformation.class);
    when(detailedRecordInformationProvider.getDetailedRecordInformation(
            eq(docId), eq(user), any(), any()))
        .thenReturn(mockDetailedInfo);

    List<ApiShareInfo> result = controller.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Existing target folder should be preserved", groupSharing.getTargetFolder());
    assertEquals(existingFolder.getId(), groupSharing.getTargetFolder().getId());
  }

  @Test
  public void testGetAllSharesForDocPopulatesSnippetFolder() {
    Long docId = 123L;
    User user = TestFactory.createAnyUser("testuser");

    RecordGroupSharing groupSharing = createMockGroupSharingForSnippet(docId);
    List<RecordGroupSharing> sharingList = List.of(groupSharing);

    Folder mockSnippetFolder = new Folder();
    mockSnippetFolder.setId(555L);
    mockSnippetFolder.setName("Group Snippet Folder");

    when(recordShareMgr.getRecordSharingInfo(docId)).thenReturn(sharingList);
    DetailedRecordInformation mockDetailedInfo = mock(DetailedRecordInformation.class);
    when(detailedRecordInformationProvider.getDetailedRecordInformation(
            eq(docId), eq(user), any(), any()))
        .thenReturn(mockDetailedInfo);
    when(folderDao.getSharedSnippetFolderForGroup(groupSharing.getSharee().asGroup()))
        .thenReturn(mockSnippetFolder);

    List<ApiShareInfo> result = controller.getAllSharesForDoc(docId, user);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertNotNull("Target folder should be populated", groupSharing.getTargetFolder());
    assertEquals(mockSnippetFolder.getId(), groupSharing.getTargetFolder().getId());
  }

  private RecordGroupSharing createMockGroupSharing(Long docId, Folder targetFolder) {
    Group mockGroup = TestFactory.createAnyGroup(sharer, new User[] {});
    mockGroup.setUniqueName("testGroup");

    BaseRecord mockRecord = mock(BaseRecord.class);
    when(mockRecord.getId()).thenReturn(docId);
    when(mockRecord.getOwner()).thenReturn(sharer);
    when(mockRecord.isSnippet()).thenReturn(false);

    RecordGroupSharing sharing = new RecordGroupSharing(mockGroup, mockRecord);
    sharing.setSharedBy(sharer);
    sharing.setTargetFolder(targetFolder);

    return sharing;
  }

  private RecordGroupSharing createMockUserSharing(Long docId) {
    User mockUser = TestFactory.createAnyUser("sharee");
    mockUser.setId(456L);

    BaseRecord mockRecord = mock(BaseRecord.class);
    when(mockRecord.getId()).thenReturn(docId);
    when(mockRecord.getOwner()).thenReturn(sharer);
    when(mockRecord.isSnippet()).thenReturn(false);

    RecordGroupSharing sharing = new RecordGroupSharing(mockUser, mockRecord);
    sharing.setSharedBy(sharer);
    sharing.setTargetFolder(null);

    return sharing;
  }

  private RecordGroupSharing createMockGroupSharingForSnippet(Long docId) {
    Group mockGroup = TestFactory.createAnyGroup(sharer, new User[] {});
    mockGroup.setUniqueName("testGroup");

    BaseRecord mockSnippet = mock(BaseRecord.class);
    when(mockSnippet.getId()).thenReturn(docId);
    when(mockSnippet.getOwner()).thenReturn(sharer);
    when(mockSnippet.isSnippet()).thenReturn(true);

    RecordGroupSharing sharing = new RecordGroupSharing(mockGroup, mockSnippet);
    sharing.setSharedBy(sharer);
    sharing.setTargetFolder(null);

    return sharing;
  }

  private void setPermission(SharePost post, String perm) {
    post.getGroupSharePostItems().get(0).setPermission(perm);
  }

  static SharePost createValidSharePost() {
    return SharePost.builder()
        .itemToShare(1L)
        .groupSharePostItem(
            GroupSharePostItem.builder().id(2L).permission("READ").sharedFolderId(3L).build())
        .userSharePostItem(UserSharePostItem.builder().id(2L).permission("EDIT").build())
        .build();
  }
}

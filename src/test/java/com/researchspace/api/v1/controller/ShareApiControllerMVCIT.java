package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.DocumentShares;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePermissionUpdate;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.TreeViewItem;
import com.researchspace.testutils.TestGroup;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebAppConfiguration
public class ShareApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void cannotShareGalleryItem() throws Exception {
    TestGroup testGrp1 = createTestGroup(1, new TestGroupConfig(true));
    User sharer = testGrp1.getUserByPrefix("u1");
    logoutAndLoginAs(sharer);
    EcatMediaFile anyMediaFile = addAudioFileToGallery(sharer);
    SharePost toPost = createValidSharePostWithGroup(testGrp1, anyMediaFile, "EDIT");
    String apiKey = createNewApiKeyForUser(sharer);
    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/share", sharer, toPost))
        .andExpect(status().is4xxClientError())
        .andReturn();
  }

  @Test
  public void canShareAndUnShareSnippetGalleryItem() throws Exception {
    TestGroup testGrp1 = createTestGroup(1, new TestGroupConfig(true));
    User sharer = testGrp1.getUserByPrefix("u1");
    logoutAndLoginAs(sharer);
    Snippet aSnippet = recordMgr.createSnippet("snippet", "some text", sharer);
    SharePost toPost = createValidSharePostWithGroup(testGrp1, aSnippet, "EDIT");
    String apiKey = createNewApiKeyForUser(sharer);
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/share", sharer, toPost))
            .andExpect(status().isCreated())
            .andReturn();
    makeSharingAssertions(sharer, aSnippet, apiKey, result);
  }

  private void makeSharingAssertions(User sharer, Record toShare, String apiKey, MvcResult result)
      throws Exception {
    ApiSharingResult shareResponse = getFromJsonResponseBody(result, ApiSharingResult.class);
    assertEquals(1, getShareCount(sharer, apiKey));
    Record shared = recordMgr.get(toShare.getId());
    assertTrue(shared.isShared());
    // test ?query param
    assertEquals(
        1, listSharesWithSearch(sharer, apiKey, toShare.getName()).getTotalHits().intValue());
    // now unshare it:
    unshare(sharer, apiKey, shareResponse, status().isNoContent());
    shared = recordMgr.get(toShare.getId());
    assertFalse(shared.isShared());
    assertEquals(0, getShareCount(sharer, apiKey));
    // unshare again
    unshare(sharer, apiKey, shareResponse, status().isNotFound());
  }

  @Test
  public void simpleShareWithValidGroup() throws Exception {

    TestGroup testGrp1 = createTestGroup(3, new TestGroupConfig(true));
    // TestGroup testGrp2 = createTestGroup(3, new TestGroupConfig(true));
    User sharer = testGrp1.getUserByPrefix("u1");
    logoutAndLoginAs(sharer);
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(sharer, "anytext");
    SharePost toPost = createValidSharePostWithGroup(testGrp1, toShare, "EDIT");
    String apiKey = createNewApiKeyForUser(sharer);

    assertEquals(0, getShareCount(sharer, apiKey));

    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/share", sharer, toPost))
            .andExpect(status().isCreated())
            .andExpect(nSharedItemsInResponse(1))
            .andExpect(nFailedItems(0))
            .andReturn();
    makeSharingAssertions(sharer, toShare, apiKey, result);
  }

  private int getShareCount(User sharer, String apiKey) throws Exception {
    return listShares(sharer, apiKey).getTotalHits().intValue();
  }

  private ApiShareSearchResult listShares(User sharer, String apiKey) throws Exception {
    MvcResult getResult = mockMvc.perform(createShareGetBuilder(sharer, apiKey)).andReturn();
    return getFromJsonResponseBody(getResult, ApiShareSearchResult.class);
  }

  private ApiShareSearchResult listSharesWithSearch(User sharer, String apiKey, String term)
      throws Exception {
    MvcResult getResult =
        mockMvc.perform(createShareGetBuilder(sharer, apiKey).param("query", term)).andReturn();
    return getFromJsonResponseBody(getResult, ApiShareSearchResult.class);
  }

  private MockHttpServletRequestBuilder createShareGetBuilder(User sharer, String apiKey) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/share", sharer);
  }

  private void unshare(
      User sharer, String apiKey, ApiSharingResult shareResponse, ResultMatcher expectedCode)
      throws Exception {
    mockMvc
        .perform(
            createBuilderForDelete(
                apiKey, "/share/{id}", sharer, shareResponse.getShareInfos().get(0).getId()))
        .andExpect(expectedCode)
        .andReturn();
  }

  private ResultMatcher nFailedItems(int n) {
    return jsonPath("$.failedShares.length()", is(n));
  }

  private ResultMatcher nSharedItemsInResponse(int n) {
    return jsonPath("$.shareInfos.length()", is(n));
  }

  @Test
  public void shareToSubfolder() throws Exception {
    TestGroup testGrp1 = createTestGroup(3, new TestGroupConfig(true));
    User sharer = testGrp1.getUserByPrefix("u1");
    logoutAndLoginAs(sharer);
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(sharer, "anytext");
    String apiKey = createNewApiKeyForUser(sharer);

    // create subfolder of sharing folder
    ApiFolder subfolder =
        createSubfolderOfFolder(testGrp1.getGroup().getCommunalGroupFolderId(), sharer, apiKey);
    ApiFolder subfolder2 = createSubfolderOfFolder(subfolder.getId(), sharer, apiKey);
    SharePost toPost =
        createSharePostWithGroupAndFolder(testGrp1, subfolder2.getId(), toShare, "EDIT");
    toPost.getGroupSharePostItems().get(0).setSharedFolderId(subfolder.getId());

    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/share", sharer, toPost))
            .andExpect(status().isCreated())
            .andExpect(nSharedItemsInResponse(1))
            .andExpect(nFailedItems(0))
            .andReturn();
    ApiSharingResult shareResponse = getFromJsonResponseBody(result, ApiSharingResult.class);
    assertEquals(toShare.getId(), shareResponse.getShareInfos().get(0).getSharedItemId());
  }

  @Test
  public void shareErrorHandling() throws Exception {
    TestGroup testGrp1 = createTestGroup(3, new TestGroupConfig(true));
    User other = createInitAndLoginAnyUser();
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(other, "anytext");
    String otherApiKey = createNewApiKeyForUser(other);

    // other is not member of group, can't share his doc.
    SharePost toPost = createValidSharePostWithGroup(testGrp1, toShare, "EDIT");
    MvcResult result = postAndExpectUnauthorized(other, toPost, otherApiKey);
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(ApiErrorCodes.AUTH.getCode(), error.getInternalCode());

    // pi IS member of group but can't share someone elses' work
    String piApiKey = createNewApiKeyForUser(testGrp1.getPi());
    postAndExpectUnauthorized(other, toPost, piApiKey);

    // now, sharer is in both groups and is sharing own doc, but has specified target folder in the
    // wrong group
    // doc will be shared into top-level group folder of specified group.
    TestGroup otherGroup = createTestGroup(2);
    addUsersToGroup(testGrp1.getPi(), otherGroup.getGroup());
    logoutAndLoginAs(testGrp1.getPi());
    StructuredDocument piDocToShare =
        createBasicDocumentInRootFolderWithText(testGrp1.getPi(), "anytext");
    toPost.setItemsToShare(toList(piDocToShare.getId()));
    toPost
        .getGroupSharePostItems()
        .get(0)
        .setSharedFolderId(otherGroup.getGroup().getCommunalGroupFolderId());
    mockMvc
        .perform(createBuilderForPostWithJSONBody(piApiKey, "/share", testGrp1.getPi(), toPost))
        .andExpect(status().isCreated())
        .andExpect(nSharedItemsInResponse(1))
        .andReturn();

    logoutAndLoginAs(testGrp1.getPi());
    assertEquals(
        1, getCountOfChildItems(testGrp1.getGroup().getCommunalGroupFolderId(), testGrp1.getPi()));
  }

  private int getCountOfChildItems(Long folderId, User user) {
    return folderMgr
        .getFolderListingForTreeView(
            folderId, PaginationCriteria.createDefaultForClass(TreeViewItem.class), user)
        .getHits();
  }

  private MvcResult postAndExpectUnauthorized(User other, SharePost toPost, String group1PIKey)
      throws Exception {

    return mockMvc
        .perform(createBuilderForPostWithJSONBody(group1PIKey, "/share", other, toPost))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  private ApiFolder createSubfolderOfFolder(Long parentFolder, User sharer, String apiKey)
      throws Exception {
    ApiFolder folderPost = new ApiFolder();
    folderPost.setParentFolderId(parentFolder);
    folderPost.setName("sf1");
    MvcResult result = this.mockMvc.perform(folderCreate(sharer, apiKey, folderPost)).andReturn();
    return getFromJsonResponseBody(result, ApiFolder.class);
  }

  private SharePost createValidSharePostWithGroup(
      TestGroup testGrp1, BaseRecord toShare, String permission) {
    return SharePost.builder()
        .itemToShare(toShare.getId())
        .groupSharePostItem(
            GroupSharePostItem.builder()
                .id(testGrp1.getGroup().getId())
                .permission(permission)
                .build())
        .build();
  }

  private SharePost createSharePostWithGroupAndFolder(
      TestGroup testGrp1, Long sharingTargetFolder, StructuredDocument toShare, String permission) {
    return SharePost.builder()
        .itemToShare(toShare.getId())
        .groupSharePostItem(
            GroupSharePostItem.builder()
                .id(testGrp1.getGroup().getId())
                .permission(permission)
                .sharedFolderId(sharingTargetFolder)
                .build())
        .build();
  }

  @Test
  public void testUpdateSharePermissions() throws Exception {
    TestGroup group = createTestGroup(2);
    User user = group.getPi();
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(user, "test");
    String apiKey = createNewApiKeyForUser(user);

    SharePost sharePost = createValidSharePostWithGroup(group, toShare, "READ");
    MvcResult createResult =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/share", user, sharePost))
            .andExpect(status().isCreated())
            .andReturn();

    ApiSharingResult shareResult = getFromJsonResponseBody(createResult, ApiSharingResult.class);
    Long shareId = shareResult.getShareInfos().get(0).getId();

    SharePermissionUpdate update = new SharePermissionUpdate();
    update.setShareId(shareId);
    update.setPermission("EDIT");

    // update permission
    mockMvc
        .perform(createBuilderForPutWithJSONBody(apiKey, "/share/", user, update))
        .andExpect(status().isNoContent())
        .andReturn();

    MvcResult getAllSharesResult =
        mockMvc
            .perform(get("/api/v1/share/document/" + toShare.getId()).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    DocumentShares updated =
        new ObjectMapper()
            .readValue(
                getAllSharesResult.getResponse().getContentAsString(), new TypeReference<>() {});

    assertEquals(
        DocumentShares.PermissionType.EDIT, updated.getDirectShares().get(0).getPermission());

    // change permission back to READ
    update.setPermission("READ");
    mockMvc
        .perform(createBuilderForPutWithJSONBody(apiKey, "/share/", user, update))
        .andExpect(status().isNoContent())
        .andReturn();

    MvcResult update2Result =
        mockMvc
            .perform(get("/api/v1/share/document/" + toShare.getId()).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    DocumentShares updated2 =
        new ObjectMapper()
            .readValue(update2Result.getResponse().getContentAsString(), new TypeReference<>() {});
    assertEquals(
        DocumentShares.PermissionType.READ, updated2.getDirectShares().get(0).getPermission());
  }

  @Test
  public void testGetAllSharesForDocument() throws Exception {
    TestGroup group = createTestGroup(2);
    User sharer = group.getPi();
    logoutAndLoginAs(sharer);

    TestGroup secondGroup = createTestGroup(1);
    addUsersToGroup(sharer, secondGroup.getGroup());
    User userToShareWith = secondGroup.getUserByPrefix("u1");

    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(sharer, "test document");
    toShare.setOwner(sharer);
    String apiKey = createNewApiKeyForUser(sharer);

    SharePost sharePost = createValidSharePostWithGroup(group, toShare, "READ");
    MvcResult createResult =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/share", sharer, sharePost))
            .andExpect(status().isCreated())
            .andReturn();

    ApiSharingResult shareResult = getFromJsonResponseBody(createResult, ApiSharingResult.class);
    assertEquals(1, shareResult.getShareInfos().size());

    // Share doc with user from 2nd group
    SharePost userSharePost =
        SharePost.builder()
            .itemToShare(toShare.getId())
            .userSharePostItem(
                UserSharePostItem.builder().id(userToShareWith.getId()).permission("EDIT").build())
            .build();

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/share", sharer, userSharePost))
        .andExpect(status().isCreated())
        .andReturn();

    // Get all shares for the document
    MvcResult getAllSharesResult =
        mockMvc
            .perform(get("/api/v1/share/document/" + toShare.getId()).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    DocumentShares docShares =
        new ObjectMapper()
            .readValue(
                getAllSharesResult.getResponse().getContentAsString(), new TypeReference<>() {});

    assertEquals(toShare.getId(), docShares.getSharedDocId(), 0L);
    assertEquals(toShare.getName(), docShares.getSharedDocName());

    List<DocumentShares.Share> directShares = docShares.getDirectShares();
    assertEquals(2, directShares.size());

    DocumentShares.Share groupShare =
        docShares.getDirectShares().stream()
            .filter(s -> s.getRecipientType().equals(DocumentShares.RecipientType.GROUP))
            .findFirst()
            .orElse(null);

    DocumentShares.Share userShare =
        docShares.getDirectShares().stream()
            .filter(s -> s.getRecipientType().equals(DocumentShares.RecipientType.USER))
            .findFirst()
            .orElse(null);

    assertEquals(DocumentShares.PermissionType.READ, groupShare.getPermission());
    assertEquals(DocumentShares.RecipientType.GROUP, groupShare.getRecipientType());
    assertEquals(sharer.getId(), groupShare.getSharerId());
    assertEquals(group.getGroup().getId(), groupShare.getRecipientId(), 0L);
    assertEquals(group.getGroup().getDisplayName(), groupShare.getRecipientName());
    assertEquals(group.getGroup().getCommunalGroupFolderId(), groupShare.getParentId());

    assertEquals(DocumentShares.PermissionType.EDIT, userShare.getPermission());
    assertEquals(DocumentShares.RecipientType.USER, userShare.getRecipientType());
    assertEquals(sharer.getId(), userShare.getSharerId());
    assertEquals(group.getGroup().getId(), groupShare.getRecipientId(), 0L);
    assertEquals(userToShareWith.getDisplayName(), userShare.getRecipientName());
    assertEquals(sharer.getUsername() + "-" + userToShareWith.getUsername(), userShare.getPath());
    // grandparentId should be null when the document is shared into the root of the shared
    // hierarchy
    assertEquals(null, userShare.getGrandparentId());
    assertEquals(null, groupShare.getGrandparentId());
  }

  @Test
  public void getAllSharesForDocNonOwnerReturns404() throws Exception {
    TestGroup group = createTestGroup(2);
    User owner = group.getPi();
    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "test");
    String ownerKey = createNewApiKeyForUser(owner);

    SharePost sharePost = createValidSharePostWithGroup(group, doc, "READ");
    mockMvc
        .perform(createBuilderForPostWithJSONBody(ownerKey, "/share", owner, sharePost))
        .andExpect(status().isCreated());

    // Non-owner attempts to fetch shares
    User other = createInitAndLoginAnyUser();
    String otherKey = createNewApiKeyForUser(other);

    mockMvc
        .perform(get("/api/v1/share/document/" + doc.getId()).header("apiKey", otherKey))
        .andExpect(status().isNotFound());
  }

  @Test
  public void deleteShareNotSharerReturns404() throws Exception {
    TestGroup group = createTestGroup(2);
    User sharer = group.getPi();
    logoutAndLoginAs(sharer);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(sharer, "test");
    String sharerKey = createNewApiKeyForUser(sharer);

    SharePost sharePost = createValidSharePostWithGroup(group, doc, "READ");
    MvcResult createResult =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(sharerKey, "/share", sharer, sharePost))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSharingResult created = getFromJsonResponseBody(createResult, ApiSharingResult.class);
    Long shareId = created.getShareInfos().get(0).getId();

    // Different user attempts to delete
    User other = createInitAndLoginAnyUser();
    String otherKey = createNewApiKeyForUser(other);

    mockMvc
        .perform(createBuilderForDelete(otherKey, "/share/{id}", other, shareId))
        .andExpect(status().isNotFound());
  }
}

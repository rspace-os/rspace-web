package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePost;
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
    MvcResult result =
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
    MvcResult deleted =
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
    addUsersToGroup(testGrp1.getPi(), otherGroup.getGroup(), new User[] {});
    logoutAndLoginAs(testGrp1.getPi());
    StructuredDocument piDocToShare =
        createBasicDocumentInRootFolderWithText(testGrp1.getPi(), "anytext");
    toPost.setItemsToShare(toList(piDocToShare.getId()));
    toPost
        .getGroupSharePostItems()
        .get(0)
        .setSharedFolderId(otherGroup.getGroup().getCommunalGroupFolderId());
    result =
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
        .getHits()
        .intValue();
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
    SharePost toPost =
        SharePost.builder()
            .itemToShare(toShare.getId())
            .groupSharePostItem(
                GroupSharePostItem.builder()
                    .id(testGrp1.getGroup().getId())
                    .permission(permission)
                    .build())
            .build();
    return toPost;
  }

  private SharePost createSharePostWithGroupAndFolder(
      TestGroup testGrp1, Long sharingTargetFolder, StructuredDocument toShare, String permission) {
    SharePost toPost =
        SharePost.builder()
            .itemToShare(toShare.getId())
            .groupSharePostItem(
                GroupSharePostItem.builder()
                    .id(testGrp1.getGroup().getId())
                    .permission(permission)
                    .sharedFolderId(sharingTargetFolder)
                    .build())
            .build();
    return toPost;
  }
}

package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.*;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class FolderApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void folderPostGetAndDelete() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiFolder folderPost = new ApiFolder();
    folderPost.setName("TestFolder");

    // create a folder
    MvcResult result =
        this.mockMvc
            .perform(folderCreate(anyUser, apiKey, folderPost))
            .andExpect(status().isCreated())
            .andReturn();
    ApiFolder createdFolder = getFromJsonResponseBody(result, ApiFolder.class);
    assertFalse(createdFolder.isNotebook());
    assertNotNull(createdFolder.getId());
    // ... and delete it:
    result =
        this.mockMvc
            .perform(
                createBuilderForDelete(apiKey, "/folders/{id}", anyUser, createdFolder.getId()))
            .andExpect(status().isNoContent())
            .andReturn();

    // create a notebook
    folderPost.setNotebook(true);

    result =
        this.mockMvc
            .perform(folderCreate(anyUser, apiKey, folderPost))
            .andExpect(status().isCreated())
            .andReturn();
    ApiFolder createdNb = getFromJsonResponseBody(result, ApiFolder.class);
    assertTrue(createdNb.isNotebook());
    assertEquals(folderMgr.getRootFolderForUser(anyUser).getId(), createdNb.getParentFolderId());
    assertNotNull(createdNb.getId());
    assertNull(createdNb.getMediaType());

    // now get the notebook using a getter:
    result =
        this.mockMvc
            .perform(getNotebook(anyUser, apiKey, createdNb))
            .andExpect(status().isOk())
            .andReturn();
    ApiFolder retrievedNb = getFromJsonResponseBody(result, ApiFolder.class);
    assertEquals(createdNb, retrievedNb);

    // delete notebook:
    result =
        this.mockMvc
            .perform(createBuilderForDelete(apiKey, "/folders/{id}", anyUser, createdNb.getId()))
            .andExpect(status().isNoContent())
            .andReturn();
    // no longer found
    result =
        this.mockMvc
            .perform(getNotebook(anyUser, apiKey, createdNb))
            .andExpect(status().isUnauthorized())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(ApiErrorCodes.AUTH.getCode(), error.getInternalCode());

    // try using invalid folder ID
    folderPost.setParentFolderId(-1111L);

    result =
        this.mockMvc
            .perform(folderCreate(anyUser, apiKey, folderPost))
            .andExpect(status().isBadRequest())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(ApiErrorCodes.INVALID_FIELD.getCode(), error.getInternalCode());
  }

  private MockHttpServletRequestBuilder getNotebook(
      User anyUser, String apiKey, ApiFolder createdNb) {
    return createBuilderForGet(
        API_VERSION.ONE, apiKey, "/folders/{id}", anyUser, createdNb.getId());
  }

  @Test
  public void folderPostAndGetInGallery() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    Folder docGalleryFolder =
        recordMgr.getGallerySubFolderForUser(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME, anyUser);
    ApiFolder folderPost = new ApiFolder();
    folderPost.setName("TestFolder");
    folderPost.setParentFolderId(docGalleryFolder.getId());
    // create folder in gallery
    MvcResult result = this.mockMvc.perform(folderCreate(anyUser, apiKey, folderPost)).andReturn();
    ApiFolder created = getFromJsonResponseBody(result, ApiFolder.class);
    assertFalse(created.isNotebook());
    assertEquals("Documents", created.getMediaType());
    assertNull(created.getPathToRootFolder());

    // retrieve created folder, with parents up to media gallery root folder
    MockHttpServletRequestBuilder getFolderWithParentRequest =
        createBuilderForGet(API_VERSION.ONE, apiKey, "/folders/{id}", anyUser, created.getId())
            .param("includePathToRootFolder", "true");
    result = this.mockMvc.perform(getFolderWithParentRequest).andReturn();
    ApiFolder retrieved = getFromJsonResponseBody(result, ApiFolder.class);
    assertFalse(retrieved.isNotebook());
    assertEquals("Documents", retrieved.getMediaType());
    assertNotNull(retrieved.getPathToRootFolder());
    assertEquals(2, retrieved.getPathToRootFolder().size());

    // can't create notebook in Gallery
    folderPost.setNotebook(true);

    result = this.mockMvc.perform(folderCreate(anyUser, apiKey, folderPost)).andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(HttpStatus.BAD_REQUEST.value(), error.getHttpCode());

    // check can't create in top folder of Gallery
    Folder mediaFolder = folderMgr.getGalleryRootFolderForUser(anyUser);
    folderPost.setNotebook(false);
    folderPost.setParentFolderId(mediaFolder.getId());

    result = this.mockMvc.perform(folderCreate(anyUser, apiKey, folderPost)).andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertNotNull(error);

    // sanity permissions check - can't create folder in other user's account
    User otherUser = createInitAndLoginAnyUser();
    logoutAndLoginAs(otherUser);
    Folder otherRoot = folderMgr.getRootFolderForUser(otherUser);
    folderPost.setParentFolderId(otherRoot.getId());
    result = this.mockMvc.perform(folderCreate(anyUser, apiKey, folderPost)).andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(HttpStatus.UNAUTHORIZED.value(), error.getHttpCode());
  }

  @Test
  public void folderTreeRootListing() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument document = createBasicDocumentInRootFolderWithText(anyUser, "any");
    String apiKey = createNewApiKeyForUser(anyUser);
    MvcResult result = performRootFolderListing(anyUser, apiKey);
    ApiRecordTreeItemListing listing =
        getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);
    final int expectedTotalRootFolderContents = 7;
    assertEquals(expectedTotalRootFolderContents, listing.getTotalHits().intValue());

    // assert Gallery folder is listed
    assertThat(
        listing.getRecords().stream().map(RecordTreeItemInfo::getName).collect(toList()),
        hasItem(Folder.MEDIAROOT));

    final int expectedDocumentCount = 1;
    // we are ignoring the document we just created
    result = performRootFolderListingWithFilter(anyUser, apiKey, "folder,notebook");
    listing = getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);
    assertEquals(
        expectedTotalRootFolderContents - expectedDocumentCount, listing.getTotalHits().intValue());

    // now we just want the document we created
    result = performRootFolderListingWithFilter(anyUser, apiKey, "document");
    listing = getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);
    assertEquals(expectedDocumentCount, listing.getTotalHits().intValue());
  }

  @Test
  public void traverseFolderTreeAndGallery() throws Exception {
    // navigates folder tree with example content.
    User anyUser = createAndSaveUser(getRandomName(10));
    initUser(anyUser, true);
    logoutAndLoginAs(anyUser);
    String apiKey = createNewApiKeyForUser(anyUser);

    MvcResult result = performRootFolderListing(anyUser, apiKey);
    ApiRecordTreeItemListing listing =
        getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);

    long galleryId = getIdFromNameForListing(Folder.MEDIAROOT, listing);
    ApiRecordTreeItemListing galleryListing = performFolderListingById(anyUser, apiKey, galleryId);
    final int expectedTopLevelGalleryFolders = 8;
    assertEquals(expectedTopLevelGalleryFolders, galleryListing.getRecords().size());

    // none of these can be deleted:
    for (RecordTreeItemInfo info : galleryListing.getRecords()) {
      result = deleteByIdFailsWith4xxStatus(anyUser, apiKey, info.getId());
    }
    // now check we can navigate into Gallery folders with 200 responses:
    for (RecordTreeItemInfo info : galleryListing.getRecords()) {
      galleryListing = performFolderListingById(anyUser, apiKey, info.getId());
    }
  }

  private MvcResult deleteByIdFailsWith4xxStatus(User anyUser, String apiKey, Long id)
      throws Exception {
    return this.mockMvc
        .perform(createBuilderForDelete(apiKey, "/folders/{id}", anyUser, id))
        .andExpect(status().is4xxClientError())
        .andReturn();
  }

  @Test
  public void traverseFolderAndSharedFolder() throws Exception {
    TestGroup group = createTestGroup(2);
    logoutAndLoginAs(group.u1());
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(group.u1(), "any");
    shareRecordWithGroup(group.u1(), group.getGroup(), sd);
    User u1 = group.u1();
    String apiKey = createNewApiKeyForUser(u1);

    MvcResult result = performRootFolderListing(u1, apiKey);
    ApiRecordTreeItemListing listing =
        getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);
    long sharedId = getIdFromNameForListing(Folder.SHARED_FOLDER_NAME, listing);
    deleteByIdFailsWith4xxStatus(u1, apiKey, sharedId);
    ApiRecordTreeItemListing sharedListing = performFolderListingById(u1, apiKey, sharedId);

    // fixed 3 folders inside shared folder can't be deleted
    assertEquals(3, sharedListing.getTotalHits().intValue());
    for (RecordTreeItemInfo info : sharedListing.getRecords()) {
      result = deleteByIdFailsWith4xxStatus(u1, apiKey, info.getId());
    }

    long labGroupsId = getIdFromNameForListing(Folder.LAB_GROUPS_FOLDER_NAME, sharedListing);
    ApiRecordTreeItemListing labGroupsListing = performFolderListingById(u1, apiKey, labGroupsId);
    deleteByIdFailsWith4xxStatus(u1, apiKey, labGroupsId);

    // labgroup folder can't be deleted either.
    assertEquals(1, labGroupsListing.getTotalHits().intValue());
    for (RecordTreeItemInfo info : labGroupsListing.getRecords()) {
      result = deleteByIdFailsWith4xxStatus(u1, apiKey, info.getId());
    }

    // user shouldn't be able to create content inside these folders either.
    ApiFolder folderPost = new ApiFolder();
    folderPost.setName("TestFolder");
    folderPost.setParentFolderId(sharedId);

    // create a folder inside Shared folder is forbidden
    result =
        this.mockMvc
            .perform(folderCreate(u1, apiKey, folderPost))
            .andExpect(status().is4xxClientError())
            .andReturn();
    // and is also forbidden in LabGroup folder
    folderPost.setParentFolderId(labGroupsId);
    result =
        this.mockMvc
            .perform(folderCreate(u1, apiKey, folderPost))
            .andExpect(status().is4xxClientError())
            .andReturn();

    // now let's login as a PI and ensure they can't delete user folder
    logoutAndLoginAs(group.getPi());
    String piApiKey = createNewApiKeyForUser(group.getPi());
    ApiRecordTreeItemListing labGroupsListingForPi =
        performFolderListingById(group.getPi(), piApiKey, labGroupsId);
    // the user folders don't seem to be added, just the group folder.
    assertEquals(1, labGroupsListingForPi.getTotalHits().intValue());

    // pi can't delete user folder:
    result =
        deleteByIdFailsWith4xxStatus(group.getPi(), piApiKey, getRootFolderForUser(u1).getId());
  }

  @Test
  public void listOwnSharedFolderInNonOwnedFolder_RSDEV_488() throws Exception {
    TestGroup group = createTestGroup(2);
    User u1 = group.u1();
    logoutAndLoginAs(u1);
    String apiKey = createNewApiKeyForUser(u1);

    Long groupSharedFolderId = group.getGroup().getCommunalGroupFolderId();
    ApiFolder folderPost = new ApiFolder();
    folderPost.setName("u1 test folder in group's shared folder");
    folderPost.setParentFolderId(groupSharedFolderId);
    MvcResult result = this.mockMvc.perform(folderCreate(u1, apiKey, folderPost)).andReturn();
    ApiFolder created = getFromJsonResponseBody(result, ApiFolder.class);
    assertNotNull(created.getId());

    // listing of top-level folder works fine for u1 (RSDEV-488)
    ApiRecordTreeItemListing sharedFolderListing =
        performFolderListingById(u1, apiKey, groupSharedFolderId);
    assertEquals(1, sharedFolderListing.getTotalHits().intValue());
    RecordTreeItemInfo retrievedFolder = sharedFolderListing.getRecords().get(0);
    assertEquals(u1.getUsername(), retrievedFolder.getOwner().getUsername());
    assertEquals(groupSharedFolderId, retrievedFolder.getParentFolderId());
  }

  private long getIdFromNameForListing(String name, ApiRecordTreeItemListing listing) {
    return listing.getRecords().stream()
        .filter(item -> item.getName().equals(name))
        .findFirst()
        .get()
        .getId();
  }

  private MvcResult performRootFolderListing(User anyUser, String apiKey) throws Exception {
    return this.mockMvc
        .perform(folderTree(anyUser, apiKey))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  private ApiRecordTreeItemListing performFolderListingById(User anyUser, String apiKey, Long id)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/folders/tree/{id}", anyUser, id))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);
  }

  private MvcResult performRootFolderListingWithFilter(User anyUser, String apiKey, String filter)
      throws Exception {
    return this.mockMvc
        .perform(folderTree(anyUser, apiKey).param("typesToInclude", filter))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  private MockHttpServletRequestBuilder folderTree(User anyUser, String apiKey) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/folders/tree", anyUser);
  }
}

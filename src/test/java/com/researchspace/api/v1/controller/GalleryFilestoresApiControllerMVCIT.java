package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.service.NfsManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class GalleryFilestoresApiControllerMVCIT extends API_MVC_TestBase {

  User anyUser;
  String apiKey;

  @Autowired private NfsManager nfsManager;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void testGetFilesystems() throws Exception {

    // initially no filesystems - expected empty array response
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/gallery/filesystems", anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    assertEquals("[ ]", result.getResponse().getContentAsString());

    // add test filesystem
    NfsFileSystem iRodsFileSystem = GalleryFilestoreTestUtils.createIrodsFileSystem();
    nfsManager.saveNfsFileSystem(iRodsFileSystem);

    result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/gallery/filesystems", anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    List<NfsFileSystemInfo> retrievedFilesystems =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertNotNull(retrievedFilesystems);
    assertEquals(1, retrievedFilesystems.size());
    assertEquals("irods_test_instance", retrievedFilesystems.get(0).getName());
  }

  @Test
  public void testCreateReadDeleteFilestore() throws Exception {

    // add test filesystem
    NfsFileSystem testFilesystem = GalleryFilestoreTestUtils.createIrodsFileSystem();
    nfsManager.saveNfsFileSystem(testFilesystem);

    // initial call - no filestores expected
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/gallery/filestores", anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    List<NfsFileStoreInfo> retrievedFilestoreInfos =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertEquals(0, retrievedFilestoreInfos.size());

    // try creating new filestore without required params
    result =
        this.mockMvc
            .perform(createBuilderForPost(API_VERSION.ONE, apiKey, "/gallery/filestores", anyUser))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals(
        "Required request parameter 'filesystemId' for method parameter type "
            + "Long is not present",
        result.getResolvedException().getMessage());

    // create new filestore with all params
    result =
        this.mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/gallery/filestores", anyUser)
                    .param("filesystemId", "" + testFilesystem.getId())
                    .param("name", "testNewFS")
                    .param("remotePath", "testNewRemotePath"))
            .andExpect(status().isCreated())
            .andReturn();

    NfsFileStoreInfo createdFilestoreInfo =
        mvcUtils.getFromJsonResponseBody(result, NfsFileStoreInfo.class);
    assertNotNull(createdFilestoreInfo);
    assertEquals(testFilesystem.getName(), createdFilestoreInfo.getFileSystem().getName());
    assertEquals("testNewFS", createdFilestoreInfo.getName());
    assertEquals("testNewRemotePath", createdFilestoreInfo.getPath());

    // check new filestore is returned by listing
    result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/gallery/filestores", anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    retrievedFilestoreInfos =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertEquals(1, retrievedFilestoreInfos.size());
    assertEquals(createdFilestoreInfo, retrievedFilestoreInfos.get(0));

    // delete filestore
    this.mockMvc
        .perform(
            createBuilderForDelete(
                apiKey, "/gallery/filestores/{id}", anyUser, createdFilestoreInfo.getId()))
        .andExpect(status().isNoContent());

    // check no filestores listed after deletion
    result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/gallery/filestores", anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    retrievedFilestoreInfos =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertEquals(0, retrievedFilestoreInfos.size());
  }

  @Test
  public void testRemoteFileDownload() throws Exception {

    // add test filesystem
    NfsFileSystem testFilesystem = GalleryFilestoreTestUtils.createIrodsFileSystem();
    nfsManager.saveNfsFileSystem(testFilesystem);

    // add test filestore
    NfsFileStore testFilestore = GalleryFilestoreTestUtils.createFileStore(
        "test", anyUser, testFilesystem);
    nfsManager.saveNfsFileStore(testFilestore);

    // try downloading without authenticating to filesystem
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey,
                "/gallery/filestores/" + testFilestore.getId() + "/download", anyUser)
                .param("remotePath", "testResource"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals("download not supported yet", result.getResolvedException().getMessage());
    //assertEquals("[ ]", result.getResponse().getContentAsString());

    // mock nfsClient that returns a file when queried
    // TODO: WIP

  }

}

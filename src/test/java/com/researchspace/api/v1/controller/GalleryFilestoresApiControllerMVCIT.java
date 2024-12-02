package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.NfsManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class GalleryFilestoresApiControllerMVCIT extends API_MVC_TestBase {

  User anyUser;
  String apiKey;

  @Autowired private NfsManager nfsManager;

  @Autowired private GalleryFilestoresCredentialsStore credentialsStore;

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
                    .param("pathToSave", "testNewFilestorePath"))
            .andExpect(status().isCreated())
            .andReturn();

    NfsFileStoreInfo createdFilestoreInfo =
        mvcUtils.getFromJsonResponseBody(result, NfsFileStoreInfo.class);
    assertNotNull(createdFilestoreInfo);
    assertEquals(testFilesystem.getName(), createdFilestoreInfo.getFileSystem().getName());
    assertEquals("testNewFS", createdFilestoreInfo.getName());
    assertEquals("testNewFilestorePath", createdFilestoreInfo.getPath());

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
  public void testLoginDownloadDummyFile() throws Exception {

    // add test filesystem
    NfsFileSystem testFilesystem = GalleryFilestoreTestUtils.createIrodsFileSystem();
    nfsManager.saveNfsFileSystem(testFilesystem);

    // add test filestore
    NfsFileStore testFilestore =
        GalleryFilestoreTestUtils.createFileStore("test", anyUser, testFilesystem);
    nfsManager.saveNfsFileStore(testFilestore);

    // try downloading without authenticating to filesystem
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                        API_VERSION.ONE,
                        apiKey,
                        "/gallery/filestores/" + testFilestore.getId() + "/download",
                        anyUser)
                    .param("remotePath", "testResource"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals(
        "User not logged to filesystem [irods_test_instance]. Call '/login' " + "endpoint first?",
        result.getResolvedException().getMessage());

    // set mocked nfsAuthenticator to allow login with dummy credentials,
    // and to return mocked nfsClient that will return a test content on download attempt
    NfsAuthentication mockNfsAuthentication = Mockito.mock(NfsAuthentication.class);
    credentialsStore.setNfsAuthentication(mockNfsAuthentication);
    NfsClient mockNfsClient = Mockito.mock(NfsClient.class);

    // mock calls used by login flow
    ApiNfsCredentials dummyCredentials = new ApiNfsCredentials(anyUser, "testuser", "testpass");
    when(mockNfsAuthentication.validateCredentials(
            dummyCredentials.getUsername(), dummyCredentials.getPassword(), anyUser))
        .thenReturn(null);
    when(mockNfsAuthentication.login(
            dummyCredentials.getUsername(),
            dummyCredentials.getPassword(),
            testFilesystem,
            anyUser))
        .thenReturn(mockNfsClient);
    when(mockNfsClient.isUserLoggedIn()).thenReturn(true);

    // login with dummy credentials
    assertEquals(0, credentialsStore.getCredentialsMapCache().size());
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/gallery/filesystems/" + testFilesystem.getId() + "/login",
                    anyUser,
                    dummyCredentials))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    assertEquals(1, credentialsStore.getCredentialsMapCache().size());

    // mock calls used by download flow
    NfsTarget dummyTarget = new NfsTarget("testResourcePath");
    NfsFileDetails dummyFileDetails = new NfsFileDetails();
    String dummyFileContent = "testContent";
    dummyFileDetails.setName("testFile");
    dummyFileDetails.setRemoteInputStream(new ByteArrayInputStream(dummyFileContent.getBytes()));
    when(mockNfsClient.queryNfsFileForDownload(dummyTarget)).thenReturn(dummyFileDetails);

    // try downloading now
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                        API_VERSION.ONE,
                        apiKey,
                        "/gallery/filestores/" + testFilestore.getId() + "/download",
                        anyUser)
                    .param("remotePath", dummyTarget.getPath()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    assertEquals("application/octet-stream", result.getResponse().getContentType());
    byte[] responseBytes = result.getResponse().getContentAsByteArray();
    assertEquals(11, responseBytes.length);
    assertEquals(dummyFileContent, new String(responseBytes));
  }
}

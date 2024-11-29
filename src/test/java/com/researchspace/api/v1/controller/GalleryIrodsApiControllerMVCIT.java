package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.GalleryIrodsApi.PARAM_FILESTORE_PATH_ID;
import static com.researchspace.api.v1.GalleryIrodsApi.PARAM_RECORD_IDS;
import static com.researchspace.api.v1.controller.GalleryIrodsApiController.Operation;
import static com.researchspace.api.v1.controller.GalleryIrodsApiController.Operation.copy;
import static com.researchspace.api.v1.controller.GalleryIrodsApiController.Operation.move;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExternalStorageInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.irods.IRODSClient;
import com.researchspace.netfiles.irods.JargonFacade;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalStorageManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.TestGroup;
import com.researchspace.webapp.controller.GalleryController;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.irods.jargon.core.connection.IRODSAccount;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
public class GalleryIrodsApiControllerMVCIT extends API_MVC_TestBase {

  private String IRODS_URL = "irods-test.researchspace.com";
  private Integer IRODS_PORT = 1247;
  private String IRODS_TIMEZONE = "tempZone";
  private String IRODS_HOME_DIR = "/tempZone/home/alice";

  @Value("${irods.realConnection.server.username}")
  private String IRODS_USERNAME;

  @Value("${irods.realConnection.server.password}")
  private String IRODS_PASSWORD;

  @Autowired private NfsManager nfsManager;
  @Autowired private GalleryController galleryController;
  @Autowired private BaseRecordManager baseRecordManager;
  @Autowired private ExternalStorageManager externalStorageManager;

  /****** IRODS teardown management **********/
  private IRODSClient irodsClient;

  private IRODSAccount iRodsAccount;
  private String IRODS_TARGET_PATH;

  /*****************************/

  private Long fileStorePathId1, fileStorePathId2;

  private TestGroup testGrp;
  private String apiKeyUser1;
  private ApiNfsCredentials irodsCredentials;
  private User user1;

  private static final String EXPECTED_NO_SEVER_INFO =
      "{\n"
          + "  \"serverUrl\" : \"\",\n"
          + "  \"configuredLocations\" : [ ],\n"
          + "  \"_links\" : [ {\n"
          + "    \"link\" : \"http://localhost:8080/api/v1/gallery/irods\",\n"
          + "    \"rel\" : \"self\"\n"
          + "  } ]\n"
          + "}";
  private static final String EXPECTED_BLANK_INFO =
      "{\n"
          + "  \"serverUrl\" : \"http://irods-test.researchspace.com:1247\",\n"
          + "  \"configuredLocations\" : [ {\n"
          + "    \"id\" : %d,\n"
          + "    \"name\" : \"test_folder_1\",\n"
          + "    \"path\" : \"/tempZone/home/alice/test\",\n"
          + "    \"_links\" : [ ]\n"
          + "  }, {\n"
          + "    \"id\" : %d,\n"
          + "    \"name\" : \"test_folder_2\",\n"
          + "    \"path\" : \"/tempZone/home/alice/training_jpgs\",\n"
          + "    \"_links\" : [ ]\n"
          + "  } ],\n"
          + "  \"_links\" : [ {\n"
          + "    \"link\" : \"http://localhost:8080/api/v1/gallery/irods\",\n"
          + "    \"rel\" : \"self\"\n"
          + "  } ]\n"
          + "}";

  private static final String EXPECTED_FILE_INFO =
      "{\n"
          + "  \"serverUrl\" : \"http://irods-test.researchspace.com:1247\",\n"
          + "  \"configuredLocations\" : [ {\n"
          + "    \"id\" : %1$d,\n"
          + "    \"name\" : \"test_folder_1\",\n"
          + "    \"path\" : \"/tempZone/home/alice/test\",\n"
          + "    \"_links\" : [ {\n"
          + "      \"operation\" : \"copy\",\n"
          + "      \"link\" :"
          + " \"http://localhost:8080/api/v1/gallery/irods/copy?filestorePathId=%1$d&recordIds=123,345,456\",\n"
          + "      \"method\" : \"POST\"\n"
          + "    }, {\n"
          + "      \"operation\" : \"move\",\n"
          + "      \"link\" :"
          + " \"http://localhost:8080/api/v1/gallery/irods/move?filestorePathId=%1$d&recordIds=123,345,456\",\n"
          + "      \"method\" : \"POST\"\n"
          + "    } ]\n"
          + "  }, {\n"
          + "    \"id\" : %2$d,\n"
          + "    \"name\" : \"test_folder_2\",\n"
          + "    \"path\" : \"/tempZone/home/alice/training_jpgs\",\n"
          + "    \"_links\" : [ {\n"
          + "      \"operation\" : \"copy\",\n"
          + "      \"link\" :"
          + " \"http://localhost:8080/api/v1/gallery/irods/copy?filestorePathId=%2$d&recordIds=123,345,456\",\n"
          + "      \"method\" : \"POST\"\n"
          + "    }, {\n"
          + "      \"operation\" : \"move\",\n"
          + "      \"link\" :"
          + " \"http://localhost:8080/api/v1/gallery/irods/move?filestorePathId=%2$d&recordIds=123,345,456\",\n"
          + "      \"method\" : \"POST\"\n"
          + "    } ]\n"
          + "  } ],\n"
          + "  \"_links\" : [ {\n"
          + "    \"link\" : \"http://localhost:8080/api/v1/gallery/irods?recordIds=123,345,456\",\n"
          + "    \"rel\" : \"self\"\n"
          + "  } ]\n"
          + "}";

  @Before
  public void setup() throws Exception {
    super.setUp();
    IRODS_TARGET_PATH = IRODS_HOME_DIR + "/test";
    testGrp = createTestGroup(2, new TestGroupConfig(true));
    user1 = testGrp.getUserByPrefix("u1");

    NfsFileSystem iRodsFileSystem = GalleryFilestoreTestUtils.createIrodsFileSystem();
    nfsManager.saveNfsFileSystem(iRodsFileSystem);

    fileStorePathId1 = createIrodsFileStore(iRodsFileSystem, "test_folder_1", IRODS_TARGET_PATH);
    fileStorePathId2 =
        createIrodsFileStore(iRodsFileSystem, "test_folder_2", IRODS_HOME_DIR + "/training_jpgs");
    logoutAndLoginAs(user1);
    apiKeyUser1 = createNewApiKeyForUser(user1);
    irodsCredentials =
        ApiNfsCredentials.builder().username(IRODS_USERNAME).password(IRODS_PASSWORD).build();
    iRodsAccount =
        new IRODSAccount(
            IRODS_URL,
            IRODS_PORT,
            IRODS_USERNAME,
            IRODS_PASSWORD,
            IRODS_HOME_DIR,
            IRODS_TIMEZONE,
            "");
    irodsClient = new IRODSClient(iRodsAccount, new JargonFacade());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testIrodsGetMappingEmptyIdsWhenNoFilestores() throws Exception {
    TestGroup testGrp1 = createTestGroup(2, new TestGroupConfig(true));
    User userNew = testGrp1.getUserByPrefix("u2");
    logoutAndLoginAs(userNew);
    String apiKeyNew = createNewApiKeyForUser(userNew);
    MvcResult getResult =
        mockMvc
            .perform(createIrodsGetBuilder(userNew, apiKeyNew))
            .andExpect(status().isOk())
            .andReturn();

    System.out.println(getResult.getResponse().getContentAsString());
    // do not throw exception here
    getFromJsonResponseBody(getResult, ApiExternalStorageInfo.class);
    assertEquals(EXPECTED_NO_SEVER_INFO, getResult.getResponse().getContentAsString());
  }

  @Test
  public void testIrodsGetMappingEmptyIds() throws Exception {
    MvcResult getResult =
        mockMvc
            .perform(createIrodsGetBuilder(user1, apiKeyUser1))
            .andExpect(status().isOk())
            .andReturn();

    System.out.println(getResult.getResponse().getContentAsString());
    // do not throw exception here
    getFromJsonResponseBody(getResult, ApiExternalStorageInfo.class);
    assertEquals(
        String.format(EXPECTED_BLANK_INFO, fileStorePathId1, fileStorePathId2),
        getResult.getResponse().getContentAsString());
  }

  @Test
  public void testIrodsGetMappingPassingIds() throws Exception {
    MvcResult getResult =
        mockMvc
            .perform(
                createIrodsGetBuilder(user1, apiKeyUser1).param("recordIds", "123", "345", "456"))
            .andExpect(status().isOk())
            .andReturn();

    System.out.println(getResult.getResponse().getContentAsString());
    // do not throw exception here
    getFromJsonResponseBody(getResult, ApiExternalStorageInfo.class);
    assertEquals(
        String.format(EXPECTED_FILE_INFO, fileStorePathId1, fileStorePathId2),
        getResult.getResponse().getContentAsString());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testIrodsCopyingFilesSuccessfully() throws IOException {
    User user2 = testGrp.getUserByPrefix("u2");
    logoutAndLoginAs(user2);
    String apiKeyUser2 = createNewApiKeyForUser(user2);

    EcatMediaFile imageInGallery1 = uploadFileIntoRspace("image1.png");
    EcatMediaFile imageInGallery2 = uploadFileIntoRspace("image2.png");
    EcatMediaFile imageInGallery3 = uploadFileIntoRspace("image3.png");

    String irodsFileName1 = generateIrodsFileName(imageInGallery1.getFileName());

    try { // copy the image1 into irods so that it is already there when copy again
      mockMvc
          .perform(
              createIrodsPostBuilder(user2, apiKeyUser2, irodsCredentials, copy)
                  .param(PARAM_RECORD_IDS, imageInGallery1.getId().toString())
                  .param(PARAM_FILESTORE_PATH_ID, fileStorePathId1.toString()))
          .andExpect(status().isOk())
          .andReturn();
      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName1));
    } catch (Exception e) {
      teardownIrods(irodsFileName1);
      throw new RuntimeException(e);
    }

    String irodsFileName2 = generateIrodsFileName(imageInGallery2.getFileName());
    String irodsFileName3 = generateIrodsFileName(imageInGallery3.getFileName());

    // copy again expecting image1 goes in error since it is already there
    try {
      MvcResult result =
          mockMvc
              .perform(
                  createIrodsPostBuilder(user2, apiKeyUser2, irodsCredentials, copy)
                      .param(
                          PARAM_RECORD_IDS,
                          imageInGallery1.getId().toString(),
                          imageInGallery2.getId().toString(),
                          imageInGallery3.getId().toString())
                      .param(PARAM_FILESTORE_PATH_ID, fileStorePathId1.toString()))
              .andExpect(status().isOk())
              .andReturn();
      System.out.println(result.getResponse().getContentAsString());
      // do not throw exception here
      ApiExternalStorageOperationResult response =
          getFromJsonResponseBody(result, ApiExternalStorageOperationResult.class);

      assertEquals(3, response.getNumFilesInput().intValue());
      assertEquals(2, response.getNumFilesSucceed().intValue());
      assertEquals(1, response.getNumFilesFailed().intValue());
      assertEquals(3, response.getFileInfoDetails().size());
      Iterator<ApiExternalStorageOperationInfo> fileInfoIterator =
          response.getFileInfoDetails().iterator();
      while (fileInfoIterator.hasNext()) {
        ApiExternalStorageOperationInfo currentFileInfo = fileInfoIterator.next();
        if (imageInGallery1.getId().equals(currentFileInfo.getRecordId())) {
          assertFalse(currentFileInfo.getSucceeded());
          assertTrue(currentFileInfo.getReason().contains("target file already exists"));
        } else {
          assertTrue(currentFileInfo.getSucceeded());
        }
      }
      assertFalse(response.getLinks().get(0).getLink().isBlank());

      // check database
      List<ExternalStorageLocation> databaseEntries =
          externalStorageManager.getAllExternalStorageLocationsByUser(user2);
      assertEquals(3, databaseEntries.size());
      for (ExternalStorageLocation currentDbEntry : databaseEntries) {
        assertEquals(user2, currentDbEntry.getOperationUser());
        assertEquals(fileStorePathId1, currentDbEntry.getFileStore().getId());
        assertNotNull(currentDbEntry.getConnectedMediaFile());
        assertNotNull(currentDbEntry.getId());
        assertNotNull(currentDbEntry.getOperationDate());
        assertNotNull(currentDbEntry.getExternalStorageId());
      }

      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName1));
      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName2));
      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName3));

    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      teardownIrods(irodsFileName1);
      teardownIrods(irodsFileName2);
      teardownIrods(irodsFileName3);
    }
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testIrodsMovingFileSuccessfully() throws Exception {
    EcatMediaFile imageInGallery1 = uploadFileIntoRspace("image1.png");
    EcatMediaFile imageInGallery2 = uploadFileIntoRspace("image2.png");
    EcatMediaFile imageInGallery3 = uploadFileIntoRspace("image3.png");

    String irodsFileName1 = generateIrodsFileName(imageInGallery1.getFileName());

    try { // copy the image1 into irods so that it is already there when copy again
      mockMvc
          .perform(
              createIrodsPostBuilder(user1, apiKeyUser1, irodsCredentials, copy)
                  .param(PARAM_RECORD_IDS, imageInGallery1.getId().toString())
                  .param(PARAM_FILESTORE_PATH_ID, fileStorePathId1.toString()))
          .andExpect(status().isOk())
          .andReturn();
      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName1));
    } catch (Exception e) {
      teardownIrods(irodsFileName1);
      throw new RuntimeException(e);
    }

    String irodsFileName2 = generateIrodsFileName(imageInGallery2.getFileName());
    String irodsFileName3 = generateIrodsFileName(imageInGallery3.getFileName());

    // copy again expecting image1 goes in error since it is already there
    try {
      MvcResult result =
          mockMvc
              .perform(
                  createIrodsPostBuilder(user1, apiKeyUser1, irodsCredentials, move)
                      .param(
                          PARAM_RECORD_IDS,
                          imageInGallery1.getId().toString(),
                          imageInGallery2.getId().toString(),
                          imageInGallery3.getId().toString())
                      .param(PARAM_FILESTORE_PATH_ID, fileStorePathId1.toString()))
              .andExpect(status().isOk())
              .andReturn();
      System.out.println(result.getResponse().getContentAsString());
      // do not throw exception here
      ApiExternalStorageOperationResult response =
          getFromJsonResponseBody(result, ApiExternalStorageOperationResult.class);

      assertEquals(3, response.getNumFilesInput().intValue());
      assertEquals(2, response.getNumFilesSucceed().intValue());
      assertEquals(1, response.getNumFilesFailed().intValue());
      assertEquals(3, response.getFileInfoDetails().size());

      // check response
      Iterator<ApiExternalStorageOperationInfo> fileInfoIterator =
          response.getFileInfoDetails().iterator();
      while (fileInfoIterator.hasNext()) {
        ApiExternalStorageOperationInfo currentFileInfo = fileInfoIterator.next();
        if (imageInGallery1.getId().equals(currentFileInfo.getRecordId())) {
          assertFalse(currentFileInfo.getSucceeded());
          assertTrue(currentFileInfo.getReason().contains("target file already exists"));
        } else {
          assertTrue(currentFileInfo.getSucceeded());
        }
      }
      assertFalse(response.getLinks().get(0).getLink().isBlank());

      // check database
      List<ExternalStorageLocation> databaseEntries =
          externalStorageManager.getAllExternalStorageLocationsByUser(user1);
      assertEquals(3, databaseEntries.size());
      for (ExternalStorageLocation currentDbEntry : databaseEntries) {
        assertEquals(user1, currentDbEntry.getOperationUser());
        assertEquals(fileStorePathId1, currentDbEntry.getFileStore().getId());
        assertNotNull(currentDbEntry.getConnectedMediaFile());
        assertNotNull(currentDbEntry.getId());
        assertNotNull(currentDbEntry.getOperationDate());
        assertNotNull(currentDbEntry.getExternalStorageId());
      }

      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName1));
      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName2));
      assertTrue(
          "The file has not been created in IRODS", irodsClient.iRodsFileExists(irodsFileName3));

    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      teardownIrods(irodsFileName1);
      teardownIrods(irodsFileName2);
      teardownIrods(irodsFileName3);
    }
  }

  private String generateIrodsFileName(String filename) {
    return IRODS_TARGET_PATH + "/" + filename;
  }

  private EcatMediaFile uploadFileIntoRspace(String filename) throws IOException {
    MockMultipartFile mf =
        new MockMultipartFile("xfile", filename, "png", getTestResourceFileStream("Picture1.png"));
    RecordInformation imageInGallery = galleryController.uploadFile(mf, null, null, null).getData();
    return baseRecordManager.retrieveMediaFile(user1, imageInGallery.getId());
  }

  private void teardownIrods(String absolutePathFilename) {
    boolean deleteResult = false;
    try {
      deleteResult = irodsClient.deleteFilesFromNfs(Set.of(absolutePathFilename));
    } catch (UnsupportedOperationException e) {
      throw new RuntimeException(e);
    }
    assertTrue(
        "It was not possible to delete the iRods file: " + absolutePathFilename, deleteResult);
  }

  private Long createIrodsFileStore(NfsFileSystem fileSystem, String name, String path) {
    NfsFileStore fileStore = GalleryFilestoreTestUtils.createFileStore(name, user1, fileSystem);
    fileStore.setPath(path);
    nfsManager.saveNfsFileStore(fileStore);
    return fileStore.getId();
  }

  private MockHttpServletRequestBuilder createIrodsGetBuilder(User user, String apiKey) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/gallery/irods", user);
  }

  private MockHttpServletRequestBuilder createIrodsPostBuilder(
      User user, String apiKey, ApiNfsCredentials credentials, Operation operation) {
    return createBuilderForPostWithJSONBody(
        apiKey, "/gallery/irods/" + operation.toString(), user, credentials);
  }
}

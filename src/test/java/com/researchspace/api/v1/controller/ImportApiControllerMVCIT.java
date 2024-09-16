package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class ImportApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void testWordImport() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    MockMultipartFile mf =
        new MockMultipartFile(
            "file", "letterlegal5.doc", "ms/doc", getTestResourceFileStream("letterlegal5.doc"));

    // this will create an API inbox folder
    MvcResult result =
        mockMvc
            .perform(
                fileUpload(createUrl(API_VERSION.ONE, "/import/word"))
                    .file(mf)
                    .header("apiKey", apiKey))
            .andReturn();
    assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus());
    // now create in specific folder, and put any extracted images in a folder.
    Folder imgTarget = createImageTarget(anyUser);
    Folder rspaceDocTarget = createSubFolder(getRootFolderForUser(anyUser), "subfolder", anyUser);
    MvcResult result2 =
        mockMvc
            .perform(
                fileUpload(createUrl(API_VERSION.ONE, "/import/word"))
                    .file(mf)
                    .param("folderId", rspaceDocTarget.getId() + "")
                    .param("imageFolderId", imgTarget.getId() + "")
                    .header("apiKey", apiKey))
            .andReturn();
    assertEquals(HttpStatus.CREATED.value(), result2.getResponse().getStatus());
    assertEquals(1, getRecordCountInFolderForUser(rspaceDocTarget.getId()));

    // the sample html file returned by the dummy converter contains 3 images
    assertEquals(3, getRecordCountInFolderForUser(imgTarget.getId()));

    // now upload without a image folder specified, one will be created.
    final long totalFolderCount = getCountOfEntityTable("Folder");
    MvcResult result3 =
        mockMvc
            .perform(
                fileUpload(createUrl(API_VERSION.ONE, "/import/word"))
                    .file(mf)
                    .param("folderId", rspaceDocTarget.getId() + "")
                    .header("apiKey", apiKey))
            .andReturn();
    // a default image folder is created one - see RSPAC-1882
    assertEquals(totalFolderCount + 1, getCountOfEntityTable("Folder").intValue());
  }

  @Test
  public void testEvernoteImport() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    MockMultipartFile mf =
        new MockMultipartFile(
            "file",
            "EvernoteDump.enex",
            "text/xml",
            getTestResourceFileStream("EvernoteDump.enex"));
    Folder imgTarget = createImageTarget(anyUser);

    // this will create an API inbox folder
    MvcResult result =
        mockMvc
            .perform(
                fileUpload(createUrl(API_VERSION.ONE, "/import/evernote"))
                    .file(mf)
                    .param("imageFolderId", imgTarget.getId() + "")
                    .header("apiKey", apiKey))
            .andReturn();
    assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus());
    final int EXPECTED_IMAGE_COUNT = 2;
    assertEquals(EXPECTED_IMAGE_COUNT, getRecordCountInFolderForUser(imgTarget.getId()));
  }

  private Folder createImageTarget(User anyUser) {
    Folder imageGallery =
        recordMgr.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, anyUser);
    Folder imageTarget = createSubFolder(imageGallery, "image-subfolder", anyUser);
    return imageTarget;
  }
}

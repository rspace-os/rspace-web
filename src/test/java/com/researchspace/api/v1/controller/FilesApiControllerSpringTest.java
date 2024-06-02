package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiFileSearchResult;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.model.*;
import com.researchspace.model.record.Folder;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;

public class FilesApiControllerSpringTest extends SpringTransactionalTest {

  @Autowired private FilesApiController filesApi;

  private API_ModelTestUtils apiModelTestUtils = new API_ModelTestUtils();

  private User testUser;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void testGetFiles() throws Exception {

    // adding two images, a document and audio to the Gallery
    addImageToGallery(testUser);
    Folder subfolder = createImgGallerySubfolder("apiTest", testUser);
    addImageToGalleryFolder(subfolder, testUser);
    EcatDocumentFile documentFile = addDocumentToGallery(testUser);
    EcatAudio audioFile = addAudioFileToGallery(testUser);
    EcatChemistryFile chemFile = addChemistryFileToGallery("Aminoglutethimide.mol", testUser);

    // search for everything
    DocumentApiPaginationCriteria pgCrit = new DocumentApiPaginationCriteria();
    ApiFileSearchConfig searchConfig = new ApiFileSearchConfig();
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(pgCrit, "any");

    ApiFileSearchResult userFiles = filesApi.getFiles(pgCrit, searchConfig, errors, testUser);
    assertNotNull(userFiles);
    assertEquals(5, userFiles.getTotalHits().intValue());
    assertEquals(0, userFiles.getPageNumber().intValue());
    assertEquals(1, userFiles.getLinks().size());
    assertEquals(ApiLinkItem.SELF_REL, userFiles.getLinks().get(0).getRel());

    // search for 'image', 'document' and 'av' mediaTypes
    searchConfig.setMediaType("image");
    ApiFileSearchResult imageFiles = filesApi.getFiles(pgCrit, searchConfig, errors, testUser);
    assertNotNull(imageFiles);
    assertEquals(2, imageFiles.getTotalHits().intValue());

    searchConfig.setMediaType("document");
    ApiFileSearchResult documentFiles = filesApi.getFiles(pgCrit, searchConfig, errors, testUser);
    assertCorrectFileRetrieved(documentFile, documentFiles);

    searchConfig.setMediaType("av");
    ApiFileSearchResult avFiles = filesApi.getFiles(pgCrit, searchConfig, errors, testUser);
    assertCorrectFileRetrieved(audioFile, avFiles);

    searchConfig.setMediaType("chemistry");
    ApiFileSearchResult chemistryFiles = filesApi.getFiles(pgCrit, searchConfig, errors, testUser);
    assertCorrectFileRetrieved(chemFile, chemistryFiles);
  }

  private void assertCorrectFileRetrieved(EcatMediaFile file, ApiFileSearchResult searchResults) {
    assertNotNull(searchResults);
    assertEquals(1, searchResults.getTotalHits().intValue());
    apiModelTestUtils.assertApiFileMatchEcatMediaFile(searchResults.getFiles().get(0), file);
  }

  @Test
  public void testGetFileById() throws Exception {
    EcatImage galleryImage = addImageToGallery(testUser);

    ApiFile apiFile = filesApi.getFileById(galleryImage.getId(), testUser);
    assertNotNull(apiFile);
    apiModelTestUtils.assertApiFileMatchEcatMediaFile(apiFile, galleryImage);
  }

  @Test
  public void testGetFileBytes() throws Exception {
    EcatImage galleryImage = addImageToGallery(testUser);

    MockHttpServletResponse resp = new MockHttpServletResponse();
    filesApi.getFileBytes(galleryImage.getId(), testUser, resp);

    byte[] content = resp.getContentAsByteArray();
    assertNotNull(content);
    assertTrue(content.length > 0);

    byte[] resourceStream = RSpaceTestUtils.getResourceAsByteArray("Picture1.png");
    assertTrue(Arrays.equals(resourceStream, content));
  }
}

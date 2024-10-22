package com.researchspace.webapp.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** This test is used to reproduce the crudops operations on the MediaGallery */
public class GalleriesCrudopsMVCIT extends MVCTestBase {

  @Autowired private StructuredDocumentController strDocumentController;
  @Autowired private GalleryController galleryController;
  @Autowired private WorkspaceController workspaceController;

  private User user;
  private PaginationCriteria<BaseRecord> pgcrit = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    pgcrit = PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void copyGalleriesTest() throws Exception {

    AjaxReturnObject<GalleryData> res =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(res.getData());

    final int initialImgSize = res.getData().getItems().getHits().intValue();

    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "image.png", "png", getTestResourceFileStream("Picture1.png"));
    MvcResult result =
        mockMvc
            .perform(
                fileUpload("/gallery/ajax/uploadFile")
                    .file(mf)
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            .andReturn();

    Map<String, Object> json = parseJSONObjectFromResponseStream(result);
    assertNotNull(json.get("data"));
    Map<String, Object> data = (Map<String, Object>) json.get("data");
    Integer id = (Integer) data.get("id");
    assertNotNull(id);

    AjaxReturnObject<GalleryData> fres =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(fres.getData());

    final int finalImgSize = fres.getData().getItems().getHits().intValue();

    assertEquals(initialImgSize + 1, finalImgSize);

    final Long[] ids = {id.longValue()};
    final String[] newNames = {"newname"};

    galleryController.copyGalleries(ids, newNames, new MockPrincipal(user.getUsername()));

    AjaxReturnObject<GalleryData> fres2 =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(fres2.getData());

    final int finalImgSize2 = fres2.getData().getItems().getHits().intValue();

    assertEquals(finalImgSize + 1, finalImgSize2);

    // test unauthorised user can't copy:
    final User other = createInitAndLoginAnyUser();
    assertAuthorisationExceptionThrown(
        () ->
            galleryController.copyGalleries(ids, newNames, new MockPrincipal(other.getUsername())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void renameGalleriesTest() throws Exception {
    logoutAndLoginAs(user);

    AjaxReturnObject<GalleryData> res =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(res.getData());

    final int initialImgSize = res.getData().getItems().getHits().intValue();

    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "image.png", "png", getTestResourceFileStream("Picture1.png"));
    MvcResult result =
        mockMvc
            .perform(
                fileUpload("/gallery/ajax/uploadFile")
                    .file(mf)
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            .andReturn();

    Map<String, Object> json = parseJSONObjectFromResponseStream(result);
    assertNotNull(json.get("data"));
    Map<String, Object> data = (Map<String, Object>) json.get("data");
    Integer id = (Integer) data.get("id");
    String name = String.valueOf(data.get("name"));

    assertNotNull(id);
    assertNotNull(name);

    AjaxReturnObject<GalleryData> fres =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(fres.getData());

    final int finalImgSize = fres.getData().getItems().getHits().intValue();
    assertEquals(initialImgSize + 1, finalImgSize);

    strDocumentController.rename(id, "newname", new MockPrincipal(user.getUsername()));

    AjaxReturnObject<GalleryData> fres2 =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(fres2.getData());

    assertFalse(name.equalsIgnoreCase("newname"));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void deleteGalleriesTest() throws Exception {
    logoutAndLoginAs(user);
    mockPrincipal = new MockPrincipal(user.getUsername());

    Folder imageFolder =
        recordMgr.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user);

    AjaxReturnObject<GalleryData> res =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(res.getData());

    final int initialImgSize = res.getData().getItems().getHits().intValue();
    // upload a new file
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "image.png", "png", getTestResourceFileStream("Picture1.png"));
    MvcResult result =
        mockMvc
            .perform(fileUpload("/gallery/ajax/uploadFile").file(mf).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    Map<String, Object> json = parseJSONObjectFromResponseStream(result);
    assertNotNull(json.get("data"));
    Map<String, Object> data = (Map<String, Object>) json.get("data");
    Integer id = (Integer) data.get("id");
    assertNotNull(id);
    // check the count has increased by 1
    AjaxReturnObject<GalleryData> fres =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(fres.getData());

    final int finalImgSize = fres.getData().getItems().getHits().intValue();
    assertEquals(initialImgSize + 1, finalImgSize);

    Long[] idsToDelete = new Long[] {id.longValue()};

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setUserPrincipal(mockPrincipal);

    MockHttpSession session2 = new MockHttpSession();
    // now delete the
    final WorkspaceSettings srchInput = new WorkspaceSettings();
    srchInput.setParentFolderId(imageFolder.getId());
    workspaceController.delete(
        idsToDelete,
        null,
        model,
        srchInput,
        req,
        mockPrincipal,
        session2,
        new MockHttpServletResponse());

    AjaxReturnObject<GalleryData> fres2 =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, false, pgcrit, null);
    assertNotNull(fres2.getData());

    final int finalImgSize2 = fres2.getData().getItems().getHits().intValue();
    assertEquals(finalImgSize - 1, finalImgSize2);
  }
}

package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class GalleryApiControllerMVCIT extends API_MVC_TestBase {

  User anyUser;
  String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void testDownloadGalleryFile() throws Exception {
    EcatImage ecatImage = addImageToGallery(anyUser);
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/gallery/" + ecatImage.getId() + "/download",
                    anyUser))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    assertEquals("application/octet-stream", result.getResponse().getContentType());
    byte[] responseBytes = result.getResponse().getContentAsByteArray();
    assertEquals(72169, responseBytes.length);
  }
}

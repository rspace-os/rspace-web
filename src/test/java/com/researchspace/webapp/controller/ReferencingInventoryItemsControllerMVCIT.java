package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_InventoryTestBase;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.record.StructuredDocument;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the session-authenticated reverse-lookup endpoint used by the ELN-side
 * "Related inventory items" panels. Unlike the {@code /api/inventory/v1} endpoint (which requires
 * an API key / OAuth bearer), this is reached with the browser session, so the test drives it with
 * a session principal rather than an API key.
 */
public class ReferencingInventoryItemsControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void sessionEndpointReturnsInventorySourcesLinkingToElnDocument() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    StructuredDocument target = createBasicDocumentInRootFolderWithText(user, "linkable doc");
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    // create the inventory -> document link via the API (needs the API key)
    String updateJson = buildLinkExtraFieldUpdate(target.getOid().getIdString(), "References");
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(apiKey, "/samples/" + source.getId(), user, updateJson))
        .andExpect(status().isOk());

    // read it back through the SESSION endpoint (no API key), as the ELN panels do
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/getReferencingInventoryItems/" + target.getOid().getIdString())
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertEquals(1, body.getReferencingItems().size());
    assertEquals(source.getGlobalId(), body.getReferencingItems().get(0).getSourceGlobalId());
    assertEquals("References", body.getReferencingItems().get(0).getRelationType());
  }

  @Test
  public void sessionEndpointReturnsEmptyForUnreferencedRecord() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument target = createBasicDocumentInRootFolderWithText(user, "lonely doc");

    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/getReferencingInventoryItems/" + target.getOid().getIdString())
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertEquals(0, body.getReferencingItems().size());
  }

  @Test
  public void sessionAttachmentsEndpointReturnsItemsAttachingGalleryFile() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    // attach a gallery file to the sample; the returned attachment carries the gallery file's id
    InventoryFile galleryAttachment =
        addGalleryFileToInventoryItem(new GlobalIdentifier(sample.getGlobalId()), user);
    String galleryFileGlobalId = galleryAttachment.getMediaFileGlobalIdentifier();

    // read it back through the SESSION endpoint (no API key), as the gallery info panel does
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/getAttachingInventoryItems/" + galleryFileGlobalId)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertEquals(1, body.getReferencingItems().size());
    assertEquals(sample.getGlobalId(), body.getReferencingItems().get(0).getSourceGlobalId());
    // attachments carry no DataCite relation type; the client supplies the "Attachment" label
    assertNull(body.getReferencingItems().get(0).getRelationType());
  }

  @Test
  public void sessionAttachmentsEndpointReturnsEmptyForUnattachedGalleryFile() throws Exception {
    User user = createInitAndLoginAnyUser();
    EcatImage image = addImageToGallery(user);

    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/getAttachingInventoryItems/" + image.getOid().getIdString())
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertEquals(0, body.getReferencingItems().size());
  }

  private String buildLinkExtraFieldUpdate(String targetGlobalId, String relationType) {
    // newFieldRequest is WRITE_ONLY on the DTO so Jackson would drop it on output; build by hand.
    return "{\"extraFields\":[{"
        + "\"name\":\"Link\","
        + "\"type\":\"link\","
        + "\"newFieldRequest\":true,"
        + "\"link\":{\"relationType\":\""
        + relationType
        + "\",\"targetGlobalId\":\""
        + targetGlobalId
        + "\"}"
        + "}]}";
  }
}

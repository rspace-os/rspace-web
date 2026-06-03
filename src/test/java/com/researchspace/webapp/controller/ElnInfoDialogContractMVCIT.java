package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_InventoryTestBase;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end MVC contract test for the RSDEV-1131 "ELN-fidelity info button" dialog. The dialog is
 * a React/MUI component in the Inventory module that opens when a user clicks the info button on an
 * Inventory Link field whose target is an ELN record (SD document, NB notebook, GL gallery file).
 * It adds no new backend endpoint; instead it reuses the SAME session-authenticated endpoints the
 * ELN record-info panel uses:
 *
 * <ul>
 *   <li>{@code GET /workspace/getRecordInformation?recordId={numericId}} -> the core metadata table
 *       (name, global id, type, owner, dates) that the dialog renders, and
 *   <li>{@code GET /workspace/getReferencingInventoryItems/{globalId}} -> the "Related inventory
 *       items" section, which must list the Inventory item(s) that link to the ELN record.
 * </ul>
 *
 * <p>This test drives both endpoints with a browser-session principal (as the SPA does, via
 * same-origin cookies), having first created the Inventory -> ELN links through the API. It asserts
 * the slice of the contract the dialog actually depends on, for each supported ELN target type.
 */
public class ElnInfoDialogContractMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void documentInfoEndpointReturnsFieldsTheDialogRenders() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "linkable doc");

    MvcResult result = getRecordInformation(doc.getId(), user);
    DetailedRecordInformation info =
        getFromJsonAjaxReturnObject(result, DetailedRecordInformation.class);

    // core metadata table fields the dialog renders for a document
    assertNotNull("dialog needs the core record info", info);
    assertEquals(doc.getId(), info.getId());
    assertEquals(doc.getName(), info.getName());
    assertEquals("Structured Document", info.getType());
    assertEquals(doc.getGlobalIdentifier(), info.getOid().toString());
    assertEquals(user.getUsername(), info.getOwnerUsername());
    assertEquals(user.getFullName(), info.getOwnerFullName());
    assertNotNull("dialog renders the created date", info.getCreationDate());
    assertNotNull("dialog renders the modified date", info.getModificationDate());
  }

  @Test
  public void referencingItemsEndpointReturnsTheInventorySourceForADocumentTarget()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "linkable doc");
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    linkInventoryItemToElnTarget(user, apiKey, source, doc.getGlobalIdentifier());

    assertReferencingItem(user, doc.getGlobalIdentifier(), source);
  }

  @Test
  public void referencingItemsEndpointReturnsTheInventorySourceForANotebookTarget()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    Notebook notebook =
        createNotebookWithNEntries(
            folderMgr.getRootFolderForUser(user).getId(), "linkable notebook", 1, user);
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    linkInventoryItemToElnTarget(user, apiKey, source, notebook.getGlobalIdentifier());

    assertReferencingItem(user, notebook.getGlobalIdentifier(), source);
  }

  @Test
  public void referencingItemsEndpointReturnsTheInventorySourceForAGalleryFileTarget()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    EcatImage galleryFile = addImageToGallery(user);
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    linkInventoryItemToElnTarget(user, apiKey, source, galleryFile.getGlobalIdentifier());

    assertReferencingItem(user, galleryFile.getGlobalIdentifier(), source);
  }

  /**
   * Reproduces the dialog's record-info call: a session-authenticated GET against the
   * WorkspaceController endpoint, keyed by the numeric DB id (the SPA derives this by stripping the
   * global-id prefix).
   */
  private MvcResult getRecordInformation(Long recordId, User user) throws Exception {
    return mockMvc
        .perform(
            get("/workspace/getRecordInformation")
                .param("recordId", recordId.toString())
                .principal(user::getUsername))
        .andExpect(status().isOk())
        .andReturn();
  }

  /**
   * Creates the Inventory -> ELN link through the API (which requires the API key), mirroring the
   * way a user attaches a Link extra-field to an Inventory item.
   */
  private void linkInventoryItemToElnTarget(
      User user, String apiKey, ApiSampleWithFullSubSamples source, String targetGlobalId)
      throws Exception {
    String updateJson = buildLinkExtraFieldUpdate(targetGlobalId, "References");
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(apiKey, "/samples/" + source.getId(), user, updateJson))
        .andExpect(status().isOk());
  }

  /**
   * Reproduces the dialog's "Related inventory items" call: a session-authenticated GET against the
   * reverse-lookup endpoint, keyed by the ELN record's global-id string.
   */
  private void assertReferencingItem(
      User user, String targetGlobalId, ApiSampleWithFullSubSamples expectedSource)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/workspace/getReferencingInventoryItems/" + targetGlobalId)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertEquals(1, body.getReferencingItems().size());
    assertEquals(
        expectedSource.getGlobalId(), body.getReferencingItems().get(0).getSourceGlobalId());
    assertEquals("References", body.getReferencingItems().get(0).getRelationType());
    assertTrue(body.getReferencingItems().get(0).getSourceName() != null);
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

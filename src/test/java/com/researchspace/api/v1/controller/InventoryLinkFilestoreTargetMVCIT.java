package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

public class InventoryLinkFilestoreTargetMVCIT extends API_MVC_InventoryTestBase {

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  private NfsFileStore filestoreFor(User owner) {
    NfsFileSystem fs = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsMgr.saveNfsFileSystem(fs);
    return nfsMgr.createAndSaveNewFileStore(fs.getId(), "My S3", "/data", owner);
  }

  private static String linkJson(Long filestoreId, String remotePath) {
    return "{\"extraFields\":[{\"name\":\"Raw data\",\"type\":\"link\",\"newFieldRequest\":true,"
        + "\"link\":{\"relationType\":\"IsSourceOf\",\"filestoreTarget\":{\"filestoreId\":"
        + filestoreId
        + ",\"remotePath\":\""
        + remotePath
        + "\"}}}]}";
  }

  @Test
  public void createsAndReadsALinkToAFilestoreItem() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    NfsFileStore store = filestoreFor(user);
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/samples/" + source.getId(),
                user,
                linkJson(store.getId(), "XRD/run.dat/")))
        .andExpect(status().isOk());

    MvcResult getResult =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/{id}", user, source.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryLink link =
        getFromJsonResponseBody(getResult, ApiSampleWithFullSubSamples.class)
            .getExtraFields()
            .stream()
            .filter(field -> field.getLink() != null)
            .findFirst()
            .orElseThrow()
            .getLink();
    assertEquals("IsSourceOf", link.getRelationType());
    assertEquals(store.getId(), link.getFilestoreTarget().getFilestoreId());
    assertEquals("/XRD/run.dat", link.getFilestoreTarget().getRemotePath());
    assertNull(link.getTargetGlobalId());
    assertNull(link.getVersionPin());
  }

  @Test
  public void refusesAnotherUsersFilestoreExactlyLikeAnUnknownOne() throws Exception {
    NfsFileStore othersStore = filestoreFor(createInitAndLoginAnyUser());
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    ApiError others = refusal(apiKey, user, source, othersStore.getId());
    ApiError unknown = refusal(apiKey, user, source, 999_999L);

    assertEquals("errors.inventory.field.linkTargetNotFound", others.getMessageCode());
    assertEquals(others.getMessageCode(), unknown.getMessageCode());
    assertEquals(
        "Link target '"
            + othersStore.getId()
            + ":/XRD/run.dat' could not be found or you do not have permission to read it.",
        others.getMessage());
  }

  @Test
  public void summaryNamesTheFilestoreToItsOwnerAndTellsAStrangerNothing() throws Exception {
    User owner = createInitAndLoginAnyUser();
    String ownerKey = createNewApiKeyForUser(owner);
    NfsFileStore store = filestoreFor(owner);

    JsonNode own = summaryJson(ownerKey, owner, store.getId());
    assertTrue(own.get("readable").asBoolean());
    assertEquals("My S3", own.get("name").asText());
    assertEquals("FILESTORE_ITEM", own.get("type").asText());

    User stranger = createInitAndLoginAnyUser();
    String strangerKey = createNewApiKeyForUser(stranger);
    JsonNode redacted = summaryJson(strangerKey, stranger, store.getId());
    assertFalse(redacted.get("readable").asBoolean());
    assertFalse(redacted.hasNonNull("name"));
    // ADR 0002: someone else's filestore and one that does not exist read the same
    assertEquals(redacted, summaryJson(strangerKey, stranger, 999_999L));
  }

  private ApiError refusal(
      String apiKey, User user, ApiSampleWithFullSubSamples source, Long filestoreId)
      throws Exception {
    MvcResult refused =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/samples/" + source.getId(),
                    user,
                    linkJson(filestoreId, "/XRD/run.dat")))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    return getErrorFromJsonResponseBody(refused, ApiError.class);
  }

  private JsonNode summaryJson(String apiKey, User user, Long filestoreId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                        API_VERSION.ONE,
                        apiKey,
                        "/linkTargets/filestores/{filestoreId}/summary",
                        user,
                        filestoreId)
                    .param("remotePath", "/XRD/run.dat"))
            .andExpect(status().isOk())
            .andReturn();
    return new ObjectMapper().readTree(result.getResponse().getContentAsString());
  }
}

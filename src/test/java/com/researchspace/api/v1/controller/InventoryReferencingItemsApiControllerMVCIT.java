package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class InventoryReferencingItemsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void referencingItemsEndpointReturnsEmptyListForUnusedTarget() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/{id}/referencingItems",
                    user,
                    target.getId()))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertNotNull(body);
    assertEquals(0, body.getReferencingItems().size());
  }

  @Test
  public void referencingItemsEndpointSurfacesSourcesLinkingToTarget() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);

    String updateJson = buildLinkExtraFieldUpdate(target.getGlobalId(), "IsCalibratedBy");
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(apiKey, "/samples/" + source.getId(), user, updateJson))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/{id}/referencingItems",
                    user,
                    target.getId()))
            .andExpect(status().isOk())
            .andReturn();

    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
    assertEquals(1, body.getReferencingItems().size());
    assertEquals("IsCalibratedBy", body.getReferencingItems().get(0).getRelationType());
    assertEquals(source.getGlobalId(), body.getReferencingItems().get(0).getSourceGlobalId());
  }

  @Test
  public void referencingItemsHidesItemsNotReadableByCaller() throws Exception {
    User owner = createInitAndLoginAnyUser();
    String ownerKey = createNewApiKeyForUser(owner);
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(owner);
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(owner);

    String updateJson = buildLinkExtraFieldUpdate(target.getGlobalId(), "References");
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                ownerKey, "/samples/" + source.getId(), owner, updateJson))
        .andExpect(status().isOk());

    User other = createInitAndLoginAnyUser();
    String otherKey = createNewApiKeyForUser(other);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    otherKey,
                    "/samples/{id}/referencingItems",
                    other,
                    target.getId()))
            .andReturn();

    if (result.getResponse().getStatus() == 200) {
      ApiInventoryReferencingItems body =
          getFromJsonResponseBody(result, ApiInventoryReferencingItems.class);
      assertEquals(
          0,
          body.getReferencingItems().size(),
          "items the requester cannot read should not appear in the back-ref list");
    }
  }

  private String buildLinkExtraFieldUpdate(String targetGlobalId, String relationType) {
    // hand-build JSON: newFieldRequest is WRITE_ONLY on the DTO so Jackson would drop it on output.
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

package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end MVC coverage for editing an existing Link extra-field. The frontend ships versionPin
 * (and relationType) changes via PUT alongside the existing extra-field id; we verify that those
 * round-trip back through a subsequent GET, which guards against the bug where
 * applyChangesToDatabaseExtraField was previously dropping the link payload silently.
 */
public class InventoryLinkExtraFieldUpdateMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void updatesVersionPinOnExistingLinkExtraField() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    // first PUT: create the Link extra-field on the source
    String createJson =
        buildLinkExtraFieldCreate(target.getGlobalId(), "References", /* versionPin */ null);
    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, createJson))
            .andExpect(status().isOk())
            .andReturn();

    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(createResult, ApiSampleWithFullSubSamples.class);
    ApiExtraField createdLinkField = findLinkExtraField(created);
    assertNotNull(createdLinkField, "link extra-field should be present after create");
    assertNull(createdLinkField.getLink().getVersionPin(), "initial versionPin should be null");
    Long extraFieldId = createdLinkField.getId();

    // second PUT: update only the versionPin
    String updateJson =
        buildLinkExtraFieldUpdate(
            extraFieldId, target.getGlobalId(), "References", /* versionPin */ 7L);
    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    ApiSampleWithFullSubSamples updated =
        getFromJsonResponseBody(updateResult, ApiSampleWithFullSubSamples.class);
    ApiExtraField updatedField = findLinkExtraField(updated);
    assertNotNull(updatedField);
    assertEquals(
        Long.valueOf(7L),
        updatedField.getLink().getVersionPin(),
        "versionPin should now be 7 after the update PUT");

    // and a fresh GET should also see the new pin
    MvcResult getResult =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/{id}", user, source.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples fetched =
        getFromJsonResponseBody(getResult, ApiSampleWithFullSubSamples.class);
    ApiExtraField fetchedField = findLinkExtraField(fetched);
    assertNotNull(fetchedField);
    assertEquals(Long.valueOf(7L), fetchedField.getLink().getVersionPin());
  }

  @Test
  public void clearsVersionPinWhenSetToNull() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    // create with a versionPin already set
    String createJson =
        buildLinkExtraFieldCreate(target.getGlobalId(), "References", /* versionPin */ 3L);
    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, createJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(createResult, ApiSampleWithFullSubSamples.class);
    ApiExtraField createdLinkField = findLinkExtraField(created);
    Long extraFieldId = createdLinkField.getId();
    assertEquals(Long.valueOf(3L), createdLinkField.getLink().getVersionPin());

    // update with versionPin omitted -> should clear to null
    String updateJson =
        buildLinkExtraFieldUpdate(
            extraFieldId, target.getGlobalId(), "References", /* versionPin */ null);
    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples updated =
        getFromJsonResponseBody(updateResult, ApiSampleWithFullSubSamples.class);
    ApiExtraField updatedField = findLinkExtraField(updated);
    assertNull(
        updatedField.getLink().getVersionPin(),
        "versionPin should be null after explicitly setting it to null");
  }

  @Test
  public void updatesTargetOnExistingLinkExtraField() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples firstTarget = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples secondTarget = createBasicSampleForUser(user);

    String createJson =
        buildLinkExtraFieldCreate(firstTarget.getGlobalId(), "References", /* versionPin */ null);
    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, createJson))
            .andExpect(status().isOk())
            .andReturn();
    Long extraFieldId =
        findLinkExtraField(getFromJsonResponseBody(createResult, ApiSampleWithFullSubSamples.class))
            .getId();

    // retarget the existing link to a different record; previously this was
    // silently ignored and the response (and any later GET) kept the old target
    String updateJson =
        buildLinkExtraFieldUpdate(
            extraFieldId, secondTarget.getGlobalId(), "References", /* versionPin */ null);
    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples updated =
        getFromJsonResponseBody(updateResult, ApiSampleWithFullSubSamples.class);
    assertEquals(
        secondTarget.getGlobalId(), findLinkExtraField(updated).getLink().getTargetGlobalId());

    // a fresh GET also sees the new target
    MvcResult getResult =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/{id}", user, source.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples fetched =
        getFromJsonResponseBody(getResult, ApiSampleWithFullSubSamples.class);
    assertEquals(
        secondTarget.getGlobalId(), findLinkExtraField(fetched).getLink().getTargetGlobalId());
  }

  @Test
  public void updatesRelationTypeOnExistingLinkExtraField() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    String createJson =
        buildLinkExtraFieldCreate(target.getGlobalId(), "References", /* versionPin */ null);
    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, createJson))
            .andExpect(status().isOk())
            .andReturn();
    Long extraFieldId =
        findLinkExtraField(getFromJsonResponseBody(createResult, ApiSampleWithFullSubSamples.class))
            .getId();

    String updateJson =
        buildLinkExtraFieldUpdate(
            extraFieldId, target.getGlobalId(), "IsCalibratedBy", /* versionPin */ null);
    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples updated =
        getFromJsonResponseBody(updateResult, ApiSampleWithFullSubSamples.class);
    ApiExtraField updatedField = findLinkExtraField(updated);
    assertEquals("IsCalibratedBy", updatedField.getLink().getRelationType());
  }

  private ApiExtraField findLinkExtraField(ApiSampleWithFullSubSamples sample) {
    return sample.getExtraFields().stream()
        .filter(ef -> ef.getLink() != null)
        .findFirst()
        .orElse(null);
  }

  private String buildLinkExtraFieldCreate(
      String targetGlobalId, String relationType, Long versionPin) {
    String versionPinFragment = versionPin == null ? "null" : versionPin.toString();
    return "{\"extraFields\":[{"
        + "\"name\":\"Linked sample\","
        + "\"type\":\"link\","
        + "\"newFieldRequest\":true,"
        + "\"link\":{\"relationType\":\""
        + relationType
        + "\",\"targetGlobalId\":\""
        + targetGlobalId
        + "\",\"versionPin\":"
        + versionPinFragment
        + "}"
        + "}]}";
  }

  private String buildLinkExtraFieldUpdate(
      Long extraFieldId, String targetGlobalId, String relationType, Long versionPin) {
    // updates an existing extra-field: id is required, newFieldRequest is omitted/false.
    String versionPinFragment = versionPin == null ? "null" : versionPin.toString();
    return "{\"extraFields\":[{"
        + "\"id\":"
        + extraFieldId
        + ","
        + "\"name\":\"Linked sample\","
        + "\"type\":\"link\","
        + "\"link\":{\"relationType\":\""
        + relationType
        + "\",\"targetGlobalId\":\""
        + targetGlobalId
        + "\",\"versionPin\":"
        + versionPinFragment
        + "}"
        + "}]}";
  }
}

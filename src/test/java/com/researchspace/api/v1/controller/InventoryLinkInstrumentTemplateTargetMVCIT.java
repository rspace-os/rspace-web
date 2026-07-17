package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for linking to an instrument template (RSDEV-1190): an NT Global ID is a
 * valid link target, accepted by validation, round-tripping through the API, and surfaced by the
 * generic referencingItems endpoint.
 *
 * <p>Authored alongside the feature; not run automatically (extends a real-transaction MVC base).
 */
public class InventoryLinkInstrumentTemplateTargetMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void createsExtraFieldLinkTargetingAnInstrumentTemplateAndListsBackReference()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    MvcResult created =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/instrumentTemplates",
                    user,
                    "{ \"name\": \"linkable instrument template\" }"))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrumentTemplate template = getFromJsonResponseBody(created, ApiInstrumentTemplate.class);

    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);
    String updateJson =
        "{\"extraFields\":[{"
            + "\"name\":\"Uses template\","
            + "\"type\":\"link\","
            + "\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + template.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    MvcResult updated =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples sourceAfter =
        getFromJsonResponseBody(updated, ApiSampleWithFullSubSamples.class);
    ApiExtraField linkField =
        sourceAfter.getExtraFields().stream()
            .filter(ef -> ef.getLink() != null)
            .findFirst()
            .orElse(null);
    assertNotNull(linkField);
    assertEquals(template.getGlobalId(), linkField.getLink().getTargetGlobalId());

    MvcResult refs =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/referencingItems/" + template.getGlobalId(), user))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(refs, ApiInventoryReferencingItems.class);
    assertEquals(1, body.getReferencingItems().size());
    assertEquals(source.getGlobalId(), body.getReferencingItems().get(0).getSourceGlobalId());
  }
}

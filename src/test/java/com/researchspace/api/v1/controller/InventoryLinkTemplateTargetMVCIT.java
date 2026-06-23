package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.RSUnitDef;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for linking to a sample template (RSDEV-1131): an IT Global ID is a valid
 * link target, accepted by the create-time validation and round-tripping through the API.
 *
 * <p>Authored as part of the bug fix; not run automatically (extends a real-transaction MVC base).
 */
public class InventoryLinkTemplateTargetMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void createsExtraFieldLinkTargetingASampleTemplate() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(user);

    ApiSampleWithFullSubSamples source = createBasicSampleForUser(user);
    ApiSampleTemplate template = createBasicTemplate(apiKey, user);

    String createJson =
        "{\"extraFields\":[{"
            + "\"name\":\"Made with template\","
            + "\"type\":\"link\","
            + "\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + template.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + source.getId(), user, createJson))
            .andExpect(status().isOk())
            .andReturn();

    ApiSampleWithFullSubSamples updated =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    ApiExtraField linkField =
        updated.getExtraFields().stream()
            .filter(ef -> ef.getLink() != null)
            .findFirst()
            .orElse(null);
    assertNotNull(linkField, "the link extra-field should have been created");
    assertEquals(template.getGlobalId(), linkField.getLink().getTargetGlobalId());
    assertEquals("References", linkField.getLink().getRelationType());
  }

  private ApiSampleTemplate createBasicTemplate(String apiKey, User user) throws Exception {
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("link target template");
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    templatePost.setSampleSource(SampleSource.LAB_CREATED);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", user, templatePost))
            // the sampleTemplates POST returns 201 Created (see SampleTemplatesApiControllerMVCIT)
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }
}

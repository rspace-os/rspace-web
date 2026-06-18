package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.RSUnitDef;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for {@code ApiSampleTemplate.samplesToUpdateCount} (RSDEV-1131): the count
 * exposed on a template GET reflects only the requesting user's samples that were created from an
 * older version of the template, and is what the UI gates the "Update Samples" action on.
 *
 * <p>The regression this guards: linking a sample to a template (a link extra-field whose target is
 * the template) must not make the template look like it has samples to update - the linking sample
 * is not created from the template, so the count stays 0 and the action stays hidden.
 *
 * <p>Authored as part of the bug fix; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class SampleTemplateUpdatableSamplesCountMVCIT extends API_MVC_InventoryTestBase {

  private User anyUser;
  private String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void countTracksSamplesCreatedFromAnOlderTemplateVersion() throws Exception {
    ApiSampleTemplate template = createBasicTemplate("counted-samples template");

    // a sample created from the template's current version: nothing to update yet
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("sample from template v1");
    sample.setTemplateId(template.getId());
    sampleApiMgr.createNewApiSample(sample, anyUser);
    assertEquals(0, retrieveSampleTemplate(template.getId()).getSamplesToUpdateCount());

    // bump the template version: the existing sample is now behind and counts as updatable
    ApiSampleTemplate bumped =
        putSampleTemplate("{\"name\":\"counted-samples template v2\"}", template.getId());
    assertEquals(2, bumped.getVersion());
    assertEquals(1, retrieveSampleTemplate(template.getId()).getSamplesToUpdateCount());

    // once the sample is brought up to date there is again nothing to update
    mockMvc
        .perform(
            createBuilderForPost(
                API_VERSION.ONE,
                apiKey,
                "/sampleTemplates/"
                    + template.getId()
                    + "/actions/updateSamplesToLatestTemplateVersion",
                anyUser))
        .andExpect(status().isOk())
        .andReturn();
    assertEquals(0, retrieveSampleTemplate(template.getId()).getSamplesToUpdateCount());
  }

  @Test
  public void linkingASampleToATemplateDoesNotCountAsSomethingToUpdate() throws Exception {
    ApiSampleTemplate template = createBasicTemplate("link-target template");

    // a sample that is NOT created from the template, then linked to it via a link extra-field
    ApiSampleWithFullSubSamples linkingSample = createBasicSampleForUser(anyUser);
    String linkJson =
        "{\"extraFields\":[{"
            + "\"name\":\"Made with template\","
            + "\"type\":\"link\","
            + "\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + template.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    MvcResult linkResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + linkingSample.getId(), anyUser, linkJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleWithFullSubSamples linked =
        getFromJsonResponseBody(linkResult, ApiSampleWithFullSubSamples.class);
    assertNotNull(
        linked.getExtraFields().stream()
            .filter(ef -> ef.getLink() != null)
            .findFirst()
            .orElse(null),
        "the link extra-field should have been created");

    // the link must not make the template look like it has samples to update
    assertEquals(0, retrieveSampleTemplate(template.getId()).getSamplesToUpdateCount());
  }

  private ApiSampleTemplate createBasicTemplate(String name) throws Exception {
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName(name);
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    templatePost.setSampleSource(SampleSource.LAB_CREATED);
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }

  private ApiSampleTemplate putSampleTemplate(String json, Long templateId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/sampleTemplates/" + templateId, anyUser, json))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }

  private ApiSampleTemplate retrieveSampleTemplate(Long id) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/sampleTemplates/" + id, anyUser))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }
}

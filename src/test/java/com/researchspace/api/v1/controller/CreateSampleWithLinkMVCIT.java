package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for RSDEV-1131 "create item and link at the same time": a brand-new Sample
 * created in a single POST whose extra-fields already carry a Link must persist that link.
 *
 * <p>Regression being guarded: the create path built the {@code ExtraLinkField} (name, type,
 * content) but never created its {@code InventoryLink} from the incoming {@code link} payload, so
 * the link was silently dropped on save even though the field itself was applied. The update (PUT)
 * path did not have this gap, so the bug only showed when the link was created together with the
 * sample rather than added afterwards.
 *
 * <p>Authored as part of the bug fix; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class CreateSampleWithLinkMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired SampleApiManager sampleApiManager;

  private User anyUser;
  private String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void linkInExtraFieldIsPersistedWhenSampleIsCreatedInOnePost() throws Exception {
    // a separate sample to be the link target (a different record, so the self-link guard,
    // which the not-yet-created source could not trip anyway, is plainly not the thing under test)
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);

    // create a brand-new sample whose extra-fields already carry a link to the target, in the
    // SAME POST - i.e. the user adds a custom Link field while creating the item and saves once
    String createJson =
        "{\"name\":\"sample created with a link\","
            + "\"extraFields\":[{"
            + "\"name\":\"Related items\","
            + "\"type\":\"link\","
            + "\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + target.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";

    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, createJson))
            // the samples POST returns 201 Created (see SamplesApiControllerMVCIT)
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    // the link round-trips in the create response ...
    ApiExtraField responseLink = findLinkField(created.getExtraFields());
    assertNotNull(responseLink, "the link extra-field should be present in the create response");
    assertEquals(target.getGlobalId(), responseLink.getLink().getTargetGlobalId());
    assertEquals("References", responseLink.getLink().getRelationType());

    // ... and is actually persisted: re-read the sample fresh from the database. Before the fix the
    // ExtraLinkField was saved with a null InventoryLink, so this is where the bug surfaced.
    ApiSample reloaded = sampleApiManager.getApiSampleById(created.getId(), anyUser);
    ApiExtraField persistedLink = findLinkField(reloaded.getExtraFields());
    assertNotNull(persistedLink, "the link must be saved with the new sample, not dropped on save");
    assertEquals(target.getGlobalId(), persistedLink.getLink().getTargetGlobalId());
    assertEquals("References", persistedLink.getLink().getRelationType());
  }

  private ApiExtraField findLinkField(List<ApiExtraField> extraFields) {
    return extraFields.stream().filter(ef -> ef.getLink() != null).findFirst().orElse(null);
  }
}

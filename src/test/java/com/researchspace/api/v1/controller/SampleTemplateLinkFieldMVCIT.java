package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for the structured Link template field (RSDEV-1131): a Sample template can
 * define a Link field carrying an allowed-relation-types whitelist; that whitelist round-trips
 * through the API and is inherited by samples created from the template; and a sample cannot set a
 * relation type outside the template's whitelist.
 *
 * <p>Authored as part of the feature; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class SampleTemplateLinkFieldMVCIT extends API_MVC_InventoryTestBase {

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
  public void templateLinkFieldWhitelistRoundTripsAndIsInheritedBySample() throws Exception {
    ApiSampleTemplate savedTemplate = createTemplateWithLinkField();

    ApiInventoryEntityField savedLinkField = findLinkField(savedTemplate.getFields());
    assertNotNull(savedLinkField, "saved template should contain a link field");
    assertEquals(ApiFieldType.LINK, savedLinkField.getType());
    assertEquals(List.of("References", "IsDerivedFrom"), savedLinkField.getAllowedRelationTypes());

    // instantiate a sample from the template: it inherits the link field + whitelist (shallowCopy)
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from link template");
    apiSample.setTemplateId(savedTemplate.getId());
    ApiSampleWithFullSubSamples created = sampleApiManager.createNewApiSample(apiSample, anyUser);
    ApiSample fetched = sampleApiManager.getApiSampleById(created.getId(), anyUser);

    ApiInventoryEntityField inheritedLinkField = findLinkField(fetched.getFields());
    assertNotNull(inheritedLinkField, "sample should inherit the template's link field");
    assertEquals(ApiFieldType.LINK, inheritedLinkField.getType());
    assertEquals(
        List.of("References", "IsDerivedFrom"), inheritedLinkField.getAllowedRelationTypes());
  }

  @Test
  public void rejectsRelationTypeOutsideTemplateWhitelist() throws Exception {
    ApiSampleTemplate savedTemplate = createTemplateWithLinkField();
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample with out-of-set relation");
    apiSample.setTemplateId(savedTemplate.getId());
    ApiSampleWithFullSubSamples created = sampleApiManager.createNewApiSample(apiSample, anyUser);
    ApiSample fetched = sampleApiManager.getApiSampleById(created.getId(), anyUser);
    Long linkFieldId = findLinkField(fetched.getFields()).getId();

    // link to a different record (not the sample itself) so the self-link guard does not fire
    // first; the rejection under test is the relation-type whitelist, not the self-link rule.
    ApiSampleWithFullSubSamples linkTarget = createBasicSampleForUser(anyUser);

    // attempt to set a relation type that is a valid DataCite type but not in the whitelist
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setRelationType("IsCitedBy");
    apiLink.setTargetGlobalId(linkTarget.getGlobalId());
    ApiInventoryEntityField linkUpdate = new ApiInventoryEntityField();
    linkUpdate.setId(linkFieldId);
    linkUpdate.setType(ApiFieldType.LINK);
    linkUpdate.setLink(apiLink);
    ApiSampleWithFullSubSamples update =
        new ApiSampleWithFullSubSamples("sample with out-of-set relation");
    update.setId(created.getId());
    update.setFields(List.of(linkUpdate));

    // an out-of-whitelist relation type is rejected as a 422 ApiRuntimeException carrying the
    // relationTypeNotPermitted key (not a raw IllegalArgumentException, which would surface as 500)
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiManager.updateApiSample(update, anyUser));
    assertEquals("errors.inventory.field.link.relationTypeNotPermitted", ex.getErrorCode());
  }

  @Test
  public void mandatoryLinkFieldFilledViaApiPutDoesNotTripMandatoryContentCheck() throws Exception {
    // a sample created from a template with a mandatory link field starts with the
    // link unfilled (the same state "update all samples" leaves pre-existing samples in)
    ApiSampleTemplate savedTemplate = createTemplateWithLinkField(true);
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample with mandatory link field");
    apiSample.setTemplateId(savedTemplate.getId());
    ApiSampleWithFullSubSamples created = sampleApiManager.createNewApiSample(apiSample, anyUser);
    ApiSample fetched = sampleApiManager.getApiSampleById(created.getId(), anyUser);
    Long linkFieldId = findLinkField(fetched.getFields()).getId();

    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);

    // the UI ships the field with empty content alongside the filled link payload; the
    // content-apply path must leave link fields alone instead of failing the mandatory
    // content check with "[] is invalid for field type Link" (RSDEV-1131 bug)
    String updateJson =
        "{\"fields\":[{"
            + "\"id\":"
            + linkFieldId
            + ","
            + "\"type\":\"link\","
            + "\"content\":\"\","
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + target.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + created.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    ApiSampleWithFullSubSamples updated =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    ApiInventoryEntityField updatedField = findLinkField(updated.getFields());
    assertNotNull(updatedField.getLink(), "the mandatory link should now be filled");
    assertEquals(target.getGlobalId(), updatedField.getLink().getTargetGlobalId());
  }

  private ApiSampleTemplate createTemplateWithLinkField() throws Exception {
    return createTemplateWithLinkField(false);
  }

  private ApiSampleTemplate createTemplateWithLinkField(boolean mandatory) throws Exception {
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("template with link field");
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    templatePost.setSampleSource(SampleSource.LAB_CREATED);
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("Related items");
    linkField.setType(ApiFieldType.LINK);
    linkField.setMandatory(mandatory);
    linkField.setAllowedRelationTypes(List.of("References", "IsDerivedFrom"));
    templatePost.setFields(List.of(linkField));

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            // the sampleTemplates POST returns 201 Created (see SampleTemplatesApiControllerMVCIT)
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }

  private ApiInventoryEntityField findLinkField(List<ApiInventoryEntityField> fields) {
    return fields.stream()
        .filter(f -> ApiFieldType.LINK.equals(f.getType()))
        .findFirst()
        .orElse(null);
  }
}

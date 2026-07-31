package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for a Sample template's <b>default link</b> (RSDEV-1246): a template Link
 * field may carry a complete relation-type-and-target pair, stored in the same {@code link_id} an
 * item's link uses, which is stamped onto every item created from that template as that item's own
 * editable link.
 *
 * <p>Authored as part of the feature; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class SampleTemplateDefaultLinkMVCIT extends API_MVC_InventoryTestBase {

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
  public void defaultLinkIsStampedOntoItemsAsTheirOwnRow() throws Exception {
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiSampleWithFullSubSamples otherTarget = createBasicSampleForUser(anyUser);
    ApiSampleTemplate savedTemplate = createTemplateWithDefaultLink(target.getGlobalId());

    ApiInventoryEntityField templateLinkField = findLinkField(savedTemplate.getFields());
    assertNotNull(templateLinkField.getLink(), "the template should hold its default link");
    assertEquals(target.getGlobalId(), templateLinkField.getLink().getTargetGlobalId());
    assertEquals("References", templateLinkField.getLink().getRelationType());

    ApiSample item = createSampleFromTemplate(savedTemplate, "item with a stamped default");
    ApiInventoryEntityField itemLinkField = findLinkField(item.getFields());
    assertNotNull(itemLinkField.getLink(), "the item should arrive with the default already set");
    assertEquals(target.getGlobalId(), itemLinkField.getLink().getTargetGlobalId());
    assertEquals("References", itemLinkField.getLink().getRelationType());

    // the item owns its stamped copy: retargeting the item must not reach back into the template.
    // This is what proves shallowCopy() produced a distinct row rather than sharing the template's.
    String itemUpdateJson =
        "{\"fields\":[{"
            + "\"id\":"
            + itemLinkField.getId()
            + ","
            + "\"type\":\"link\","
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + otherTarget.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/samples/" + item.getId(), anyUser, itemUpdateJson))
        .andExpect(status().isOk());

    ApiSampleTemplate reloadedTemplate =
        sampleApiManager.getApiSampleTemplateById(savedTemplate.getId(), anyUser);
    assertEquals(
        target.getGlobalId(),
        findLinkField(reloadedTemplate.getFields()).getLink().getTargetGlobalId(),
        "retargeting the item must leave the template's default alone");
  }

  @Test
  public void defaultLinkWithADeletedTargetIsStillStamped() throws Exception {
    // ADR-0006: the default is stamped verbatim. A target deleted after the template was authored
    // must not stop items being created, and must not silently drop the link.
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiSampleTemplate savedTemplate = createTemplateWithDefaultLink(target.getGlobalId());

    sampleApiManager.markSampleAsDeleted(target.getId(), false, anyUser);

    ApiSample item = createSampleFromTemplate(savedTemplate, "item created after target deletion");
    ApiInventoryEntityField itemLinkField = findLinkField(item.getFields());
    assertNotNull(itemLinkField.getLink(), "a deleted target is still stamped verbatim");
    assertEquals(target.getGlobalId(), itemLinkField.getLink().getTargetGlobalId());
  }

  @Test
  public void editingTheTemplatesDefaultLeavesExistingItemsUnchanged() throws Exception {
    ApiSampleWithFullSubSamples firstTarget = createBasicSampleForUser(anyUser);
    ApiSampleWithFullSubSamples secondTarget = createBasicSampleForUser(anyUser);
    ApiSampleTemplate savedTemplate = createTemplateWithDefaultLink(firstTarget.getGlobalId());
    Long templateLinkFieldId = findLinkField(savedTemplate.getFields()).getId();

    ApiSample item = createSampleFromTemplate(savedTemplate, "item stamped before the edit");

    // retarget the template's default (bumps the template version)
    String updateJson =
        "{\"fields\":[{"
            + "\"id\":"
            + templateLinkFieldId
            + ","
            + "\"type\":\"link\","
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + secondTarget.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/sampleTemplates/" + savedTemplate.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSampleTemplate updatedTemplate = getFromJsonResponseBody(result, ApiSampleTemplate.class);
    assertEquals(
        secondTarget.getGlobalId(),
        findLinkField(updatedTemplate.getFields()).getLink().getTargetGlobalId(),
        "the template's default should be retargeted");

    // the already-created item keeps the target it was stamped with: defaults are never
    // retro-applied, and updateToLatestTemplateVersion clears newly-cloned fields
    ApiSample reloadedItem = sampleApiManager.getApiSampleById(item.getId(), anyUser);
    assertEquals(
        firstTarget.getGlobalId(),
        findLinkField(reloadedItem.getFields()).getLink().getTargetGlobalId());

    sampleApiManager.updateSampleToLatestTemplateVersion(item.getId(), anyUser);
    ApiSample syncedItem = sampleApiManager.getApiSampleById(item.getId(), anyUser);
    assertEquals(
        firstTarget.getGlobalId(),
        findLinkField(syncedItem.getFields()).getLink().getTargetGlobalId(),
        "syncing to a newer template version must not re-stamp the item");
  }

  @Test
  public void templateHoldingADefaultAppearsInItsTargetsReferencingItems() throws Exception {
    // ADR-0006 decision 3: templates are not filtered out of referencingItems. Seeing the template
    // listed is wanted, because it shows new items will keep being created pointing at that record.
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiSampleTemplate savedTemplate = createTemplateWithDefaultLink(target.getGlobalId());

    MvcResult refs =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/referencingItems/" + target.getGlobalId(), anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryReferencingItems body =
        getFromJsonResponseBody(refs, ApiInventoryReferencingItems.class);
    assertTrue(
        body.getReferencingItems().stream()
            .anyMatch(r -> savedTemplate.getGlobalId().equals(r.getSourceGlobalId())),
        "the template holding the default link should be listed as a referencing item");
  }

  @Test
  public void narrowingTheWhitelistPastTheDefaultIsRejected() throws Exception {
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiSampleTemplate savedTemplate = createTemplateWithDefaultLink(target.getGlobalId());
    Long templateLinkFieldId = findLinkField(savedTemplate.getFields()).getId();

    // the default uses "References"; removing it from the whitelist would orphan the default, so
    // the edit must fail rather than silently drop it
    String updateJson =
        "{\"fields\":[{"
            + "\"id\":"
            + templateLinkFieldId
            + ","
            + "\"type\":\"link\","
            + "\"allowedRelationTypes\":[\"IsCitedBy\"],"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + target.getGlobalId()
            + "\",\"versionPin\":null}"
            + "}]}";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/sampleTemplates/" + savedTemplate.getId(), anyUser, updateJson))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  public void aTemplateWithNoDefaultStillCreatesItemsWithAnEmptyLinkField() throws Exception {
    // the default is optional: a whitelist-only template behaves exactly as it did before
    // RSDEV-1246
    ApiSampleTemplate savedTemplate = createTemplateWithDefaultLink(null);
    assertNull(findLinkField(savedTemplate.getFields()).getLink());

    ApiSample item = createSampleFromTemplate(savedTemplate, "item with no default");
    ApiInventoryEntityField itemLinkField = findLinkField(item.getFields());
    assertNotNull(itemLinkField, "the item should still inherit the link field itself");
    assertNull(itemLinkField.getLink(), "with no default the item's link starts unset");
  }

  private ApiSample createSampleFromTemplate(ApiSampleTemplate template, String name) {
    ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples(name);
    apiSample.setTemplateId(template.getId());
    ApiSampleWithFullSubSamples created = sampleApiManager.createNewApiSample(apiSample, anyUser);
    return sampleApiManager.getApiSampleById(created.getId(), anyUser);
  }

  /** Creates a template with one Link field; {@code targetGlobalId} null means "no default". */
  private ApiSampleTemplate createTemplateWithDefaultLink(String targetGlobalId) throws Exception {
    // hand-written JSON rather than the typed DTO, so the "link" key can be omitted entirely
    // (the DTO cannot express absent-vs-null, which is the distinction under test elsewhere)
    String linkJson =
        targetGlobalId == null
            ? ""
            : ",\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
                + targetGlobalId
                + "\",\"versionPin\":null}";
    String templateJson =
        "{\"name\":\"template with a default link\","
            + "\"defaultUnitId\":"
            + RSUnitDef.GRAM.getId()
            + ","
            + "\"sampleSource\":\"LAB_CREATED\","
            + "\"fields\":[{"
            + "\"name\":\"Related items\","
            + "\"type\":\"link\","
            + "\"allowedRelationTypes\":[\"References\",\"IsDerivedFrom\"]"
            + linkJson
            + "}]}";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templateJson))
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

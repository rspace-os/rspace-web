package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.InventoryEntityFieldDao;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for an instrument template's <b>default link</b> (RSDEV-1246) and for the
 * link-row reconciliation the instrument manager was missing (RSDEV-1270).
 *
 * <p>The RSDEV-1270 cases fail on {@code main} at the {@code deleted=true} assertion. The symptom
 * is invisible in the UI, because both referencing-items queries filter on the field's {@code
 * deleted} flag, so the assertion has to reach the {@link InventoryLink} row directly.
 *
 * <p>Authored as part of the feature; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class InstrumentTemplateDefaultLinkMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired InstrumentEntityApiManager instrumentApiManager;
  private @Autowired InventoryLinkDao inventoryLinkDao;
  private @Autowired InventoryEntityFieldDao inventoryEntityFieldDao;

  private User anyUser;
  private String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void defaultLinkIsStampedOntoInstrumentsCreatedFromTheTemplate() throws Exception {
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiInstrumentTemplate template = createTemplateWithDefaultLink(target.getGlobalId());

    ApiInventoryEntityField templateLinkField = findLinkField(template.getFields());
    assertNotNull(templateLinkField.getLink(), "the template should hold its default link");
    assertEquals(target.getGlobalId(), templateLinkField.getLink().getTargetGlobalId());

    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instrument with a stamped default");
    apiInstrument.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiManager.createNewApiInstrument(apiInstrument, anyUser);

    ApiInventoryEntityField instrumentLinkField = findLinkField(created.getFields());
    assertNotNull(instrumentLinkField.getLink(), "the instrument should arrive with the default");
    assertEquals(target.getGlobalId(), instrumentLinkField.getLink().getTargetGlobalId());
    assertEquals("References", instrumentLinkField.getLink().getRelationType());
  }

  @Test
  public void deletingATemplateLinkFieldSoftDeletesItsDefaultLinkRow() throws Exception {
    // RSDEV-1270, template side: deleting the field is a soft delete (an UPDATE, not a JPA remove),
    // so cascade/orphanRemoval never fires and the row has to be soft-deleted at the service layer.
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiInstrumentTemplate template = createTemplateWithDefaultLink(target.getGlobalId());
    Long linkFieldId = findLinkField(template.getFields()).getId();
    Long linkRowId = linkRowIdOfField(linkFieldId);

    String deleteJson = "{\"fields\":[{\"id\":" + linkFieldId + ",\"deleteFieldRequest\":true}]}";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instrumentTemplates/" + template.getId(), anyUser, deleteJson))
        .andExpect(status().isOk());

    assertTrue(
        linkRowIsDeleted(linkRowId), "the default link row must be soft-deleted with its field");
  }

  @Test
  public void propagatingATemplateFieldDeletionSoftDeletesTheInstrumentsLinkRow() throws Exception {
    // RSDEV-1270, propagation side: this is the case already broken on main. A concrete
    // instrument's
    // link field does hold a link, and updateInstrumentToLatestTemplateVersion marked the field
    // deleted without touching the row.
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiInstrumentTemplate template = createTemplateWithDefaultLink(target.getGlobalId());
    Long templateLinkFieldId = findLinkField(template.getFields()).getId();

    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setName("instrument whose link field is about to go");
    apiInstrument.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiManager.createNewApiInstrument(apiInstrument, anyUser);
    Long instrumentLinkFieldId = findLinkField(created.getFields()).getId();
    Long instrumentLinkRowId = linkRowIdOfField(instrumentLinkFieldId);

    // delete the template field with "update all", bumping the template version
    String deleteJson =
        "{\"fields\":[{\"id\":"
            + templateLinkFieldId
            + ",\"deleteFieldRequest\":true,\"deleteFieldOnSampleUpdate\":true}]}";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instrumentTemplates/" + template.getId(), anyUser, deleteJson))
        .andExpect(status().isOk());

    instrumentApiManager.updateInstrumentToLatestTemplateVersion(created.getId(), anyUser);

    ApiInstrument synced = instrumentApiManager.getApiInstrumentById(created.getId(), anyUser);
    assertFalse(
        synced.getFields().stream().anyMatch(f -> ApiFieldType.LINK.equals(f.getType())),
        "the propagated delete should remove the link field from the instrument");

    assertTrue(
        linkRowIsDeleted(instrumentLinkRowId),
        "the instrument's orphaned link row must be soft-deleted by the sync");
  }

  @Test
  public void narrowingTheWhitelistPastTheDefaultIsRejected() throws Exception {
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    ApiInstrumentTemplate template = createTemplateWithDefaultLink(target.getGlobalId());
    Long linkFieldId = findLinkField(template.getFields()).getId();

    String updateJson =
        "{\"fields\":[{"
            + "\"id\":"
            + linkFieldId
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
                apiKey, "/instrumentTemplates/" + template.getId(), anyUser, updateJson))
        .andExpect(status().isUnprocessableEntity());
  }

  /**
   * The InventoryLink row id behind a link field, read straight from the DAO. This base runs real
   * transactions per request rather than wrapping the test method in one, so a direct DAO call
   * needs its own transaction or Hibernate has no session to bind to.
   */
  private Long linkRowIdOfField(Long fieldId) throws Exception {
    return doInTransaction(
        () -> {
          InventoryLinkField field = (InventoryLinkField) inventoryEntityFieldDao.get(fieldId);
          assertNotNull(field.getLink(), "the field should hold a link before it is deleted");
          return field.getLink().getId();
        });
  }

  /** Same reason as {@link #linkRowIdOfField}: the row read needs its own transaction. */
  private boolean linkRowIsDeleted(Long linkRowId) throws Exception {
    return doInTransaction(() -> inventoryLinkDao.get(linkRowId).isDeleted());
  }

  private ApiInstrumentTemplate createTemplateWithDefaultLink(String targetGlobalId)
      throws Exception {
    String templateJson =
        "{\"name\":\"instrument template with a default link\","
            + "\"fields\":[{"
            + "\"name\":\"Related items\","
            + "\"type\":\"link\","
            + "\"allowedRelationTypes\":[\"References\",\"IsDerivedFrom\"],"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + targetGlobalId
            + "\",\"versionPin\":null}"
            + "}]}";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instrumentTemplates", anyUser, templateJson))
            .andExpect(status().isCreated())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
  }

  private ApiInventoryEntityField findLinkField(List<ApiInventoryEntityField> fields) {
    return fields.stream()
        .filter(f -> ApiFieldType.LINK.equals(f.getType()))
        .findFirst()
        .orElse(null);
  }
}

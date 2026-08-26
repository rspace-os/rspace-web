package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryTextField;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A link field's value lives in its InventoryLink (applied via the service-layer
 * InventoryLinkManager), not in the {@code data} column, so the DTO content-apply path must leave
 * link fields alone. Before this rule, updating a sample whose mandatory link field was added by a
 * template ("update all samples") failed with "[] is invalid for field type Link: Field is
 * mandatory, but no content is provided" because the client's empty content string was pushed into
 * setFieldData.
 */
class ApiInventoryEntityFieldLinkContentTest {

  @Test
  void contentChangesAreNotAppliedToMandatoryLinkFields() {
    InventoryLinkField dbField = new InventoryLinkField();
    dbField.setName("ALL");
    dbField.setMandatory(true);

    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    apiField.setContent("");

    assertDoesNotThrow(() -> apiField.applyChangesToDatabaseField(dbField, null));
    assertFalse(apiField.applyChangesToDatabaseField(dbField, null));
  }

  @Test
  void contentChangesAreNotAppliedToOptionalLinkFields() {
    InventoryLinkField dbField = new InventoryLinkField();
    dbField.setName("related");

    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    apiField.setContent("some stray content");

    assertFalse(apiField.applyChangesToDatabaseField(dbField, null));
    assertEquals(null, dbField.getData());
  }

  @Test
  void editedAllowedRelationTypesWhitelistIsAppliedToTemplateLinkField() {
    // the whitelist is set on create but was dropped on edit (RSDEV-1200): the template
    // update path applied name/columnIndex/definition/mandatory/content but not the
    // link field's allowed-relation-types whitelist, so it stayed at the initial value.
    InventoryLinkField dbField = new InventoryLinkField();
    dbField.setName("related");
    dbField.setAllowedRelationTypes("References");

    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    apiField.setType(ApiFieldType.LINK);
    apiField.setAllowedRelationTypes(List.of("References", "IsDerivedFrom"));

    assertTrue(apiField.applyChangesToDatabaseTemplateField(dbField, null));
    assertEquals("References|IsDerivedFrom", dbField.getAllowedRelationTypes());
  }

  @Test
  void unchangedAllowedRelationTypesWhitelistReportsNoChange() {
    InventoryLinkField dbField = new InventoryLinkField();
    dbField.setAllowedRelationTypes("References|IsDerivedFrom");

    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    apiField.setType(ApiFieldType.LINK);
    apiField.setAllowedRelationTypes(List.of("References", "IsDerivedFrom"));

    assertFalse(apiField.applyChangesToDatabaseTemplateField(dbField, null));
    assertEquals("References|IsDerivedFrom", dbField.getAllowedRelationTypes());
  }

  @Test
  void clearingAllowedRelationTypesWhitelistPersistsAsAll() {
    // an empty list means "all relation types permitted"; it must clear a previously set
    // whitelist (stored null) rather than be ignored.
    InventoryLinkField dbField = new InventoryLinkField();
    dbField.setAllowedRelationTypes("References");

    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    apiField.setType(ApiFieldType.LINK);
    apiField.setAllowedRelationTypes(List.of());

    assertTrue(apiField.applyChangesToDatabaseTemplateField(dbField, null));
    assertEquals(null, dbField.getAllowedRelationTypes());
  }

  @Test
  void contentChangesStillApplyToNonLinkFields() {
    InventoryTextField dbField = new InventoryTextField("notes");

    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    apiField.setContent("updated text");

    assertTrue(apiField.applyChangesToDatabaseField(dbField, null));
    assertEquals("updated text", dbField.getData());
  }

  /*
   * Telling an absent "link" key apart from an explicit "link": null is what stops a partial
   * template PUT destroying that field's default link (RSDEV-1246). The whole mechanism rests on
   * Jackson routing deserialization through the hand-written setLink, so these read real JSON
   * rather than calling the setter directly: a change to Jackson visibility, an added @JsonSetter,
   * or Lombok regaining the setter would silently reinstate the bug with every other test green.
   */
  private static ApiInventoryEntityField parse(String json) throws Exception {
    return new ObjectMapper().readValue(json, ApiInventoryEntityField.class);
  }

  @Test
  void aPayloadWithNoLinkKeyLeavesLinkUnprovided() throws Exception {
    ApiInventoryEntityField field = parse("{\"id\":1,\"allowedRelationTypes\":[\"IsCitedBy\"]}");

    assertFalse(field.isLinkProvided());
    assertNull(field.getLink());
  }

  @Test
  void anExplicitNullLinkCountsAsProvided() throws Exception {
    ApiInventoryEntityField field = parse("{\"id\":1,\"link\":null}");

    assertTrue(field.isLinkProvided(), "an explicit null is the client asking to clear the link");
    assertNull(field.getLink());
  }

  @Test
  void aPopulatedLinkCountsAsProvided() throws Exception {
    ApiInventoryEntityField field =
        parse("{\"id\":1,\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\"SA2\"}}");

    assertTrue(field.isLinkProvided());
    assertEquals("SA2", field.getLink().getTargetGlobalId());
  }

  @Test
  void aClientCannotForgeTheProvidedFlag() throws Exception {
    // @JsonIgnore removes the whole logical property, so linkProvided is not client-settable
    ApiInventoryEntityField field = parse("{\"id\":1,\"linkProvided\":true}");

    assertFalse(field.isLinkProvided());
  }
}

package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

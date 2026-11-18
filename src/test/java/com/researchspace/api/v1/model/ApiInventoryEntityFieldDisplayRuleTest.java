package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryStringField;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The {@code fieldKey} and {@code displayRule} contracts of template fields in the API. */
class ApiInventoryEntityFieldDisplayRuleTest {

  private static final String KEY = "0b7e2c5d-3a41-4f6e-8c19-5d2a7e4b1f30";
  private static final String RULE_JSON =
      "{\"sourceFieldKey\":\"6f1c2e0a-8d5b-4c51-9a3e-2b7f1d0c4e9a\",\"operator\":\"IS_ANY_OF\","
          + "\"values\":[\"Yes\"]}";
  private static final FieldDisplayRule RULE = FieldDisplayRule.fromJson(RULE_JSON);

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void ruleIsReadAndWrittenAsStored() throws Exception {
    ApiInventoryEntityField field =
        mapper.readValue("{\"displayRule\":" + RULE_JSON + "}", ApiInventoryEntityField.class);
    assertEquals(RULE, field.getDisplayRule());

    String json = mapper.writeValueAsString(field);
    assertTrue(json.contains("\"displayRule\":" + RULE_JSON), json);
  }

  @Test
  void absentRuleAndExplicitNullAreTold() throws Exception {
    ApiInventoryEntityField absent = mapper.readValue("{}", ApiInventoryEntityField.class);
    assertFalse(absent.isDisplayRuleProvided());

    ApiInventoryEntityField cleared =
        mapper.readValue("{\"displayRule\":null}", ApiInventoryEntityField.class);
    assertTrue(cleared.isDisplayRuleProvided());
    assertNull(cleared.getDisplayRule());
  }

  @Test
  void malformedRulesAreRejected() {
    for (String rule :
        List.of(
            "{\"sourceFieldKey\":\"k\",\"operator\":\"IS_ANY_OF\"}",
            "{\"sourceFieldKey\":\"k\",\"operator\":\"is_any_of\",\"values\":[\"a\"]}",
            "{\"sourceFieldKey\":\"k\",\"operator\":\"HAS_VALUE\",\"values\":[1]}",
            "{\"sourceFieldKey\":\"k\",\"operator\":\"HAS_VALUE\",\"effect\":\"HIDE\"}",
            "\"HAS_VALUE\"")) {
      assertThrows(
          JsonProcessingException.class,
          () -> mapper.readValue("{\"displayRule\":" + rule + "}", ApiInventoryEntityField.class),
          rule);
    }
  }

  @Test
  void outgoingFieldCarriesKeyAndRuleWithoutMarkingTheRuleProvided() {
    InventoryStringField dbField = new InventoryStringField("permit");
    dbField.setFieldKey(KEY);
    dbField.setDisplayRule(RULE);

    ApiInventoryEntityField api = new ApiInventoryEntityField(dbField);

    assertEquals(KEY, api.getFieldKey());
    assertEquals(RULE, api.getDisplayRule());
    assertFalse(api.isDisplayRuleProvided());
  }

  @Test
  void templateUpdateKeepsAnAbsentRuleAndClearsAnExplicitNull() {
    InventoryStringField dbField = new InventoryStringField("permit");
    dbField.setFieldKey(KEY);
    dbField.setDisplayRule(RULE);

    ApiInventoryEntityField rename = new ApiInventoryEntityField();
    rename.setName("renamed");
    rename.applyChangesToDatabaseTemplateField(dbField, null);
    assertEquals(RULE, dbField.getDisplayRule());

    ApiInventoryEntityField clear = new ApiInventoryEntityField();
    clear.setDisplayRule(null);
    assertTrue(clear.applyChangesToDatabaseTemplateField(dbField, null));
    assertNull(dbField.getDisplayRule());
  }

  @Test
  void templateUpdateRejectsADifferentKey() {
    InventoryStringField dbField = new InventoryStringField("permit");
    dbField.setFieldKey(KEY);

    ApiInventoryEntityField sameKey = new ApiInventoryEntityField();
    sameKey.setFieldKey(KEY);
    assertFalse(sameKey.applyChangesToDatabaseTemplateField(dbField, null));

    ApiInventoryEntityField otherKey = new ApiInventoryEntityField();
    otherKey.setFieldKey("9a3c1e7f-2b4d-4e85-a6c0-1f8d3b5e7a92");
    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> otherKey.applyChangesToDatabaseTemplateField(dbField, null));
    assertEquals("errors.inventory.field.keyChanged", e.getMessage());
    assertEquals(KEY, dbField.getFieldKey());
  }

  @Test
  void factoryGivesNewFieldsTheClientKeyOrAFreshOneAndTheRule() {
    ApiFieldToModelFieldFactory factory = new ApiFieldToModelFieldFactory();
    ApiInventoryEntityField withKey = new ApiInventoryEntityField();
    withKey.setType(ApiFieldType.STRING);
    withKey.setName("permit");
    withKey.setFieldKey(KEY);
    withKey.setDisplayRule(RULE);

    InventoryEntityField created = factory.apiInventoryFieldToModelField(withKey);
    assertEquals(KEY, created.getFieldKey());
    assertEquals(RULE, created.getDisplayRule());

    withKey.setFieldKey(null);
    String generated = factory.apiInventoryFieldToModelField(withKey).getFieldKey();
    assertTrue(
        Pattern.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", generated));
  }
}

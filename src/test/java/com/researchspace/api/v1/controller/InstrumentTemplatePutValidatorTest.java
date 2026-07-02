package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class InstrumentTemplatePutValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private InstrumentTemplatePutValidator putValidator;

  @Before
  public void setup() {
    validator = putValidator;
  }

  @Test
  public void supportsApiInstrumentTemplate() {
    assertTrue(putValidator.supports(ApiInstrumentTemplate.class));
  }

  @Test
  public void emptyPutHasNoErrors() {
    // PUT is intentionally lenient: a body with no fields is valid (no changes requested).
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void rejectsTooLongDescription() {
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();
    put.setDescription("d".repeat(2001));

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    assertEquals(1, e.getErrorCount());
    assertFieldNameIs(e, "description");
    assertMaxLengthMsg(e);
  }

  @Test
  public void rejectsNewFieldWithEmptyName() {
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();
    ApiInventoryEntityField bad = new ApiInventoryEntityField();
    bad.setNewFieldRequest(true);
    bad.setType(ApiFieldType.TEXT);
    put.setFields(java.util.List.of(bad));

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    assertTrue(e.getErrorCount() >= 1);
    assertEquals("errors.inventory.template.empty.field.name", e.getFieldError().getCode());
  }

  @Test
  public void acceptsUpdateToExistingFieldWithoutTypeOrName() {
    // PUT to existing field is lenient — id present, no other changes.
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();
    ApiInventoryEntityField existing = new ApiInventoryEntityField();
    existing.setId(123L);
    put.setFields(java.util.List.of(existing));

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void rejectsLinkFieldWithInvalidRelationType() {
    // RSDEV-1200: instrument templates support link fields, so an invalid allowed-relation-type
    // must be rejected here just as it is for sample templates.
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();
    ApiInventoryEntityField link = new ApiInventoryEntityField();
    link.setId(123L);
    link.setType(ApiFieldType.LINK);
    link.setAllowedRelationTypes(java.util.List.of("NotADataCiteRelationType"));
    put.setFields(java.util.List.of(link));

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    // the field error is registered under a nested path (e.g. fields[0].allowedRelationTypes),
    // so match by error code rather than by top-level field name.
    assertTrue(
        e.getFieldErrors().stream()
            .anyMatch(fe -> "errors.inventory.template.invalid.relation.type".equals(fe.getCode())),
        "expected an invalid-relation-type error, got: " + e.getAllErrors());
  }

  @Test
  public void acceptsLinkFieldWithValidRelationTypes() {
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();
    ApiInventoryEntityField link = new ApiInventoryEntityField();
    link.setId(123L);
    link.setType(ApiFieldType.LINK);
    link.setAllowedRelationTypes(java.util.List.of("References", "IsCitedBy"));
    put.setFields(java.util.List.of(link));

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    assertTrue(
        e.getFieldErrors().stream()
            .noneMatch(
                fe -> "errors.inventory.template.invalid.relation.type".equals(fe.getCode())),
        "unexpected invalid-relation-type error, got: " + e.getAllErrors());
  }

  @Test
  public void rejectsInvalidRelationTypeOnExistingFieldPutWithoutType() {
    // RSDEV-1200: an existing-field PUT may omit `type`, but the whitelist is still persisted by
    // the
    // DB field type, so it must be validated whenever present - not only when the DTO says
    // type==LINK. Otherwise an invalid whitelist bypasses validation and is stored anyway.
    ApiInstrumentTemplate put = new ApiInstrumentTemplate();
    ApiInventoryEntityField existingLink = new ApiInventoryEntityField();
    existingLink.setId(123L); // existing field, no type provided
    existingLink.setAllowedRelationTypes(java.util.List.of("NotADataCiteRelationType"));
    put.setFields(java.util.List.of(existingLink));

    Errors e = new BeanPropertyBindingResult(put, "templatePut");
    putValidator.validate(put, e);

    assertTrue(
        e.getFieldErrors().stream()
            .anyMatch(fe -> "errors.inventory.template.invalid.relation.type".equals(fe.getCode())),
        "expected an invalid-relation-type error, got: " + e.getAllErrors());
  }
}

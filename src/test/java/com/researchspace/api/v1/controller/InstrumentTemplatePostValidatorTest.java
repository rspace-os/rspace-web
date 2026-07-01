package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.service.inventory.InventoryFieldNameUniquenessValidator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class InstrumentTemplatePostValidatorTest extends InventoryRecordValidationTestBase {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired private InstrumentTemplatePostValidator postValidator;

  @Before
  public void setup() {
    validator = postValidator;
  }

  @Test
  public void supportsApiInstrumentTemplatePost() {
    assertTrue(postValidator.supports(ApiInstrumentTemplatePost.class));
  }

  @Test
  public void validPostHasNoErrors() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("valid template");
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName("F1");
    field.setType(ApiFieldType.TEXT);
    templatePost.getFields().add(field);

    Errors e = new BeanPropertyBindingResult(templatePost, "templatePost");
    postValidator.validate(templatePost, e);

    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void rejectsBlankName() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName(" ");

    Errors e = new BeanPropertyBindingResult(templatePost, "templatePost");
    postValidator.validate(templatePost, e);

    assertEquals(1, e.getErrorCount());
    assertFieldNameIs(e, "name");
    assertEquals("errors.required", e.getFieldError().getCode());
  }

  @Test
  public void rejectsTooLongName() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("a".repeat(256));

    Errors e = new BeanPropertyBindingResult(templatePost, "templatePost");
    postValidator.validate(templatePost, e);

    assertEquals(1, e.getErrorCount());
    assertFieldNameIs(e, "name");
    assertMaxLengthMsg(e);
  }

  @Test
  public void rejectsFieldWithEmptyName() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("template");
    ApiInventoryEntityField bad = new ApiInventoryEntityField();
    bad.setType(ApiFieldType.TEXT);
    templatePost.getFields().add(bad);

    Errors e = new BeanPropertyBindingResult(templatePost, "templatePost");
    postValidator.validate(templatePost, e);

    assertTrue(e.getErrorCount() >= 1);
    assertEquals("fields[0].name", e.getFieldError().getField());
    assertEquals("errors.inventory.template.empty.field.name", e.getFieldError().getCode());
  }

  @Test
  public void rejectsFieldWithMissingType() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("template");
    ApiInventoryEntityField bad = new ApiInventoryEntityField();
    bad.setName("noType");
    templatePost.getFields().add(bad);

    Errors e = new BeanPropertyBindingResult(templatePost, "templatePost");
    postValidator.validate(templatePost, e);

    assertTrue(e.getErrorCount() >= 1);
    assertTrue(
        e.getAllErrors().stream()
            .anyMatch(err -> "errors.inventory.template.empty.field.type".equals(err.getCode())));
  }

  //@Test
  public void rejectsDuplicateExtraFieldNames() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("template");
    ApiExtraField ef1 = new ApiExtraField();
    ef1.setName("Notes");
    ApiExtraField ef2 = new ApiExtraField();
    ef2.setName("notes"); // case-insensitive duplicate
    templatePost.getExtraFields().add(ef1);
    templatePost.getExtraFields().add(ef2);

    Errors e = new BeanPropertyBindingResult(templatePost, "templatePost");
    postValidator.validate(templatePost, e);

    assertTrue(e.getErrorCount() >= 1);
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        e.getFieldError().getCode());
  }
}

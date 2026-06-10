package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryLink;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

class InventoryLinkValidatorTest {

  private final InventoryLinkValidator validator = new InventoryLinkValidator();

  @Test
  void rejectsUnknownRelationType() {
    ApiInventoryLink link = buildLink("MadeUpRelation", "SA1");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA1", errors);

    assertTrue(errors.hasFieldErrors("relationType"));
    assertEquals(
        "errors.inventory.field.link.relationTypeInvalid",
        errors.getFieldError("relationType").getCode());
  }

  @Test
  void rejectsTargetWithUnsupportedPrefix() {
    ApiInventoryLink link = buildLink("References", "GF1");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
    assertEquals(
        "errors.inventory.field.link.targetKindUnsupported",
        errors.getFieldError("targetGlobalId").getCode());
  }

  @Test
  void rejectsMalformedTargetGlobalId() {
    ApiInventoryLink link = buildLink("References", "not-a-global-id");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
  }

  @Test
  void rejectsSelfLink() {
    ApiInventoryLink link = buildLink("References", "SA42");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
    assertEquals(
        "errors.inventory.field.link.selfLinkForbidden",
        errors.getFieldError("targetGlobalId").getCode());
  }

  @Test
  void rejectsSelfLinkIgnoringVersionSuffix() {
    ApiInventoryLink link = buildLink("References", "SA42v2");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
    assertEquals(
        "errors.inventory.field.link.selfLinkForbidden",
        errors.getFieldError("targetGlobalId").getCode());
  }

  @Test
  void acceptsValidInventoryLink() {
    ApiInventoryLink link = buildLink("IsCalibratedBy", "SA99");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  void acceptsAllFourInventoryPrefixes() {
    for (String prefix : new String[] {"SA", "SS", "IC", "IN"}) {
      ApiInventoryLink link = buildLink("References", prefix + "1");
      Errors errors = errorsFor(link);
      validator.validate(link, "SA42", errors);
      assertFalse(errors.hasErrors(), "expected " + prefix + " prefix to be accepted");
    }
  }

  @Test
  void acceptsSampleTemplateTarget() {
    ApiInventoryLink link = buildLink("References", "IT12");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertFalse(errors.hasErrors(), "expected IT (sample template) prefix to be accepted");
  }

  @Test
  void acceptsElnTargetPrefixes() {
    for (String prefix : new String[] {"SD", "NB", "GL"}) {
      ApiInventoryLink link = buildLink("References", prefix + "1");
      Errors errors = errorsFor(link);
      validator.validate(link, "SA42", errors);
      assertFalse(errors.hasErrors(), "expected " + prefix + " (ELN) prefix to be accepted");
    }
  }

  @Test
  void rejectsNullRelationType() {
    ApiInventoryLink link = buildLink(null, "SA1");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertTrue(errors.hasFieldErrors("relationType"));
  }

  private ApiInventoryLink buildLink(String relationType, String targetGlobalId) {
    ApiInventoryLink l = new ApiInventoryLink();
    l.setRelationType(relationType);
    l.setTargetGlobalId(targetGlobalId);
    return l;
  }

  private Errors errorsFor(ApiInventoryLink link) {
    return new BeanPropertyBindingResult(link, "link");
  }
}

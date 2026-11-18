package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkFilestoreTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

class InventoryLinkValidatorTest {

  private final InventoryLinkValidator validator = new InventoryLinkValidator();

  @Test
  void blankTargetIsRejectedAsNotFound() {
    ApiInventoryLink link = buildLink("References", "   ");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA1", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
    assertEquals(
        "errors.inventory.field.linkTargetNotFound",
        errors.getFieldError("targetGlobalId").getCode());
  }

  @Test
  void rejectsUnknownRelationType() {
    ApiInventoryLink link = buildLink("MadeUpRelation", "SA1");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA1", errors);

    assertTrue(errors.hasFieldErrors("relationType"));
    assertEquals(
        "errors.inventory.field.linkRelationTypeInvalid",
        errors.getFieldError("relationType").getCode());
  }

  @Test
  void rejectsTargetWithUnsupportedPrefix() {
    ApiInventoryLink link = buildLink("References", "GF1");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
    assertEquals(
        "errors.inventory.field.linkTargetKindUnsupported",
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
  void acceptsInstrumentTarget() {
    ApiInventoryLink link = buildLink("References", "IN7");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  void acceptsInstrumentTemplateTarget() {
    ApiInventoryLink link = buildLink("References", "NT7");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  void rejectsInstrumentSelfLinkIgnoringVersionSuffix() {
    ApiInventoryLink link = buildLink("References", "IN42v2");
    Errors errors = errorsFor(link);
    validator.validate(link, "IN42", errors);

    assertTrue(errors.hasFieldErrors("targetGlobalId"));
    assertEquals(
        "errors.inventory.field.link.selfLinkForbidden",
        errors.getFieldError("targetGlobalId").getCode());
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

  private ApiInventoryLink filestoreLink(Long filestoreId, String remotePath) {
    ApiInventoryLink l = new ApiInventoryLink();
    l.setRelationType("IsSourceOf");
    l.setFilestoreTarget(new ApiInventoryLinkFilestoreTarget(filestoreId, remotePath));
    return l;
  }

  @Test
  void acceptsAFilestoreItemTarget() {
    ApiInventoryLink link = filestoreLink(12L, "/XRD/run.dat");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  void rejectsALinkNamingBothAGlobalIdAndAFilestoreItem() {
    ApiInventoryLink link = filestoreLink(12L, "/XRD/run.dat");
    link.setTargetGlobalId("SA1");
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertEquals(
        "errors.inventory.field.linkTargetAmbiguous",
        errors.getFieldError("targetGlobalId").getCode());
  }

  @Test
  void rejectsAVersionPinOnAFilestoreItem() {
    ApiInventoryLink link = filestoreLink(12L, "/XRD/run.dat");
    link.setVersionPin(3L);
    Errors errors = errorsFor(link);
    validator.validate(link, "SA42", errors);

    assertEquals(
        "errors.inventory.field.linkTargetVersionNotFound",
        errors.getFieldError("versionPin").getCode());
  }

  @Test
  void rejectsAFilestoreTargetNamingNoItemAsNotFound() {
    // a blank path, the filestore's own root, or no filestore at all
    for (ApiInventoryLink link :
        List.of(filestoreLink(12L, " "), filestoreLink(12L, "//"), filestoreLink(null, "/XRD"))) {
      Errors errors = errorsFor(link);
      validator.validate(link, "SA42", errors);

      assertEquals(
          "errors.inventory.field.linkTargetNotFound",
          errors.getFieldError("filestoreTarget").getCode());
    }
  }

  @Test
  void rejectsAPathWithADotSegmentAsNotFound() {
    for (String path : List.of("/../../other/secret.txt", "/a/./b.txt", "a/..", "..")) {
      ApiInventoryLink link = filestoreLink(12L, path);
      Errors errors = errorsFor(link);
      validator.validate(link, "SA42", errors);

      assertEquals(
          "errors.inventory.field.linkTargetNotFound",
          errors.getFieldError("filestoreTarget").getCode(),
          path);
    }
  }

  @Test
  void acceptsNamesThatOnlyContainDots() {
    for (String path : List.of("/a/.hidden", "/a/run..dat", "/a/...")) {
      ApiInventoryLink link = filestoreLink(12L, path);
      Errors errors = errorsFor(link);
      validator.validate(link, "SA42", errors);

      assertFalse(errors.hasErrors(), path);
    }
  }
}

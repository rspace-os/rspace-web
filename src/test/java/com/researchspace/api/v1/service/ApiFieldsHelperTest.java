package com.researchspace.api.v1.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLinkField;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

/**
 * Unit tests for {@link ApiFieldsHelper#validateMandatoryFieldsForEntityPost} focused on the
 * structured Link field (RSDEV-1131). A Link field carries its value in the incoming field's {@code
 * link} object, not in its (always-empty) {@code content} string, so the mandatory check must read
 * the incoming link's target rather than the content. Regression guard for the bug where a
 * user-provided link on a mandatory Link field was rejected as "mandatory, but provided value was
 * empty".
 */
public class ApiFieldsHelperTest {

  private final ApiFieldsHelper helper = new ApiFieldsHelper();

  private InventoryLinkField mandatoryLinkTemplateField() {
    InventoryLinkField templateField = new InventoryLinkField();
    templateField.setName("Related items");
    templateField.setMandatory(true);
    return templateField;
  }

  @Test
  public void mandatoryLinkFieldSatisfiedByIncomingLinkTarget() {
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setRelationType("References");
    apiLink.setTargetGlobalId("SD123");
    ApiInventoryEntityField incoming = new ApiInventoryEntityField();
    incoming.setType(ApiFieldType.LINK);
    incoming.setLink(apiLink);

    Errors errors = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        "sample",
        List.of(incoming),
        List.<InventoryEntityField>of(mandatoryLinkTemplateField()),
        errors);

    assertFalse(errors.hasErrors(), "a provided link target must satisfy the mandatory link field");
  }

  @Test
  public void mandatoryLinkFieldRejectedWhenNoLinkProvided() {
    ApiInventoryEntityField incoming = new ApiInventoryEntityField();
    incoming.setType(ApiFieldType.LINK);
    // no link object provided

    Errors errors = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        "sample",
        List.of(incoming),
        List.<InventoryEntityField>of(mandatoryLinkTemplateField()),
        errors);

    assertTrue(errors.hasErrors(), "a mandatory link field with no link must be rejected");
  }
}

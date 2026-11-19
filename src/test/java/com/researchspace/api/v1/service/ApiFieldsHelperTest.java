package com.researchspace.api.v1.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkFilestoreTarget;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryStringField;
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
  public void ignoresDeletedDocumentFormFields() {
    FieldForm deletedField = new TextFieldForm("Old field");
    deletedField.setDeleted(true);

    helper.checkApiFieldsMatchingFormFields(
        List.of(new ApiDocumentField()),
        List.of(deletedField, new TextFieldForm("Current field")),
        null);
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
        List.of(incoming), List.<InventoryEntityField>of(mandatoryLinkTemplateField()), errors);

    assertFalse(errors.hasErrors(), "a provided link target must satisfy the mandatory link field");
  }

  @Test
  public void mandatoryLinkFieldRejectedWhenNoLinkProvided() {
    ApiInventoryEntityField incoming = new ApiInventoryEntityField();
    incoming.setType(ApiFieldType.LINK);
    // no link object provided

    Errors errors = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(incoming), List.<InventoryEntityField>of(mandatoryLinkTemplateField()), errors);

    assertTrue(errors.hasErrors(), "a mandatory link field with no link must be rejected");
    assertEquals("errors.inventory.field.mandatoryFieldEmpty", errors.getFieldError().getCode());
  }

  @Test
  public void mandatoryLinkFieldSatisfiedByIncomingFilestoreTarget() {
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setRelationType("IsSourceOf");
    apiLink.setFilestoreTarget(new ApiInventoryLinkFilestoreTarget(12L, "/XRD/run.dat"));
    ApiInventoryEntityField incoming = new ApiInventoryEntityField();
    incoming.setType(ApiFieldType.LINK);
    incoming.setLink(apiLink);

    Errors errors = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(incoming), List.<InventoryEntityField>of(mandatoryLinkTemplateField()), errors);

    assertFalse(errors.hasErrors());
  }

  private static final String GMO_KEY = "6f1c2e0a-8d5b-4c51-9a3e-2b7f1d0c4e9a";

  /**
   * "Is this a GMO?" radio, defaulting to {@code defaultOption}, and a dependent mandatory field.
   */
  private static List<InventoryEntityField> gmoTemplateFields(String defaultOption) {
    InventoryRadioFieldDef def = new InventoryRadioFieldDef();
    def.setRadioOptionsList(List.of("Yes", "No"));
    InventoryRadioField gmo = new InventoryRadioField(def, "Is this a GMO?");
    gmo.setFieldKey(GMO_KEY);
    if (defaultOption != null) {
      gmo.setSelectedOptions(List.of(defaultOption));
    }
    InventoryStringField permit = new InventoryStringField("GMO permit number");
    permit.setMandatory(true);
    permit.setDisplayRule(
        new FieldDisplayRule(GMO_KEY, ConditionOperator.IS_ANY_OF, List.of("Yes")));
    return List.of(gmo, permit);
  }

  private static ApiInventoryEntityField radioValue(String option) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setSelectedOptions(option == null ? List.of() : List.of(option));
    return field;
  }

  @Test
  public void hiddenMandatoryFieldIsNotRequired() {
    Errors errors = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(radioValue("No"), new ApiInventoryEntityField()), gmoTemplateFields(null), errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  public void shownMandatoryFieldIsRequired() {
    Errors errors = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(radioValue("Yes"), new ApiInventoryEntityField()), gmoTemplateFields(null), errors);

    assertEquals("errors.inventory.field.mandatoryFieldEmpty", errors.getFieldError().getCode());
  }

  @Test
  public void visibilityOfOmittedFieldsFollowsTheTemplateDefaults() {
    Errors shownByDefault = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(), gmoTemplateFields("Yes"), shownByDefault);
    assertTrue(shownByDefault.hasErrors());

    Errors hiddenByDefault = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(), gmoTemplateFields("No"), hiddenByDefault);
    assertFalse(hiddenByDefault.hasErrors());
  }

  @Test
  public void visibilityOfAScalarSourceFollowsTheSubmittedContentOverTheTemplateDefault() {
    String weightKey = "0b7d4c1e-3f2a-4e6b-8c9d-1a2b3c4d5e6f";
    InventoryNumberField weight = new InventoryNumberField("Weight");
    weight.setFieldKey(weightKey);
    weight.setFieldData("4");
    InventoryStringField permit = new InventoryStringField("Heavy goods permit");
    permit.setMandatory(true);
    permit.setDisplayRule(
        new FieldDisplayRule(weightKey, ConditionOperator.GREATER_THAN, List.of("2")));
    List<InventoryEntityField> templateFields = List.of(weight, permit);

    Errors shownByDefault = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(List.of(), templateFields, shownByDefault);
    assertTrue(shownByDefault.hasErrors());

    ApiInventoryEntityField light = new ApiInventoryEntityField();
    light.setContent("1");
    Errors hiddenBySubmittedValue = new MapBindingResult(new HashMap<>(), "sample");
    helper.validateMandatoryFieldsForEntityPost(
        List.of(light, new ApiInventoryEntityField()), templateFields, hiddenBySubmittedValue);
    assertFalse(hiddenBySubmittedValue.hasErrors());
  }
}

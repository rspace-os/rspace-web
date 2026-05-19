package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.controller.InstrumentsApiController.ApiInstrumentFullPost;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryTextField;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

public class InstrumentApiPostFullValidatorTest extends InventoryRecordValidationTestBase {

  @Autowired
  @Qualifier("instrumentApiPostFullValidator")
  private InstrumentApiPostFullValidator instrumentPostFullValidator;

  @Before
  public void setup() {
    validator = instrumentPostFullValidator;
  }

  @Test
  public void validateMandatoryFields() {
    User testUser = createInitAndLoginAnyUser();
    ApiInstrument apiInstrumentPost = new ApiInstrument();
    ApiInstrumentFullPost fullPost = new ApiInstrumentFullPost();
    fullPost.setApiInstrument(apiInstrumentPost);
    fullPost.setUser(testUser);
    fullPost.setTemplate(createInstrumentTemplateWithMandatoryFields());

    Errors errors = new BeanPropertyBindingResult(apiInstrumentPost, "apiInstrument");
    validator.validate(fullPost, errors);
    assertEquals(2, errors.getErrorCount());
    assertFieldNameIs(errors, "fields");
    FieldError firstError = errors.getFieldErrors().get(0);
    FieldError secondError = errors.getFieldErrors().get(1);
    assertEquals("errors.inventory.instrument.mandatory.field.empty", firstError.getCode());
    assertEquals("myText (mandatory - no default value)", getFirstArgument(firstError));
    assertEquals("errors.inventory.instrument.mandatory.field.no.selection", secondError.getCode());
    assertEquals("myRadio (mandatory - no default value)", getFirstArgument(secondError));

    apiInstrumentPost.setFields(
        Stream.generate(ApiInventoryEntityField::new).limit(6).collect(Collectors.toList()));
    errors = new BeanPropertyBindingResult(apiInstrumentPost, "apiInstrument");
    validator.validate(fullPost, errors);
    assertEquals(4, errors.getErrorCount());
    assertFieldNameIs(errors, "fields");
    List<FieldError> fieldErrors = errors.getFieldErrors();
    assertEquals("myText (mandatory - with default value)", getFirstArgument(fieldErrors.get(0)));
    assertEquals("myText (mandatory - no default value)", getFirstArgument(fieldErrors.get(1)));
    assertEquals("myRadio (mandatory - with default value)", getFirstArgument(fieldErrors.get(2)));
    assertEquals("myRadio (mandatory - no default value)", getFirstArgument(fieldErrors.get(3)));

    apiInstrumentPost.getFields().get(0).setContent("test content");
    apiInstrumentPost.getFields().get(1).setContent("test content");
    apiInstrumentPost.getFields().get(3).setSelectedOptions(List.of("a"));
    apiInstrumentPost.getFields().get(4).setSelectedOptions(List.of("b"));
    errors = new BeanPropertyBindingResult(apiInstrumentPost, "apiInstrument");
    validator.validate(fullPost, errors);
    assertEquals(0, errors.getErrorCount());
  }

  @Test
  public void validateFieldDefinitionsAndContent() {
    User testUser = createInitAndLoginAnyUser();
    ApiInstrument apiInstrumentPost = new ApiInstrument();
    ApiInstrumentFullPost fullPost = new ApiInstrumentFullPost();
    fullPost.setApiInstrument(apiInstrumentPost);
    fullPost.setUser(testUser);
    fullPost.setTemplate(createInstrumentTemplateWithNumberAndTextFields());

    apiInstrumentPost.setFields(List.of(new ApiInventoryEntityField()));
    Errors errors = new BeanPropertyBindingResult(apiInstrumentPost, "apiInstrument");
    validator.validate(fullPost, errors);
    assertEquals(1, errors.getErrorCount());
    assertNotNull(errors.getGlobalErrors().get(0).getDefaultMessage());
    assertEquals(
        "\"fields\" array should have 2 fields, but had 1",
        errors.getGlobalErrors().get(0).getDefaultMessage());

    ApiInventoryEntityField invalidNumberField = new ApiInventoryEntityField();
    invalidNumberField.setType(ApiFieldType.NUMBER);
    invalidNumberField.setContent("abc");
    ApiInventoryEntityField validTextField = new ApiInventoryEntityField();
    validTextField.setType(ApiFieldType.TEXT);
    validTextField.setContent("valid text");
    apiInstrumentPost.setFields(List.of(invalidNumberField, validTextField));
    errors = new BeanPropertyBindingResult(apiInstrumentPost, "apiInstrument");
    validator.validate(fullPost, errors);
    assertEquals(1, errors.getErrorCount());
    String invalidNumberMessage = errors.getGlobalErrors().get(0).getDefaultMessage();
    assertNotNull(invalidNumberMessage);
    assertTrue(invalidNumberMessage.contains("Invalid number"));

    invalidNumberField.setContent("3.56");
    errors = new BeanPropertyBindingResult(apiInstrumentPost, "apiInstrument");
    validator.validate(fullPost, errors);
    assertEquals(0, errors.getErrorCount());
  }

  private InstrumentTemplate createInstrumentTemplateWithMandatoryFields() {
    InstrumentTemplate template = new InstrumentTemplate();

    InventoryTextField textField =
        new InventoryTextField("myText (mandatory - with default value)");
    textField.setMandatory(true);
    textField.setFieldData("default value");
    addField(template, textField);

    InventoryTextField textField2 = new InventoryTextField("myText (mandatory - no default value)");
    textField2.setMandatory(true);
    textField2.setData("");
    addField(template, textField2);

    InventoryTextField textField3 = new InventoryTextField("myText (not mandatory)");
    textField3.setData("");
    addField(template, textField3);

    InventoryRadioField radioField =
        new InventoryRadioField(
            createRadioDef(List.of("a", "b", "c")), "myRadio (mandatory - with default value)");
    radioField.setMandatory(true);
    radioField.setSelectedOptions(List.of("a"));
    addField(template, radioField);

    InventoryRadioField radioField2 =
        new InventoryRadioField(
            createRadioDef(List.of("a", "b", "c")), "myRadio (mandatory - no default value)");
    radioField2.setMandatory(true);
    addField(template, radioField2);

    InventoryRadioField radioField3 =
        new InventoryRadioField(createRadioDef(List.of("a", "b", "c")), "myRadio (not mandatory)");
    addField(template, radioField3);

    return template;
  }

  private InstrumentTemplate createInstrumentTemplateWithNumberAndTextFields() {
    InstrumentTemplate template = new InstrumentTemplate();

    InventoryNumberField numberField = new InventoryNumberField("my number");
    addField(template, numberField);

    InventoryTextField textField = new InventoryTextField("my text");
    addField(template, textField);

    return template;
  }

  private InventoryRadioFieldDef createRadioDef(List<String> options) {
    InventoryRadioFieldDef radioDef = new InventoryRadioFieldDef();
    radioDef.setRadioOptionsList(options);
    return radioDef;
  }

  private void addField(InstrumentTemplate template, InventoryEntityField field) {
    field.setColumnIndex(template.getFields().size() + 1);
    field.setInventoryRecord(template);
    template.getFields().add(field);
  }

  private Object getFirstArgument(FieldError fieldError) {
    assertNotNull(fieldError.getArguments());
    assertTrue(fieldError.getArguments().length > 0);
    return fieldError.getArguments()[0];
  }
}

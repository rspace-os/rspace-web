package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiField;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.model.inventory.Sample;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** For validating incoming sample template fields */
@Component
abstract class SampleTemplateFieldValidator implements Validator {

  private @Autowired ApiFieldToModelFieldFactory apiFieldToModelFieldFactory;

  private final Set<String> reservedFieldNames = (new Sample()).getReservedFieldNames();

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiField.class.isAssignableFrom(clazz);
  }

  /** Checks if incoming non-empty field name is valid. */
  protected void validateIncomingTemplateFieldName(Errors errors, String fieldName) {
    if (StringUtils.isEmpty(fieldName)) {
      return;
    }
    if (reservedFieldNames.contains(fieldName.toLowerCase())) {
      String reservedFieldNamesString =
          reservedFieldNames.stream().sorted().collect(Collectors.joining("/"));
      errors.rejectValue(
          "name",
          "errors.inventory.template.reserved.field.name",
          new Object[] {fieldName, reservedFieldNamesString},
          "reserved field name");
    }
    // check length matches limit for SampleField.name column
    if (StringUtils.length(fieldName) > 50) {
      errors.rejectValue(
          "name",
          "errors.inventory.template.field.name.too.long",
          new Object[] {fieldName, 50},
          "template field name too long");
    }
  }

  /** Checks if incoming field content is valid for the field type. */
  protected void validateIncomingTemplateFieldContent(
      Errors errors, ApiSampleField apiTemplateField) {
    // check field content - this requires type to be provided
    if (apiTemplateField.getType() != null) {
      try {
        // this internally calls model.validate
        apiFieldToModelFieldFactory.apiSampleFieldToModelField(apiTemplateField);
      } catch (IllegalArgumentException e) {
        errors.rejectValue(
            "content",
            "errors.inventory.template.invalid.field.content",
            new Object[] {e.getMessage()},
            e.getMessage());
      }
    }
  }
}

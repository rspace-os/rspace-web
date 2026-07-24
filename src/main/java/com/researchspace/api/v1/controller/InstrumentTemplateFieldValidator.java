package com.researchspace.api.v1.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.api.v1.model.ApiField;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.inventory.DataCiteRelationType;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** For validating incoming instrument template fields. */
@Component
abstract class InstrumentTemplateFieldValidator implements Validator {

  private @Autowired ApiFieldToModelFieldFactory apiFieldToModelFieldFactory;

  private final Set<String> reservedFieldNames = (new Instrument()).getReservedFieldNames();

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
          ListFormatUtils.formatList(
              reservedFieldNames.stream().sorted().toList(), ListFormatter.Type.OR);
      errors.rejectValue(
          "name",
          "errors.inventory.template.reservedFieldName",
          new Object[] {fieldName, reservedFieldNamesString},
          null);
    }
    if (StringUtils.length(fieldName) > 50) {
      errors.rejectValue(
          "name", "errors.inventory.template.fieldNameTooLong", new Object[] {fieldName, 50}, null);
    }
  }

  /** Checks if incoming field content is valid for the field type. */
  protected void validateIncomingTemplateFieldContent(
      Errors errors, ApiInventoryEntityField apiTemplateField) {
    if (apiTemplateField.getType() != null) {
      try {
        apiFieldToModelFieldFactory.apiInventoryFieldToModelField(apiTemplateField);
      } catch (IllegalArgumentException e) {
        errors.rejectValue(
            "content",
            "errors.inventory.template.invalidFieldContent",
            new Object[] {e.getMessage()},
            null);
      }
    }

    // Link fields: every entry in the allowed-relation-types whitelist must be a valid DataCite
    // relation type (a null/empty whitelist means "all relation types allowed"). Validate whenever
    // the whitelist is present, not only when the DTO declares type==LINK: an existing-field PUT
    // may
    // omit `type` while still updating a link field's whitelist (persisted by DB field type), so
    // gating on the incoming type would let an invalid whitelist bypass validation (RSDEV-1200).
    // (Mirrors SampleTemplateFieldValidator: instrument templates support link fields too.)
    if (apiTemplateField.getAllowedRelationTypes() != null) {
      for (String relationType : apiTemplateField.getAllowedRelationTypes()) {
        if (!DataCiteRelationType.isValid(relationType)) {
          errors.rejectValue(
              "allowedRelationTypes",
              "errors.inventory.template.invalidRelationType",
              new Object[] {relationType},
              null);
        }
      }
    }
  }
}

package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiTagInfo;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.webapp.controller.RSpaceTag;
import com.researchspace.webapp.controller.TagValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

/*
 * Helper methods for validating common inventory record properties.
 */
abstract class InventoryRecordValidator {

  @Autowired ApiExtraFieldsHelper extraFieldHelper;

  void validateInventoryRecordQuantity(ApiInventoryRecordInfo incomingApiInvRec, Errors errors) {
    if (incomingApiInvRec.getQuantity() != null) {
      if (isNegativeQuantity(incomingApiInvRec.getQuantity())) {
        errors.rejectValue("quantity", "", "Quantity must be positive");
      }
      if (isValidUnit(incomingApiInvRec.getQuantity())) {
        RSUnitDef def = RSUnitDef.getUnitById(incomingApiInvRec.getQuantity().getUnitId());
        if (!def.isAmount()) {
          errors.rejectValue(
              "quantity",
              "",
              String.format(
                  "Quantity unit id [%d] is not pointing to a unit of amount, "
                      + "it should be an id of a mass, volume or dimensionless unit.",
                  incomingApiInvRec.getQuantity().getUnitId()));
        }
      } else {
        errors.rejectValue(
            "quantity",
            "",
            String.format(
                "Quantity unit id [%d] is invalid", incomingApiInvRec.getQuantity().getUnitId()));
      }
    }
  }

  void validateExtraFields(ApiInventoryRecordInfo inventoryRecord, Errors errors) {
    if (inventoryRecord != null && !CollectionUtils.isEmpty(inventoryRecord.getExtraFields())) {
      int i = 0;
      for (ApiExtraField aef : inventoryRecord.getExtraFields()) {
        errors.pushNestedPath(String.format("extraFields[%d]", i++));
        validateExtraFieldName(aef.getName(), errors);
        ValidationUtils.invokeValidator(extraFieldHelper, aef, errors);
        errors.popNestedPath();
      }
    }
  }

  boolean isValidUnit(ApiQuantityInfo quantity) {
    return quantity.getUnitId() != null && RSUnitDef.exists(quantity.getUnitId());
  }

  boolean isNegativeQuantity(ApiQuantityInfo info) {
    return info.getNumericValue().compareTo(BigDecimal.ZERO) < 0;
  }

  void validateNameTooLong(String value, Errors errors) {
    validateTooLong("name", value, BaseRecord.DEFAULT_VARCHAR_LENGTH, errors);
  }

  void validateTags(List<ApiTagInfo> tags, Errors errors) {
    tags.stream()
        .forEach(
            tag -> {
              validateTooLong("tags", tag.getValue(), EditInfo.DESCRIPTION_LENGTH, errors);
              ValidationUtils.invokeValidator(
                  new TagValidator(), new RSpaceTag(tag.getValue()), errors);
            });
  }

  void validateDescriptionTooLong(String value, Errors errors) {
    validateTooLong("description", value, EditInfo.DESCRIPTION_LENGTH, errors);
  }

  void validateTooLong(String fieldName, String fieldValue, int maxLength, Errors errors) {
    if (!StringUtils.isBlank(fieldValue)) {
      if (fieldValue.length() > maxLength) {
        errors.rejectValue(
            fieldName,
            "errors.maxlength",
            new Object[] {fieldName, maxLength},
            fieldName + " is too long.");
      }
    }
  }

  void validateTooShort(String fieldName, String fieldValue, int minLength, Errors errors) {
    if (StringUtils.length(fieldValue) < minLength) {
      errors.rejectValue(
          fieldName,
          "errors.minlength",
          new Object[] {fieldName, minLength},
          fieldName + " is too short");
    }
  }

  void validateExtraFieldName(String fieldName, Errors errors) {
    if (fieldName != null && getReservedFieldNames().contains(fieldName.toLowerCase())) {
      String reservedFieldNamesString =
          getReservedFieldNames().stream().sorted().collect(Collectors.joining("/"));
      errors.rejectValue(
          "name",
          "errors.inventory.template.reserved.field.name",
          new Object[] {fieldName, reservedFieldNamesString},
          "reserved field name");
    }
  }

  // obtain InventoryRecord list of reserved names through container class
  private final Set<String> reservedGenericFieldNames = (new Container()).getReservedFieldNames();

  protected Set<String> getReservedFieldNames() {
    return reservedGenericFieldNames;
  }
}

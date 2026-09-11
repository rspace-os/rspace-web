package com.researchspace.api.v1.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiTagInfo;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.webapp.controller.RSpaceTag;
import com.researchspace.webapp.controller.TagValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

/*
 * Helper methods for validating common inventory record properties.
 */
abstract class InventoryRecordValidator {

  @Autowired ApiExtraFieldsHelper extraFieldHelper;

  void validateInventoryRecordQuantity(ApiInventoryRecordInfo incomingApiInvRec, Errors errors) {
    validateApiQuantityInfo(incomingApiInvRec.getQuantity(), "quantity", errors);
  }

  void validateApiQuantityInfo(
      ApiQuantityInfo incomingApiQuantity, String quantityFieldName, Errors errors) {
    if (incomingApiQuantity == null) {
      return;
    }

    if (isNegativeQuantity(incomingApiQuantity)) {
      errors.rejectValue(quantityFieldName, "errors.inventory.quantity.negative");
    }
    if (isValidUnit(incomingApiQuantity)) {
      RSUnitDef def = RSUnitDef.getUnitById(incomingApiQuantity.getUnitId());
      if (!def.isAmount()) {
        errors.rejectValue(
            quantityFieldName,
            "errors.inventory.quantity.unitNotAmount",
            new Object[] {incomingApiQuantity.getUnitId()},
            null);
      }
    } else {
      errors.rejectValue(
          quantityFieldName,
          "errors.inventory.quantity.unitInvalid",
          new Object[] {incomingApiQuantity.getUnitId()},
          null);
    }
  }

  void validateExtraFields(ApiInventoryRecordInfo inventoryRecord, Errors errors) {
    if (inventoryRecord != null && !CollectionUtils.isEmpty(inventoryRecord.getExtraFields())) {
      int i = 0;
      for (ApiExtraField aef : inventoryRecord.getExtraFields()) {
        errors.pushNestedPath(String.format("extraFields[%d]", i++));
        // A null element ("extraFields": [null]) is already a bean-validation error at binding,
        // but the controllers accept a BindingResult so this validator still runs; dereferencing
        // the element would turn that reported 400 into a 500 (Copilot review, PR #1090).
        if (aef != null) {
          validateExtraFieldName(aef.getName(), errors);
          ValidationUtils.invokeValidator(extraFieldHelper, aef, errors);
        }
        errors.popNestedPath();
      }
    }
  }

  // No rejection of operationFieldKey here, deliberately. The DTO property is READ_ONLY, so a value
  // in any request body is dropped at binding before this method runs. Rejecting it in this shared
  // method was tried and was wrong twice over: the template validators never call this method, so
  // a forged key still persisted (parallel review, C1); and rejecting a value the API itself
  // returns broke read-modify-write, so any client that GETs an operation-created sample, edits
  // one field and PUTs it back got a 400 on a field it never touched (C2 of that review). Ignoring
  // is also truthful: no update path can change a persisted key, so there is nothing a caller could
  // have meant by sending one. RSDEV-1231.

  boolean isValidUnit(ApiQuantityInfo quantity) {
    return quantity.getUnitId() != null && RSUnitDef.exists(quantity.getUnitId());
  }

  boolean isNegativeQuantity(ApiQuantityInfo info) {
    return info.getNumericValue() != null
        && (info.getNumericValue().compareTo(BigDecimal.ZERO) < 0);
  }

  void validateNameTooLong(String value, Errors errors) {
    validateTooLong("name", value, BaseRecord.DEFAULT_VARCHAR_LENGTH, errors);
  }

  void validateTags(List<ApiTagInfo> tags, Errors errors) {
    tags.stream()
        // A null element ("tags": [null]) is already a bean-validation error at binding, but the
        // controllers accept a BindingResult so this validator still runs; dereferencing the
        // element would turn that reported 400 into a 500 (Copilot review, PR #1090).
        .filter(Objects::nonNull)
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

  void validateNotNullAndBlank(String fieldName, String fieldValue, Errors errors) {
    if (fieldValue != null && StringUtils.isBlank(fieldValue)) {
      errors.rejectValue(fieldName, "errors.emptyString.generic", new Object[] {fieldName}, null);
    }
  }

  void validateTooLong(String fieldName, String fieldValue, int maxLength, Errors errors) {
    if (!StringUtils.isBlank(fieldValue)) {
      if (fieldValue.length() > maxLength) {
        errors.rejectValue(
            fieldName, "errors.maxLength", new Object[] {fieldName, maxLength}, null);
      }
    }
  }

  void validateTooShort(String fieldName, String fieldValue, int minLength, Errors errors) {
    if (StringUtils.length(fieldValue) < minLength) {
      errors.rejectValue(fieldName, "errors.minLength", new Object[] {fieldName, minLength}, null);
    }
  }

  void validateExtraFieldName(String fieldName, Errors errors) {
    if (fieldName != null && getReservedFieldNames().contains(fieldName.toLowerCase())) {
      String reservedFieldNamesString =
          ListFormatUtils.formatList(
              getReservedFieldNames().stream().sorted().toList(), ListFormatter.Type.OR);
      errors.rejectValue(
          "name",
          "errors.inventory.template.reservedFieldName",
          new Object[] {fieldName, reservedFieldNamesString},
          null);
    }
  }

  // obtain InventoryRecord list of reserved names through container class
  private final Set<String> reservedGenericFieldNames = (new Container()).getReservedFieldNames();

  protected Set<String> getReservedFieldNames() {
    return reservedGenericFieldNames;
  }
}

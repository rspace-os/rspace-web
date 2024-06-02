package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSubSampleAlias;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.units.RSUnitDef;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/** Validates incoming SampleTemplate request */
@Component
public abstract class SampleTemplateValidator extends SampleApiPostValidator {

  protected @Autowired SampleTemplateFieldPostValidator templateFieldPostValidator;
  protected @Autowired SampleTemplateFieldPutValidator templateFieldPutValidator;

  protected abstract Validator getIncomingFieldValidator(ApiSampleField incomingField);

  protected void validateFields(Errors errors, List<ApiSampleField> incomingFields) {
    int k = 0;
    for (ApiSampleField aef : incomingFields) {
      errors.pushNestedPath(String.format("fields[%d]", k++));
      ValidationUtils.invokeValidator(getIncomingFieldValidator(aef), aef, errors);
      errors.popNestedPath();
    }
  }

  protected void validateDefaultUnit(Errors errors, Integer incomingDefaultUnitId) {
    if (incomingDefaultUnitId != null && !RSUnitDef.exists(incomingDefaultUnitId)) {
      errors.rejectValue(
          "defaultUnitId",
          "errors.inventory.template.invalid.unitId",
          new Object[] {incomingDefaultUnitId},
          "Invalid unit id");
    }
  }

  private static final int SUBSAMPLE_ALIAS_UI_MIN_LENGTH = 2;

  protected void validateSubSampleAlias(Errors errors, ApiSubSampleAlias subSampleAlias) {
    if (subSampleAlias != null) {
      validateTooShort(
          "subSampleAlias.alias", subSampleAlias.getAlias(), SUBSAMPLE_ALIAS_UI_MIN_LENGTH, errors);
      validateTooLong(
          "subSampleAlias.alias",
          subSampleAlias.getAlias(),
          Sample.SUBSAMPLE_ALIAS_MAX_LENGTH,
          errors);

      validateTooShort(
          "subSampleAlias.plural",
          subSampleAlias.getPlural(),
          SUBSAMPLE_ALIAS_UI_MIN_LENGTH,
          errors);
      validateTooLong(
          "subSampleAlias.plural",
          subSampleAlias.getPlural(),
          Sample.SUBSAMPLE_ALIAS_MAX_LENGTH,
          errors);
    }
  }
}

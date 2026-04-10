package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InstrumentsApiController.ApiInstrumentFullPut;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validator for editing an existing instrument */
@Component
public class InstrumentApiPutFullValidator extends InstrumentApiValidator implements Validator {

  @Autowired private ApiFieldsHelper fieldHelper;

  @Data
  @AllArgsConstructor
  public static class ErrorAggregator implements Consumer<String> {
    Errors errors;

    @Override
    public void accept(String errorMessage) {
      if (!StringUtils.isEmpty(errorMessage)) {
        errors.reject("", errorMessage);
      }
    }
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(ApiInstrumentFullPut.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInstrumentFullPut apiInstrumentPut = (ApiInstrumentFullPut) target;
    validateFieldsAndTemplateFields(errors, apiInstrumentPut);
  }

  private void validateFieldsAndTemplateFields(
      Errors errors, ApiInstrumentFullPut apiInstrumentPut) {
    if (apiInstrumentPut.getTemplate() != null) {
      fieldHelper.checkApiFieldsMatchingFormFields(
          apiInstrumentPut.getApiInstrument().getFields(),
          apiInstrumentPut.getTemplate().getActiveFields(),
          apiInstrumentPut.getUser(),
          new ErrorAggregator(errors));
    }
  }
}

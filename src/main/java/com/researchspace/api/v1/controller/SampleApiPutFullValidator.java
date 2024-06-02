package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPut;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validator for editing an existing sample */
@Component
public class SampleApiPutFullValidator extends SampleApiValidator implements Validator {

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
    return clazz.isAssignableFrom(ApiSampleFullPut.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiSampleFullPut apiSamplePost = (ApiSampleFullPut) target;
    validateFieldsAndTemplateFields(errors, apiSamplePost);
  }

  private void validateFieldsAndTemplateFields(Errors errors, ApiSampleFullPut apiSamplePut) {
    if (apiSamplePut.getTemplate() != null) {
      fieldHelper.checkApiFieldsMatchingFormFields(
          apiSamplePut.getApiSample().getFields(),
          apiSamplePut.getTemplate().getActiveFields(),
          apiSamplePut.getUser(),
          new ErrorAggregator(errors));
    }
  }
}

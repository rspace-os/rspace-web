package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InstrumentsApiController.ApiInstrumentFullPost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validator for creating a new instrument */
@Component
public class InstrumentApiPostFullValidator implements Validator {

  @Autowired private ApiFieldsHelper fieldHelper;
  private static final String INSTRUMENT_ENTITY_NAME_TYPE = "instrument";

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(ApiInstrumentFullPost.class);
  }

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
  public void validate(Object target, Errors errors) {
    ApiInstrumentFullPost apiInstrumentPost = (ApiInstrumentFullPost) target;
    validateFieldsAndTemplateFields(errors, apiInstrumentPost);
  }

  private void validateFieldsAndTemplateFields(
      Errors errors, ApiInstrumentFullPost apiInstrumentPost) {
    if (apiInstrumentPost.getTemplate() != null) {
      List<ApiInventoryEntityField> incomingApiFields =
          apiInstrumentPost.getApiInstrument().getFields();
      List<InventoryEntityField> templateFields = apiInstrumentPost.getTemplate().getActiveFields();

      if (!incomingApiFields.isEmpty()) {
        // run validation against template fields
        fieldHelper.checkApiFieldsMatchingFormFields(
            incomingApiFields,
            templateFields,
            apiInstrumentPost.getUser(),
            new ErrorAggregator(errors));
      }

      fieldHelper.validateMandatoryFieldsForEntityPost(
          INSTRUMENT_ENTITY_NAME_TYPE, incomingApiFields, templateFields, errors);
    }
  }
}

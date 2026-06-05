package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInventoryEntityField;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/** Common base for InstrumentTemplate post/put validators. */
@Component
public abstract class InstrumentTemplateValidator extends InstrumentApiPostValidator {

  protected @Autowired InstrumentTemplateFieldPostValidator templateFieldPostValidator;
  protected @Autowired InstrumentTemplateFieldPutValidator templateFieldPutValidator;

  protected abstract Validator getIncomingFieldValidator(ApiInventoryEntityField incomingField);

  protected void validateFields(Errors errors, List<ApiInventoryEntityField> incomingFields) {
    int k = 0;
    for (ApiInventoryEntityField aef : incomingFields) {
      errors.pushNestedPath(String.format("fields[%d]", k++));
      ValidationUtils.invokeValidator(getIncomingFieldValidator(aef), aef, errors);
      errors.popNestedPath();
    }
  }
}

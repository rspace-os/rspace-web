package com.researchspace.model.dtos;

import static com.researchspace.model.record.BaseRecord.DEFAULT_VARCHAR_LENGTH;

import com.researchspace.model.field.StringFieldForm;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates the business logic of the constraints on a String Field editor page. */
public class StringFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(StringFieldDTO.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    super.validate(target, errors);
    StringFieldDTO<StringFieldForm> dto = (StringFieldDTO) target;
    if (!StringUtils.isEmpty(dto.getDefaultStringValue())
        && dto.getDefaultStringValue().length() > DEFAULT_VARCHAR_LENGTH) {
      errors.rejectValue(
          "defaultStringValue",
          "errors.maxlength",
          new Object[] {"default value", DEFAULT_VARCHAR_LENGTH},
          "defaultStringValue is too long");
    }
  }
}

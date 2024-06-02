package com.researchspace.model.dtos;

import com.researchspace.model.field.FieldUtils;
import com.researchspace.model.field.RadioFieldForm;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/** Validates Business logic of RadioField submission */
public class RadioFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(RadioFieldDTO.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    RadioFieldDTO<RadioFieldForm> dto = (RadioFieldDTO) target;
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "fieldName", "no.name");
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "radioValues", "no.multiplechoice");
    if (isNameTooLong(dto.getFieldName())) {
      rejectTooLong(errors);
    }
    String vals = dto.getRadioValues();
    if (!FieldUtils.isValidRadioOrChoiceString(vals)) {
      errors.rejectValue("radioValues", "choiceoptions.invalidformat");
    }
  }
}

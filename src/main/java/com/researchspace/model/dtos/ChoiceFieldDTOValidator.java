package com.researchspace.model.dtos;

import com.researchspace.model.field.FieldUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

public class ChoiceFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ChoiceFieldDTO.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    if (!(target instanceof ChoiceFieldDTO<?> dto)) {
      throw new IllegalArgumentException("Target must be a ChoiceFieldDTO");
    }
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "fieldName", "errors.noValue.name");
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "multipleChoice", "errors.noValue.multipleChoice");
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "choiceValues", "errors.noValue.multipleChoice");
    if (isNameTooLong(dto.getFieldName())) {
      rejectTooLong(errors);
    }
    String vals = dto.getChoiceValues();
    if (!FieldUtils.isValidRadioOrChoiceString(vals)) {
      errors.rejectValue("choiceValues", "form.choiceOptions.invalidFormat");
    }
  }
}

package com.researchspace.model.dtos;

import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.FieldUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

public class ChoiceFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(ChoiceFieldDTO.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void validate(Object target, Errors errors) {
    ChoiceFieldDTO<ChoiceFieldForm> dto = (ChoiceFieldDTO<ChoiceFieldForm>) target;
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

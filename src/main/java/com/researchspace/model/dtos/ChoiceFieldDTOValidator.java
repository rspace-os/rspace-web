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
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "fieldName", "no.name");
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "multipleChoice", "no.multiplechoice");
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "choiceValues", "no.multiplechoice");
    if (isNameTooLong(dto.getFieldName())) {
      rejectTooLong(errors);
    }
    String vals = dto.getChoiceValues();
    if (!FieldUtils.isValidRadioOrChoiceString(vals)) {
      errors.rejectValue("choiceValues", "choiceoptions.invalidformat");
    }
  }
}

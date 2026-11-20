package com.researchspace.model.dtos;

/** Validates field DTOs that carry only a name and the mandatory flag. */
public class NameOnlyFieldDTOValidator extends AbstractFieldFormValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return AbstractFormFieldDTO.class.isAssignableFrom(clazz);
  }
}

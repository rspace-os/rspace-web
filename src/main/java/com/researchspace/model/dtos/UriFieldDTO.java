package com.researchspace.model.dtos;

import com.researchspace.model.field.URIFieldForm;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Uri fields have no settings beyond name and mandatory. They can be created only through the forms
 * API, but the form field editor renames, reorders, deletes and (un)marks them as mandatory.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UriFieldDTO extends AbstractFormFieldDTO<URIFieldForm> {

  public UriFieldDTO(String name, boolean isMandatory) {
    super(name, isMandatory);
  }

  @Override
  public void copyValuesIntoFieldForm(URIFieldForm fieldForm) {
    copyCommonValuesIntoFieldForm(fieldForm);
  }

  @Override
  public URIFieldForm createFieldForm() {
    URIFieldForm fieldForm = new URIFieldForm(getName());
    copyValuesIntoFieldForm(fieldForm);
    return fieldForm;
  }
}

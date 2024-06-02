package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.AttachmentFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "type", "lastModified", "index"})
public class ApiAttachmentFormField extends ApiFormField {

  public ApiAttachmentFormField(AttachmentFieldForm formField) {
    super(formField);
  }
}

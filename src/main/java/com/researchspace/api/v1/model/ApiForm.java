package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.FormTemplatesCommon.ApiFormTemplateLinkSource;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.AbstractForm;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "stableId",
      "version",
      "name",
      "tags",
      "formState",
      "accessControl",
      "fields",
      "_links"
    })
public class ApiForm extends ApiFormInfo implements ApiFormTemplateLinkSource {

  private List<ApiFormField> fields = new ArrayList<>();

  public ApiForm(AbstractForm form) {
    super(form);
    for (FieldForm formField : form.getFieldForms()) {
      fields.add(ApiFormField.fromFieldForm(formField));
    }
  }
}

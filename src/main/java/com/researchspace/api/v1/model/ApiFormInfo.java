/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.AccessControl;
import com.researchspace.model.record.AbstractForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** The Form on which the document structure is based. Does not include fields. */
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
      "iconId",
      "_links"
    })
public class ApiFormInfo extends ApiAbstractFormInfo {

  private AccessControl accessControl;

  public ApiFormInfo(AbstractForm form) {
    super(form);
    setAccessControl(form.getAccessControl());
  }
}

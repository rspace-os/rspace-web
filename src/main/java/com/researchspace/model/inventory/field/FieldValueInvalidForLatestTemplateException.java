package com.researchspace.model.inventory.field;

/**
 * A field's current value is not allowed by the latest definition of its template field, so the
 * item cannot be updated to the latest template version until the value is changed. This is an
 * expected validation outcome, mapped to HTTP 422 by {@code ApiControllerAdvice}.
 */
public class FieldValueInvalidForLatestTemplateException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public FieldValueInvalidForLatestTemplateException(String fieldName, String fieldData) {
    super(
        "Field ["
            + fieldName
            + "] value ["
            + fieldData
            + "] is invalid according to latest template field definition");
  }
}

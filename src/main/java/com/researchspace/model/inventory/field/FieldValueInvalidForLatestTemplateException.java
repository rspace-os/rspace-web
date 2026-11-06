package com.researchspace.model.inventory.field;

import com.researchspace.model.field.LocalizedIllegalStateException;

/**
 * A field's current value is not allowed by the latest definition of its template field, so the
 * item cannot be updated to the latest template version until the value is changed. This is an
 * expected validation outcome, mapped to HTTP 422 by {@code ApiControllerAdvice}.
 */
public class FieldValueInvalidForLatestTemplateException extends LocalizedIllegalStateException {

  private static final long serialVersionUID = 1L;

  /** Message key for the user-facing phrase, resolved with {@link #getArgs()}. */
  public static final String ERROR_CODE = "validation.inventoryField.invalidForLatestTemplate";

  private final String fieldName;
  private final String fieldData;

  public FieldValueInvalidForLatestTemplateException(String fieldName, String fieldData) {
    super(ERROR_CODE, fieldName, fieldData);
    this.fieldName = fieldName;
    this.fieldData = fieldData;
  }

  public Object[] getArgs() {
    return new Object[] {fieldName, fieldData};
  }
}

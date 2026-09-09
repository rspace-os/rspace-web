package com.researchspace.api.v1.model;

import lombok.Getter;

/**
 * A property the operations endpoint does not declare appeared in the request body.
 *
 * <p>Carries the property NAME and a message key rather than a built English sentence. Jackson
 * wraps whatever a {@code @JsonAnySetter} throws and {@code
 * RestControllerAdvice.handleHttpMessageNotReadable} puts the resulting {@code
 * getLocalizedMessage()} straight into the API error, so a plain {@code IllegalArgumentException}
 * shipped untranslated English AND Jackson's reference chain, which names the DTO class and package
 * (parallel review, I6). {@code ApiControllerAdvice} unwraps this cause and resolves the key
 * instead, matching how the same mistake on the created sample's own fields is already reported
 * ({@code errors.inventory.operation.undeclaredProperty}).
 */
@Getter
public class UnknownOperationPropertyException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * The message key resolved by ApiControllerAdvice; takes the property name as its one argument.
   */
  public static final String MESSAGE_KEY = "errors.inventory.operation.unknownProperty";

  private final String propertyName;

  public UnknownOperationPropertyException(String propertyName) {
    super(MESSAGE_KEY);
    this.propertyName = propertyName;
  }
}

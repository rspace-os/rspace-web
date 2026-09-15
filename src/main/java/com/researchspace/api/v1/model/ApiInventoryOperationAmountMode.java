package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a client decided the amount it is taking from an origin, mirroring the wizard's own {@code
 * AmountMode} string union. Only the distinction the backend can act on is modelled: whether the
 * submitted amount is a value the user typed, or a snapshot of the origin's whole quantity.
 *
 * <p>{@link #ALL} makes {@code amountTaken} a compare-and-swap guard: the client is asserting "this
 * was the origin's entire quantity when I read it", so the endpoint rejects the request with 409 if
 * the live quantity no longer matches, rather than emptying an origin the user never saw. {@link
 * #EXPLICIT} amounts are user-entered and carry no such claim. The wizard's "perSubsample" and
 * "same" modes are both EXPLICIT here for that reason. See DevDocs/adr/0007.
 */
public enum ApiInventoryOperationAmountMode {
  EXPLICIT("explicit"),
  ALL("all"),

  /**
   * Any wire value this enum does not recognise. Binding an unknown mode here rather than throwing
   * from {@link #fromWireValue} is what keeps the rejection translatable: a throw in a
   * {@code @JsonCreator} surfaces as {@code HttpMessageNotReadableException}, whose raw English
   * message the shared advice copies straight into the 400 body, so an untranslated developer
   * string (echoing the client's own input) reached the user. {@code
   * InventoryOperationPostValidator} rejects this constant with a catalog key instead, and the
   * request still fails with a 400.
   *
   * <p>Kept distinct from a null mode: absent means "this client predates the field" and stays
   * acceptable, while UNKNOWN means the client sent something it made up.
   */
  UNKNOWN("unknown");

  private final String wireValue;

  ApiInventoryOperationAmountMode(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String getWireValue() {
    return wireValue;
  }

  /**
   * Binds the lowercase wire value, case-insensitively. An unrecognised value binds to {@link
   * #UNKNOWN}, which the endpoint's validator rejects with a translated message; see that constant
   * for why this does not throw.
   */
  @JsonCreator
  public static ApiInventoryOperationAmountMode fromWireValue(String value) {
    for (ApiInventoryOperationAmountMode mode : values()) {
      if (mode.wireValue.equalsIgnoreCase(value)) {
        return mode;
      }
    }
    return UNKNOWN;
  }
}

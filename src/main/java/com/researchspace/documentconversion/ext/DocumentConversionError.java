package com.researchspace.documentconversion.ext;

import java.util.Arrays;
import java.util.Optional;

/** Stable error codes returned by document-conversion adapters. */
public enum DocumentConversionError {
  AUTHENTICATION_FAILED(
      "conversion.authentication-failed", "errors.documentConversion.authenticationFailed"),
  OUTPUT_CREATE_FAILED(
      "conversion.output-create-failed", "errors.documentConversion.outputCreateFailed"),
  UNSUPPORTED("conversion.unsupported", "errors.documentConversion.unsupported"),
  INPUT_INVALID("conversion.input-invalid", "errors.documentConversion.inputInvalid"),
  INPUT_TOO_LARGE("conversion.input-too-large", "errors.documentConversion.inputTooLarge"),
  OUTPUT_INVALID("conversion.output-invalid", "errors.documentConversion.outputInvalid"),
  OUTPUT_TOO_LARGE("conversion.output-too-large", "errors.documentConversion.outputTooLarge"),
  SERVICE_BUSY("conversion.service-busy", "errors.documentConversion.serviceBusy"),
  SERVICE_UNAVAILABLE(
      "conversion.service-unavailable", "errors.documentConversion.serviceUnavailable"),
  TIMEOUT("conversion.timeout", "errors.documentConversion.timeout"),
  FAILED("conversion.failed", "errors.documentConversion.failed");

  private final String code;
  private final String messageKey;

  DocumentConversionError(String code, String messageKey) {
    this.code = code;
    this.messageKey = messageKey;
  }

  public String code() {
    return code;
  }

  public String messageKey() {
    return messageKey;
  }

  public static Optional<DocumentConversionError> fromCode(String code) {
    return Arrays.stream(values()).filter(error -> error.code.equals(code)).findFirst();
  }

  public static String messageKeyForCode(String code) {
    return fromCode(code).orElse(FAILED).messageKey();
  }
}

package com.researchspace.conversion;

/** Stable error codes returned to RSpace by the conversion sidecar. */
enum ConversionError {
  FAILED("conversion.failed"),
  INPUT_INVALID("conversion.input-invalid"),
  INPUT_TOO_LARGE("conversion.input-too-large"),
  OUTPUT_INVALID("conversion.output-invalid"),
  OUTPUT_TOO_LARGE("conversion.output-too-large"),
  SERVICE_BUSY("conversion.service-busy"),
  SERVICE_UNAVAILABLE("conversion.service-unavailable"),
  TIMEOUT("conversion.timeout"),
  UNSUPPORTED("conversion.unsupported");

  static final String HEADER = "X-RSpace-Conversion-Error";

  private final String code;

  ConversionError(String code) {
    this.code = code;
  }

  String code() {
    return code;
  }
}

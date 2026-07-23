package com.researchspace.webapp.integrations.b2inst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Error payload returned by B2INST (InvenioRDM) when a request fails, e.g. when a draft record
 * fails community validation on submission for review.
 *
 * <p>Example: {@code {"status": 400, "message": "A validation error occurred.", "errors":
 * [{"field": "instrument_type", "messages": ["Missing data for required field."]}]}}. The {@code
 * errors} array is absent on non-validation failures such as permission errors.
 *
 * @param status HTTP status code reported in the payload (e.g. 400).
 * @param message Human-readable summary of the failure.
 * @param errors Per-field validation failures; may be null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record B2instErrorResponse(int status, String message, List<FieldError> errors) {

  /**
   * A single field-level validation failure.
   *
   * @param field Dot-path to the offending field (e.g. {@code instrument_type}).
   * @param messages One or more messages describing why the field failed.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FieldError(String field, List<String> messages) {}

  /**
   * One-line human-readable summary: field errors joined as {@code field: message} (message-only
   * when the payload omits the field name), else the top-level message, else {@code null} when the
   * payload carries nothing usable.
   */
  public String describe() {
    String fieldSummary =
        errors == null
            ? ""
            : errors.stream()
                .filter(Objects::nonNull)
                .filter(error -> error.messages() != null && !error.messages().isEmpty())
                .map(B2instErrorResponse::describeEntry)
                .collect(Collectors.joining("; "));
    if (!fieldSummary.isBlank()) {
      return fieldSummary;
    }
    return (message == null || message.isBlank()) ? null : message;
  }

  private static String describeEntry(FieldError error) {
    String joinedMessages = String.join(" ", error.messages());
    return (error.field() == null || error.field().isBlank())
        ? joinedMessages
        : error.field() + ": " + joinedMessages;
  }
}

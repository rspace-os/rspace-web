package com.researchspace.webapp.integrations.b2inst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Error payload returned by B2INST (InvenioRDM). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record B2instErrorResponse(int status, String message, List<FieldError> errors) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FieldError(String field, List<String> messages) {}

  public String describe() {
    String fieldSummary =
        errors == null
            ? ""
            : errors.stream()
                .filter(Objects::nonNull)
                .filter(error -> error.messages() != null)
                .map(B2instErrorResponse::describeEntry)
                .filter(entrySummary -> !entrySummary.isEmpty())
                .collect(Collectors.joining("; "));
    if (!fieldSummary.isBlank()) {
      return fieldSummary;
    }
    return (message == null || message.isBlank()) ? null : message;
  }

  private static String describeEntry(FieldError error) {
    String joinedMessages =
        error.messages().stream()
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(message -> !message.isEmpty())
            .collect(Collectors.joining(" "));
    if (joinedMessages.isEmpty()) {
      return "";
    }
    return (error.field() == null || error.field().isBlank())
        ? joinedMessages
        : error.field() + ": " + joinedMessages;
  }
}

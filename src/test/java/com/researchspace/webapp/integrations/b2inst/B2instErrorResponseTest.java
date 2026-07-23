package com.researchspace.webapp.integrations.b2inst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.core.util.JacksonUtil;
import java.util.List;
import org.junit.jupiter.api.Test;

class B2instErrorResponseTest {

  @Test
  void deserialisesInvenioValidationPayloadIgnoringUnknownFields() {
    String json =
        "{\"status\":400,\"message\":\"A validation error occurred.\",\"unknown\":true,"
            + "\"errors\":[{\"field\":\"instrument_type\","
            + "\"messages\":[\"Missing data for required field.\"],\"extra\":1}]}";

    B2instErrorResponse parsed = JacksonUtil.fromJson(json, B2instErrorResponse.class);

    assertEquals("instrument_type: Missing data for required field.", parsed.describe());
  }

  @Test
  void describeJoinsMultipleFieldErrors() {
    B2instErrorResponse response =
        new B2instErrorResponse(
            400,
            "A validation error occurred.",
            List.of(
                new B2instErrorResponse.FieldError(
                    "instrument_type", List.of("Missing data for required field.")),
                new B2instErrorResponse.FieldError(
                    "owners", List.of("Shorter than minimum length 1."))));

    assertEquals(
        "instrument_type: Missing data for required field.; owners: Shorter than minimum"
            + " length 1.",
        response.describe());
  }

  @Test
  void describeToleratesNullErrorEntries() {
    String json =
        "{\"status\":400,\"message\":\"A validation error occurred.\",\"errors\":"
            + "[null,{\"field\":\"instrument_type\",\"messages\":[\"Missing data for required"
            + " field.\"]}]}";

    B2instErrorResponse parsed = JacksonUtil.fromJson(json, B2instErrorResponse.class);

    assertEquals("instrument_type: Missing data for required field.", parsed.describe());
  }

  @Test
  void describeFallsBackToMessageWhenAllErrorEntriesAreNull() {
    String json = "{\"status\":400,\"message\":\"A validation error occurred.\",\"errors\":[null]}";

    B2instErrorResponse parsed = JacksonUtil.fromJson(json, B2instErrorResponse.class);

    assertEquals("A validation error occurred.", parsed.describe());
  }

  @Test
  void describeRendersFieldlessErrorMessagesWithoutPrefix() {
    String json =
        "{\"status\":400,\"message\":\"A validation error occurred.\",\"errors\":"
            + "[{\"messages\":[\"Record has validation errors.\"]}]}";

    B2instErrorResponse parsed = JacksonUtil.fromJson(json, B2instErrorResponse.class);

    assertEquals("Record has validation errors.", parsed.describe());
  }

  @Test
  void describeRendersBlankFieldErrorMessagesWithoutPrefix() {
    B2instErrorResponse response =
        new B2instErrorResponse(
            400,
            "A validation error occurred.",
            List.of(
                new B2instErrorResponse.FieldError(" ", List.of("Record has validation errors.")),
                new B2instErrorResponse.FieldError(
                    "instrument_type", List.of("Missing data for required field."))));

    assertEquals(
        "Record has validation errors.; instrument_type: Missing data for required field.",
        response.describe());
  }

  @Test
  void describeSkipsNullAndBlankMessageEntries() {
    String json =
        "{\"status\":400,\"message\":\"A validation error occurred.\",\"errors\":"
            + "[{\"field\":\"instrument_type\",\"messages\":[null,\" \",\"Missing data for"
            + " required field.\"]}]}";

    B2instErrorResponse parsed = JacksonUtil.fromJson(json, B2instErrorResponse.class);

    assertEquals("instrument_type: Missing data for required field.", parsed.describe());
  }

  @Test
  void describeFallsBackToMessageWhenAllMessagesAreNullOrBlank() {
    String json =
        "{\"status\":400,\"message\":\"A validation error occurred.\",\"errors\":"
            + "[{\"field\":\"instrument_type\",\"messages\":[null,\" \"]}]}";

    B2instErrorResponse parsed = JacksonUtil.fromJson(json, B2instErrorResponse.class);

    assertEquals("A validation error occurred.", parsed.describe());
  }

  @Test
  void describeFallsBackToTopLevelMessage() {
    assertEquals(
        "Permission denied.", new B2instErrorResponse(403, "Permission denied.", null).describe());
  }

  @Test
  void describeReturnsNullWhenNothingUsable() {
    assertNull(new B2instErrorResponse(500, " ", List.of()).describe());
  }
}

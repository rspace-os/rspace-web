package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;

import com.researchspace.model.collection.AccessPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ApiV2OpenApiBookingPaths {

  private static final String JSON = "application/json";

  static void addBookingDirectoryPaths(Map<String, Object> paths) {
    paths.put(
        "/api/v2/booking-settings/access-grantees",
        ordered("get", bookingDirectoryOperation(true)));
    paths.put(
        "/api/v2/booking-configuration-targets", ordered("get", bookingDirectoryOperation(false)));
  }

  private static Map<String, Object> bookingDirectoryOperation(boolean grantees) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put(
        "operationId",
        grantees ? "searchBookingSettingsAccessGrantees" : "searchBookingConfigurationTargets");
    operation.put(
        "summary",
        grantees
            ? "Search eligible default-access grantees"
            : "Search eligible Booking configuration targets");
    operation.put("tags", List.of("booking-directories"));
    operation.put(
        "security", List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())));
    operation.put(
        "x-rspace-access",
        ordered(
            "description",
            grantees
                ? "Authenticated sysadmins editing Booking creation defaults."
                : "Authenticated callers; ordinary users see owned eligible Instruments and "
                    + "sysadmins see every eligible Instrument.",
            "denialReasonCodes",
            List.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN)));
    operation.put(
        "parameters",
        List.of(
            parameter(
                "query",
                "query",
                true,
                ordered("type", "string", "minLength", 2),
                "Case-insensitive search text."),
            parameter(
                "limit",
                "query",
                false,
                ordered("type", "integer", "minimum", 1, "maximum", 50, "default", 20),
                "Maximum results to return.")));
    Map<String, Object> resultSchema =
        ordered(
            "type",
            "array",
            "items",
            ref(grantees ? "ResourceGranteeDirectoryEntry" : "BookingConfigurationTarget"));
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put(
        "200",
        ordered(
            "description",
            grantees ? "Eligible users and groups." : "Eligible Instrument summaries.",
            "content",
            Map.of(JSON, ordered("schema", resultSchema))));
    responses.put("400", responseRef("BadRequest"));
    responses.put("401", responseRef("Unauthenticated"));
    responses.put("403", responseRef("Forbidden"));
    responses.put("429", responseRef("TooManyRequests"));
    responses.put("500", responseRef("UnexpectedError"));
    operation.put("responses", responses);
    operation.put(
        "x-rspace-operation",
        grantees ? "BOOKING_SETTINGS_GRANTEE_SEARCH" : "BOOKING_TARGET_SEARCH");
    return operation;
  }

  private static Map<String, Object> parameter(
      String name, String in, boolean required, Map<String, Object> schema, String description) {
    return ApiV2OpenApiQueryParameters.parameter(name, in, required, schema, description);
  }

  private static Map<String, Object> responseRef(String name) {
    return ApiV2OpenApiGenerator.responseRef(name);
  }
}

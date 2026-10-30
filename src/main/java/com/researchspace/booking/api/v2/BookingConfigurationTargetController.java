package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.controller.ResourceAccessController;
import com.researchspace.booking.service.BookingConfigurationTarget;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Safe, bounded creation-target search for Booking configurations. */
@RestController
public class BookingConfigurationTargetController {

  public record TargetDirectoryDocument(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BookingConfigurationTarget> docs) {
    public TargetDirectoryDocument {
      docs = List.copyOf(docs);
    }
  }

  private final BookingConfigurationTargetManager manager;

  public BookingConfigurationTargetController(BookingConfigurationTargetManager manager) {
    this.manager = manager;
  }

  /**
   * Lists eligible targets by name. Without a query, or with a blank one, it browses the first
   * {@code limit} targets; a non-blank query must have at least two characters.
   */
  @GetMapping("/api/v2/booking-configuration-targets")
  @Operation(
      operationId = "searchBookingConfigurationTargets",
      summary = "Search eligible Booking configuration targets",
      description =
          "Lists eligible targets ordered by name. Omitted, empty, or whitespace-only `q`"
              + " browses the first `limit` eligible targets. A nonblank `q` filters names by"
              + " case-insensitive containment and must have at least two characters after"
              + " trimming.",
      tags = "booking-directories",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Eligible Instrument summaries in docs, without paging metadata."),
        @ApiResponse(responseCode = "400"),
        @ApiResponse(responseCode = "401"),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable."),
        @ApiResponse(responseCode = "429"),
        @ApiResponse(responseCode = "500")
      })
  public TargetDirectoryDocument search(
      @Parameter(
              description =
                  "Optional case-insensitive search text. Omit it or provide an empty or"
                      + " whitespace-only value to browse the first `limit` eligible targets by"
                      + " name. A nonblank value must have at least two characters after"
                      + " trimming.")
          @RequestParam(name = "q", required = false)
          String query,
      @Parameter(
              description = "Maximum results to return.",
              schema = @Schema(minimum = "1", maximum = "50", defaultValue = "20"))
          @RequestParam(defaultValue = "20")
          int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    if (caller == null) {
      throw new ApiV2AuthenticationException();
    }
    String trimmed = query == null ? "" : query.trim();
    if (!trimmed.isEmpty() && trimmed.length() < 2) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return new TargetDirectoryDocument(
        manager.search(
            trimmed.isEmpty() ? null : trimmed,
            ResourceAccessController.validateLimit(limit),
            caller.subject()));
  }
}

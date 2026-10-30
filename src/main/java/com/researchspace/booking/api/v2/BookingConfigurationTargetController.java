package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.controller.ResourceAccessController;
import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
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

  private final BookingConfigurationTargetManager manager;

  public BookingConfigurationTargetController(BookingConfigurationTargetManager manager) {
    this.manager = manager;
  }

  @GetMapping("/api/v2/booking-configuration-targets")
  @Operation(
      operationId = "searchBookingConfigurationTargets",
      summary = "Search eligible Booking configuration targets",
      tags = "booking-directories",
      responses = {
        @ApiResponse(responseCode = "200", description = "Eligible Instrument summaries."),
        @ApiResponse(responseCode = "400"),
        @ApiResponse(responseCode = "401"),
        @ApiResponse(responseCode = "403"),
        @ApiResponse(responseCode = "429"),
        @ApiResponse(responseCode = "500")
      })
  public List<BookingConfigurationTarget> search(
      @Parameter(description = "Case-insensitive search text.", schema = @Schema(minLength = 2))
          @RequestParam
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
    if (trimmed.length() < 2) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return manager.search(trimmed, ResourceAccessController.validateLimit(limit), caller.subject());
  }
}

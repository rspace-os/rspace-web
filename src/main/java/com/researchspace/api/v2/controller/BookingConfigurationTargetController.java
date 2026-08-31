package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingConfigurationTarget;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
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
  public List<BookingConfigurationTarget> search(
      @RequestParam String query,
      @RequestParam(defaultValue = "20") int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    if (caller == null) {
      throw new ApiV2AuthenticationException();
    }
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.length() < 2 || limit < 1 || limit > 50) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return manager.search(trimmed, limit, caller.subject());
  }
}

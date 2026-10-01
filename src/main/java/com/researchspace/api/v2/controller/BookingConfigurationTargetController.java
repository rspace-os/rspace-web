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

  /**
   * Lists eligible targets by name. Without a query, or with a blank one, it browses the first
   * {@code limit} targets; a non-blank query must have at least two characters.
   */
  @GetMapping("/api/v2/booking-configuration-targets")
  public List<BookingConfigurationTarget> search(
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "20") int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE, required = false)
          ApiV2Caller caller) {
    if (caller == null) {
      throw new ApiV2AuthenticationException();
    }
    String trimmed = query == null ? "" : query.trim();
    if ((!trimmed.isEmpty() && trimmed.length() < 2) || limit < 1 || limit > 50) {
      throw new ApiV2BadRequestException("errors.api.v2.invalidRequest");
    }
    return manager.search(trimmed.isEmpty() ? null : trimmed, limit, caller.subject());
  }
}

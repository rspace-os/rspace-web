package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingCalendarManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Positive;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API v2 transport for a one-off calendar file covering a single booking. Unlike a
 * subscription, nothing is created or stored: the file is generated per request and never updates
 * itself once saved.
 */
@RestController
@Validated
@RequestMapping("/api/v2/bookings/{bookingId}/calendar-file")
public class BookingCalendarDownloadController {

  private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

  private final BookingCalendarManager manager;

  public BookingCalendarDownloadController(BookingCalendarManager manager) {
    this.manager = manager;
  }

  @GetMapping
  @Operation(
      operationId = "downloadBookingCalendarFile",
      summary = "Download a booking as a calendar file",
      description =
          "Returns one confirmed booking as an iCalendar attachment, shaped by the caller's "
              + "visibility of it. No subscription is created or changed.",
      responses = {
        @ApiResponse(responseCode = "200", description = "The generated calendar file."),
        @ApiResponse(responseCode = "401", description = "Authentication is required."),
        @ApiResponse(responseCode = "403", description = "Booking is unavailable."),
        @ApiResponse(
            responseCode = "404",
            description = "The booking was not found, or is not confirmed.")
      })
  public ResponseEntity<byte[]> download(
      @PathVariable @Positive(message = "{errors.api.v2.invalidRequest}") Long bookingId,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller,
      Locale locale) {
    Optional<BookingCalendarManager.Download> download =
        manager.download(bookingId, caller.subject(), locale);
    if (download.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(CALENDAR);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(download.get().filename()).build());
    headers.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return new ResponseEntity<>(download.get().body(), headers, HttpStatus.OK);
  }
}

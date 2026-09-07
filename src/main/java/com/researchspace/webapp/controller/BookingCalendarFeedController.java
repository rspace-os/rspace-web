package com.researchspace.webapp.controller;

import com.researchspace.booking.service.BookingCalendarManager;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous transport for one validated booking calendar bearer credential. */
@RestController
public final class BookingCalendarFeedController {

  static final String PATH = "/public/booking/calendars/feed.ics";
  static final int RETRY_AFTER_SECONDS = 30;

  private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
  private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");
  private static final byte[] EMPTY = new byte[0];

  private final BookingCalendarManager manager;

  public BookingCalendarFeedController(BookingCalendarManager manager) {
    this.manager = manager;
  }

  @GetMapping(PATH)
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  public ResponseEntity<byte[]> feed(
      @RequestParam MultiValueMap<String, String> parameters, Locale locale) {
    String token = exactToken(parameters);
    if (token == null) {
      return empty(HttpStatus.NOT_FOUND, false);
    }
    BookingCalendarManager.FeedResult result = manager.feed(token, locale, new Date());
    if (result instanceof BookingCalendarManager.Available available) {
      HttpHeaders headers = privateHeaders();
      headers.setContentType(CALENDAR);
      headers.setContentDisposition(
          ContentDisposition.inline().filename("rspace-bookings.ics").build());
      return new ResponseEntity<>(available.body(), headers, HttpStatus.OK);
    }
    if (result instanceof BookingCalendarManager.NotFound) {
      return empty(HttpStatus.NOT_FOUND, false);
    }
    return empty(
        HttpStatus.SERVICE_UNAVAILABLE, result instanceof BookingCalendarManager.AtCapacity);
  }

  private static String exactToken(MultiValueMap<String, String> parameters) {
    if (parameters.size() != 1 || !parameters.containsKey("token")) {
      return null;
    }
    List<String> values = parameters.get("token");
    if (values == null || values.size() != 1) {
      return null;
    }
    String value = values.get(0);
    return value != null && TOKEN.matcher(value).matches() ? value : null;
  }

  private static ResponseEntity<byte[]> empty(HttpStatus status, boolean retryable) {
    HttpHeaders headers = privateHeaders();
    if (retryable) {
      headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(RETRY_AFTER_SECONDS));
    }
    return new ResponseEntity<>(EMPTY, headers, status);
  }

  private static HttpHeaders privateHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
    headers.set("Referrer-Policy", "no-referrer");
    return headers;
  }
}

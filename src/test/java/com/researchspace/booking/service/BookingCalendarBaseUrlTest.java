package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingCalendarBaseUrlTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://rspace.example.org",
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        "http://[::1]:8080"
      })
  void acceptsHttpsAndLoopbackHttp(String configured) {
    assertEquals(URI.create(configured), BookingCalendarManagerImpl.validatedBaseUrl(configured));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://rspace.example.org",
        "https://user@rspace.example.org",
        "https://rspace.example.org/?q=1",
        "https://rspace.example.org/#x",
        "rspace.example.org"
      })
  void rejectsUnsafeBaseUrls(String configured) {
    assertThrows(
        IllegalArgumentException.class,
        () -> BookingCalendarManagerImpl.validatedBaseUrl(configured));
  }
}

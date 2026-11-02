package com.researchspace.webapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.service.BookingCalendarManager;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingCalendarFeedControllerTest {

  private static final String TOKEN = "A".repeat(43);

  private BookingCalendarManager manager;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    manager = mock(BookingCalendarManager.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new BookingCalendarFeedController(manager)).build();
  }

  @Test
  void availableFeedIsAnUndecoratedPrivateCalendar() throws Exception {
    byte[] calendar = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n".getBytes(StandardCharsets.UTF_8);
    when(manager.feed(eq(TOKEN), any(Locale.class), any(Date.class)))
        .thenReturn(new BookingCalendarManager.Available(calendar));

    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).param("token", TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/calendar;charset=UTF-8"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"rspace-bookings.ics\""))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(content().bytes(calendar));
  }

  @Test
  void rejectsAnythingOtherThanOneExactTokenParameterBeforeCallingTheManager() throws Exception {
    List<MockHttpServletRequestBuilder> invalidRequests =
        List.of(
            get(BookingCalendarFeedController.PATH),
            get(BookingCalendarFeedController.PATH).param("token", TOKEN, TOKEN),
            get(BookingCalendarFeedController.PATH).param("token", TOKEN).param("extra", "x"),
            get(BookingCalendarFeedController.PATH).param("token", "short"));
    for (MockHttpServletRequestBuilder request : invalidRequests) {
      mockMvc.perform(request).andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    verifyNoInteractions(manager);
  }

  @Test
  void missingCapacityAndUnavailableResultsHaveEmptyPrivateResponses() throws Exception {
    when(manager.feed(eq(TOKEN), any(Locale.class), any(Date.class)))
        .thenReturn(new BookingCalendarManager.NotFound())
        .thenReturn(new BookingCalendarManager.AtCapacity())
        .thenReturn(new BookingCalendarManager.Oversized());

    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).param("token", TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(content().string(""));
    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).param("token", TOKEN))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(content().string(""));
    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).param("token", TOKEN))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(content().string(""));
  }

  @Test
  void unexpectedFeedFailuresDoNotExposeTheException() throws Exception {
    when(manager.feed(eq(TOKEN), any(Locale.class), any(Date.class)))
        .thenThrow(new IllegalStateException("internal calendar details"));

    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).param("token", TOKEN))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(content().string(""));
  }
}

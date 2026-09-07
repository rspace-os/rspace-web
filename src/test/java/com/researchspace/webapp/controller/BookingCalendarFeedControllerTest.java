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
import java.util.Date;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingCalendarFeedControllerTest {

  private static final String PATH = "/public/booking/calendars/feed.ics";
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
    byte[] calendar =
        "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(manager.feed(eq(TOKEN), any(Locale.class), any(Date.class)))
        .thenReturn(new BookingCalendarManager.Available(calendar));

    mockMvc
        .perform(get(PATH).param("token", TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/calendar;charset=UTF-8"))
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
    mockMvc.perform(get(PATH)).andExpect(status().isNotFound()).andExpect(content().string(""));
    mockMvc
        .perform(get(PATH).param("token", TOKEN, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
    mockMvc
        .perform(get(PATH).param("token", TOKEN).param("extra", "x"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
    mockMvc
        .perform(get(PATH).param("token", "short"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));

    verifyNoInteractions(manager);
  }

  @Test
  void missingCapacityAndUnavailableResultsHaveEmptyPrivateResponses() throws Exception {
    when(manager.feed(eq(TOKEN), any(Locale.class), any(Date.class)))
        .thenReturn(new BookingCalendarManager.NotFound())
        .thenReturn(new BookingCalendarManager.AtCapacity())
        .thenReturn(new BookingCalendarManager.Oversized());

    mockMvc
        .perform(get(PATH).param("token", TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(content().string(""));
    mockMvc
        .perform(get(PATH).param("token", TOKEN))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(content().string(""));
    mockMvc
        .perform(get(PATH).param("token", TOKEN))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
        .andExpect(content().string(""));
  }
}

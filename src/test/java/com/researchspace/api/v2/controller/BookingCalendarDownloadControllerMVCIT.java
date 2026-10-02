package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies the one-off calendar file: a booking in, an attachment out, and nothing else exposed.
 */
@ApiV2WebIntegrationTest
class BookingCalendarDownloadControllerMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private InstrumentEntityApiManager instrumentManager;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }

  @Test
  void downloadRequiresAuthentication() throws Exception {
    mockMvc.perform(get(path(1))).andExpect(status().isUnauthorized());
  }

  @Test
  void confirmedBookingIsDeliveredAsOneNamedCalendarAttachment() throws Exception {
    User owner = fixture.user();
    String itemName = "Confocal Microscope " + fixture.marker();
    long instrumentId = fixture.instrument(owner, itemName);
    fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));

    String expectedName =
        itemName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
            + "-IN"
            + instrumentId
            + "-"
            + LocalDate.ofInstant(start, ZoneOffset.UTC)
            + ".ics";

    String body =
        mockMvc
            .perform(get(path(bookingId)).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andExpect(
                content().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        containsString("attachment; filename=\"" + expectedName + "\"")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // iCalendar folds long lines, so compare against the unfolded text.
    String unfolded = body.replace("\r\n ", "");
    assertTrue(unfolded.startsWith("BEGIN:VCALENDAR"), unfolded);
    assertTrue(unfolded.contains("END:VCALENDAR"), unfolded);
    assertTrue(unfolded.contains(itemName), unfolded);
    assertEquals(1, occurrences(unfolded, "BEGIN:VEVENT"));
  }

  @Test
  void oneBookingYieldsExactlyOneEventEvenWhenTheItemHasOthers() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, "Spectrometer " + fixture.marker());
    fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    fixture.booking(instrumentId, start.plus(3, ChronoUnit.HOURS), start.plus(4, ChronoUnit.HOURS));
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));

    String body =
        mockMvc
            .perform(get(path(bookingId)).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(1, occurrences(body, "BEGIN:VEVENT"));
  }

  @Test
  void exportRequiresCurrentItemAccessEvenForTheRequester() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, "Cryostat " + fixture.marker());
    fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));

    mockMvc
        .perform(get("/api/v2/bookings/" + bookingId).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk());

    User newOwner = fixture.otherUser();
    ApiInstrument transferred = instrumentManager.getApiInstrumentById(instrumentId, owner);
    transferred.setOwner(new ApiUser(newOwner));
    instrumentManager.changeApiInstrumentOwner(transferred, owner);

    // The requester keeps the redacted booking record but cannot export the hidden item.
    mockMvc
        .perform(get("/api/v2/bookings/" + bookingId).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isOk());
  }

  @Test
  void cancelledBookingsAreNotFound() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, "Rotary evaporator " + fixture.marker());
    fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));

    cancel(bookingId);

    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("BEGIN:VCALENDAR"))));
  }

  private void cancel(long bookingId) throws Exception {
    JsonNode booking =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/v2/bookings/" + bookingId).header("apiKey", fixture.userKey()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mockMvc
        .perform(
            patch("/api/v2/bookings/" + bookingId)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, "\"" + booking.get("version").asLong() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"CANCELLED\"}"))
        .andExpect(status().isOk());
  }

  private static int occurrences(String body, String token) {
    return body.split(token, -1).length - 1;
  }

  private static String path(long bookingId) {
    return "/api/v2/bookings/" + bookingId + "/calendar-file";
  }

  /** Booking creation rejects starts that are not on a five-minute boundary. */
  private static Instant alignedStart() {
    Instant candidate = Instant.now().plus(2, ChronoUnit.DAYS);
    return Instant.ofEpochSecond(((candidate.getEpochSecond() + 299) / 300) * 300);
  }
}

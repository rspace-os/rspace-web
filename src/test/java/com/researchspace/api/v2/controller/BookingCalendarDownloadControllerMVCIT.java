package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
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
  void exportFollowsTheConfigurationsOwnAccessRules() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, "Cryostat " + fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));

    // All users are Bookers by default, so a third party may export exactly what the booking read
    // API already shows them. The file adds no visibility of its own.
    mockMvc
        .perform(get("/api/v2/bookings/" + bookingId).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isOk());

    withoutAllUsersAccess(configurationId);

    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(path(bookingId)).header("apiKey", fixture.userKey()))
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

  /** Leaves the owner in place and takes the all-users audience down to no access. */
  private void withoutAllUsersAccess(long configurationId) throws Exception {
    String accessPath = "/api/v2/booking-configurations/" + configurationId + "/access";
    String etag =
        mockMvc
            .perform(get(accessPath).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ETAG);
    mockMvc
        .perform(
            put(accessPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"assignments":[
                      {"granteeKey":"user:%d","role":"OWNER"},
                      {"granteeKey":"audience:all-users","role":"NO_ACCESS"}
                    ]}
                    """
                        .formatted(fixture.user().getId())))
        .andExpect(status().isOk());
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

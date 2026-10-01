package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class TimeSlotBookingReadMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private TimeSlotBookingDao bookingDao;
  @Autowired private TimeSlotBookingManager bookingManager;
  @Autowired private PlatformTransactionManager transactionManager;

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
  void cancelledBookingRemainsReadableAndListableButNotConfirmed() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));
    String path = "/api/v2/bookings/" + bookingId;

    MvcResult booking =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn();
    mockMvc
        .perform(
            patch(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, booking.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"CANCELLED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"));

    mockMvc
        .perform(get(path).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"));
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param("where", "id==%d;state==CANCELLED".formatted(bookingId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1))
        .andExpect(jsonPath("$.docs[0].id").value(bookingId));
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param("where", "id==%d;state==CONFIRMED".formatted(bookingId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(0));
    withoutAllUsersAccess(configurationId);
    mockMvc
        .perform(get(path).header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isNotFound());
  }

  @Test
  void softDeletedBookingIsHiddenFromEveryReadPath() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(ignored -> bookingDao.get(bookingId).setDeleted(true));

    mockMvc
        .perform(get("/api/v2/bookings/" + bookingId).header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param("where", "id==" + bookingId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(0));
    assertTrue(bookingManager.getBookingForAudit(bookingId, fixture.user()).isEmpty());
  }

  private static Instant alignedStart() {
    Instant candidate = Instant.now().plus(7, ChronoUnit.DAYS);
    return Instant.ofEpochSecond(((candidate.getEpochSecond() + 299) / 300) * 300);
  }

  private void withoutAllUsersAccess(long configurationId) throws Exception {
    String path = "/api/v2/booking-configurations/" + configurationId + "/access";
    String etag =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ETAG);
    mockMvc
        .perform(
            put(path)
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
}

package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> bookingDao.get(bookingId).setPurpose("Acceptance UI 20260922"));

    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param(
                    "where",
                    "requesterId==%d;purpose=contains=Acceptance"
                        .formatted(fixture.user().getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1));
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param("where", "target==IN" + instrumentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1));

    MvcResult booking =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult cancelled =
        mockMvc
            .perform(
                patch(path)
                    .header("apiKey", fixture.userKey())
                    .header(HttpHeaders.IF_MATCH, booking.getResponse().getHeader(HttpHeaders.ETAG))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"state\":\"CANCELLED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("CANCELLED"))
            .andReturn();

    mockMvc
        .perform(
            patch(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, cancelled.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"CANCELLED\"}"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ETAG, cancelled.getResponse().getHeader(HttpHeaders.ETAG)))
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

  @Test
  void purposeSearchAndCountUseCurrentTargetReadAccess() throws Exception {
    var owner = fixture.user();
    fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    fixture.thirdUser();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(ignored -> bookingDao.get(bookingId).setPurpose("Hidden purpose"));

    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.otherUserKey())
                .param("where", "id==%d".formatted(bookingId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1))
        .andExpect(jsonPath("$.docs[0].purpose").value("Hidden purpose"));
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.thirdUserKey())
                .param("where", "purpose=contains=Hidden"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(0));
    mockMvc
        .perform(
            get("/api/v2/bookings/count")
                .header("apiKey", fixture.thirdUserKey())
                .param("where", "purpose=contains=Hidden"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(0));
  }

  private static Instant alignedStart() {
    Instant candidate = Instant.now().plus(7, ChronoUnit.DAYS);
    return Instant.ofEpochSecond(((candidate.getEpochSecond() + 299) / 300) * 300);
  }

  @Test
  void inheritsAccessAndRedactsOwnBookingAfterInventoryTransfer() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    long bookingId = fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));
    String accessPath = "/api/v2/booking-configurations/" + configurationId + "/access";
    var access =
        mockMvc
            .perform(get(accessPath).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inherited").value(true))
            .andExpect(jsonPath("$.assignments.length()").value(0))
            .andReturn();
    mockMvc
        .perform(
            put(accessPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, access.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignments\":[]}"))
        .andExpect(status().isForbidden());

    var calendar = context.getBean(com.researchspace.booking.service.BookingCalendarManager.class);
    var created =
        calendar.createOrRotate(configurationId, fixture.user(), fixture.user(), "\"inactive\"");
    String token =
        java.net.URI.create(created.subscriptionUrl()).getRawQuery().substring("token=".length());
    var instruments =
        context.getBean(com.researchspace.service.inventory.InstrumentEntityApiManager.class);
    var item = instruments.getApiInstrumentById(instrumentId, fixture.user());
    item.setOwner(new com.researchspace.api.v1.model.ApiUser(fixture.otherUser()));
    instruments.changeApiInstrumentOwner(item, fixture.user());

    String path = "/api/v2/bookings/" + bookingId;
    var retained =
        mockMvc
            .perform(get(path).header("apiKey", fixture.userKey()).param("depth", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target").isEmpty())
            .andExpect(jsonPath("$.timezone").isEmpty())
            .andExpect(jsonPath("$.canViewConfiguration").value(false))
            .andExpect(jsonPath("$.canEdit").value(false))
            .andExpect(jsonPath("$.canCancel").value(false))
            .andReturn();
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param("where", "id==" + bookingId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1));
    mockMvc
        .perform(
            get("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .param("where", "id==" + bookingId + ";target.name==" + fixture.marker()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(0));
    mockMvc
        .perform(
            get("/api/v2/booking-configurations/" + configurationId)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            patch(path)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, retained.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"CANCELLED\"}"))
        .andExpect(status().isNotFound());

    item = instruments.getApiInstrumentById(instrumentId, fixture.otherUser());
    item.setOwner(new com.researchspace.api.v1.model.ApiUser(fixture.user()));
    instruments.changeApiInstrumentOwner(item, fixture.otherUser());
    org.junit.jupiter.api.Assertions.assertInstanceOf(
        com.researchspace.booking.service.BookingCalendarManager.NotFound.class,
        calendar.feed(token, java.util.Locale.UK, new java.util.Date()));
  }
}

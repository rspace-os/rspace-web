package com.researchspace.api.v2.contract;

import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningHoursCodec;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** Independent weekday hours through HTTP, persistence and Envers history. */
@ApiV2WebIntegrationTest
class BookingOpeningWeekdaysMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private InstrumentEntityApiManager instrumentManager;

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
  void freshSchemaDefaultsSevenIndependentFullDays() {
    assertEquals(
        BookingSchedulingSettings.DEFAULT_OPENING_HOURS,
        BookingOpeningHoursCodec.openingHours(
            jdbcTemplate.queryForObject(
                "select openingHours from BookingConfigurationDefaults where id = 1",
                String.class)));
  }

  @Test
  void patchesReplaceTheWholeWeekAndRejectedWritesLeaveRowsVersionsAndHistoryUnchanged()
      throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = "/api/v2/booking-configurations/" + id;
    List<BookingOpeningHoursEntry> replacement =
        List.of(
            new BookingOpeningHoursEntry(1, "09:00", "17:00"),
            new BookingOpeningHoursEntry(6, "10:00", "16:00"));
    patchConfiguration(
            path,
            "\"0\"",
            """
            {"openingHours":[{"dayOfWeek":6,"start":"10:00","end":"16:00"},
                             {"dayOfWeek":1,"start":"09:00","end":"17:00"}]}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours.length()").value(2))
        .andExpect(jsonPath("$.openingHours[0].dayOfWeek").value(1))
        .andExpect(jsonPath("$.openingHours[1].start").value("10:00"))
        .andExpect(jsonPath("$.configurationVersion").value(1))
        .andExpect(jsonPath("$.openingStart").doesNotExist())
        .andExpect(jsonPath("$.openDays").doesNotExist());

    for (String invalid :
        List.of(
            "{\"openingHours\":null}",
            "{\"openingHours\":[]}",
            "{\"openingHours\":[1]}",
            "{\"openingHours\":[{\"dayOfWeek\":\"1\",\"start\":\"09:00\",\"end\":\"17:00\"}]}",
            "{\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"09:00\"}]}",
            "{\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\",\"note\":\"x\"}]}",
            "{\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\"},"
                + "{\"dayOfWeek\":1,\"start\":\"10:00\",\"end\":\"16:00\"}]}",
            "{\"openingStart\":\"08:00\"}",
            "{\"openDays\":[1]}")) {
      patchConfiguration(path, "\"1\"", invalid).andExpect(status().isBadRequest());
    }
    assertEquals(replacement, storedHours(id));
    assertEquals(
        Long.valueOf(1),
        jdbcTemplate.queryForObject(
            "select configurationVersion from BookingConfiguration where id = ?", Long.class, id));
    assertEquals(Integer.valueOf(2), auditCount(id));

    patchConfiguration(path, "\"1\"", "{\"bufferAfterMinutes\":5}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours.length()").value(2));
    assertEquals(replacement, storedHours(id));
    patchConfiguration(
            path,
            "\"2\"",
            "{\"openingHours\":[{\"dayOfWeek\":3,\"start\":\"00:00\",\"end\":\"24:00\"}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours.length()").value(1));
    assertEquals(hours("00:00", "24:00", 3), storedHours(id));

    mockMvc
        .perform(
            delete(path)
                .queryParam("permanent", "true")
                .header("apiKey", fixture.sysadminKey())
                .header(HttpHeaders.IF_MATCH, "\"3\""))
        .andExpect(status().isNoContent());
    try (Session session = sessionFactory.openSession()) {
      List<?> revisions =
          AuditReaderFactory.get(session)
              .createQuery()
              .forRevisionsOfEntity(BookingConfiguration.class, false, true)
              .add(org.hibernate.envers.query.AuditEntity.id().eq(id))
              .addOrder(org.hibernate.envers.query.AuditEntity.revisionNumber().asc())
              .getResultList();
      assertEquals(5, revisions.size());
      assertEquals(
          replacement, ((BookingConfiguration) ((Object[]) revisions.get(1))[0]).getOpeningHours());
      Object[] deletion = (Object[]) revisions.get(4);
      assertEquals(RevisionType.DEL, deletion[2]);
      assertNull(((BookingConfiguration) deletion[0]).getOpeningHours());
    }
  }

  @Test
  void partialDaysMayCloseAtMidnightAndBookingsMayEndThereButNotLater() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String patched =
        patchConfiguration(
                "/api/v2/booking-configurations/" + id,
                "\"0\"",
                "{\"openingHours\":"
                    + BookingOpeningHoursCodec.openingHoursJson(hours("18:00", "24:00"))
                    + "}")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ZoneId zone = ZoneId.of(JsonPath.read(patched, "$.timezone"));
    LocalDate day = LocalDate.now(zone).plusDays(7);
    fixture.booking(
        instrumentId,
        day.atTime(22, 0).atZone(zone).toInstant(),
        day.plusDays(1).atStartOfDay(zone).toInstant());
    mockMvc
        .perform(
            post("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"target\":{\"relationTo\":\"booking-instruments\",\"value\":%d},\"start\":\"%s\",\"end\":\"%s\"}"
                        .formatted(
                            instrumentId,
                            day.plusDays(1).atTime(22, 0).atZone(zone).toInstant(),
                            day.plusDays(2).atTime(0, 15).atZone(zone).toInstant())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.booking.openingHours"));
  }

  @Test
  void createCopiesDefaultsWhenOmittedAndRejectsExplicitNull() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    fixture.enableBookings();
    mockMvc
        .perform(
            post("/api/v2/booking-configurations")
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"openingHours\":null,\"target\":{\"relationTo\":\"booking-instruments\",\"value\":%d}}"
                        .formatted(instrumentId)))
        .andExpect(status().isBadRequest());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    assertEquals(
        BookingOpeningHoursCodec.openingHours(
            jdbcTemplate.queryForObject(
                "select openingHours from BookingConfigurationDefaults where id = 1",
                String.class)),
        storedHours(id));
  }

  @Test
  void schedulingPermissionsStillGateIndependentHours() throws Exception {
    User owner = fixture.user();
    fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    fixture.thirdUser();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = "/api/v2/booking-configurations/" + id;
    List<BookingOpeningHoursEntry> original = storedHours(id);
    for (String role : List.of("BOOKER", "VIEWER")) {
      if (role.equals("VIEWER")) {
        ApiInstrument instrument = instrumentManager.getApiInstrumentById(instrumentId, owner);
        instrument.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
        instrumentManager.updateApiInstrument(instrument, owner);
      }
      mockMvc
          .perform(get(path).header("apiKey", fixture.otherUserKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.effectiveRole").value(role))
          .andExpect(jsonPath("$.capabilities.canEditConfiguration").value(false));
      String body = "{\"openingHours\":[{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\"}]}";
      patchConfigurationAs(fixture.otherUserKey(), path, "\"0\"", body)
          .andExpect(status().isForbidden());
      patchConfigurationAs(fixture.thirdUserKey(), path, "\"0\"", body)
          .andExpect(status().isNotFound());
    }
    assertEquals(original, storedHours(id));
    assertEquals(
        Long.valueOf(0),
        jdbcTemplate.queryForObject(
            "select configurationVersion from BookingConfiguration where id = ?", Long.class, id));
    assertEquals(Integer.valueOf(1), auditCount(id));
  }

  private Integer auditCount(long id) {
    return jdbcTemplate.queryForObject(
        "select count(*) from BookingConfiguration_AUD where id = ?", Integer.class, id);
  }

  private List<BookingOpeningHoursEntry> storedHours(long id) {
    return BookingOpeningHoursCodec.openingHours(
        jdbcTemplate.queryForObject(
            "select openingHours from BookingConfiguration where id = ?", String.class, id));
  }

  private org.springframework.test.web.servlet.ResultActions patchConfiguration(
      String path, String etag, String body) throws Exception {
    return patchConfigurationAs(fixture.userKey(), path, etag, body);
  }

  private org.springframework.test.web.servlet.ResultActions patchConfigurationAs(
      String apiKey, String path, String etag, String body) throws Exception {
    return mockMvc.perform(
        patch(path)
            .header("apiKey", apiKey)
            .header(HttpHeaders.IF_MATCH, etag)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}

package com.researchspace.api.v2.contract;

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
import com.researchspace.model.booking.BookingOpeningException;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

/** Open weekdays and opening exceptions through the collection API, schema and Envers history. */
@ApiV2WebIntegrationTest
class BookingOpeningWeekdaysMVCIT {

  private static final String EXCEPTION = "{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\"}";

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
  void freshSchemaDefaultsSevenOpenDaysAndNoExceptions() {
    Map<String, Object> defaults =
        jdbcTemplate.queryForMap(
            "select openDays, openingExceptions from BookingConfigurationDefaults where id = 1");

    assertEquals("[1,2,3,4,5,6,7]", defaults.get("openDays"));
    assertEquals("[]", defaults.get("openingExceptions"));
  }

  @Test
  void patchesReplaceWholeListsAndInvalidPatchesWriteNothing() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = "/api/v2/booking-configurations/" + id;

    mockMvc
        .perform(get(path).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.openDays").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6, 7)))
        .andExpect(jsonPath("$.openingExceptions").isEmpty());

    patchConfiguration(
            path, "\"0\"", "{\"openDays\":[6,5,4,3,2,1],\"openingExceptions\":[" + EXCEPTION + "]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openDays").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6)))
        .andExpect(jsonPath("$.openingExceptions[0].dayOfWeek").value(6))
        .andExpect(jsonPath("$.openingExceptions[0].start").value("10:00"))
        .andExpect(jsonPath("$.openingExceptions[0].end").value("16:00"))
        .andExpect(jsonPath("$.configurationVersion").value(1));

    for (String invalid :
        List.of(
            "{\"openDays\":[1,2,3,4,5]}",
            "{\"openDays\":[\"1\"]}",
            "{\"openDays\":null}",
            "{\"openDays\":[]}",
            "{\"openingExceptions\":[{\"dayOfWeek\":7,\"start\":\"10:00\",\"end\":\"16:00\"}]}",
            "{\"openingExceptions\":[{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\","
                + "\"note\":\"x\"}]}")) {
      patchConfiguration(path, "\"1\"", invalid).andExpect(status().isBadRequest());
    }
    patchConfiguration(path, "\"1\"", "{\"openDays\":[1,2,3,4,5]}")
        .andExpect(
            jsonPath("$.code")
                .value("errors.api.v2.bookingConfiguration.openingExceptions.invalid"));

    patchConfiguration(path, "\"1\"", "{\"openingStart\":\"08:00\",\"openingEnd\":\"18:00\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openDays").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6)))
        .andExpect(jsonPath("$.openingExceptions[0].dayOfWeek").value(6))
        .andExpect(jsonPath("$.configurationVersion").value(2));
    patchConfiguration(path, "\"2\"", "{\"openDays\":[1,2,3,4,5],\"openingExceptions\":[]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingExceptions").isEmpty());

    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "select openDays, openingExceptions from BookingConfiguration where id = ?", id);
    assertEquals("[1,2,3,4,5]", stored.get("openDays"));
    assertEquals("[]", stored.get("openingExceptions"));
    List<String> auditedDays =
        jdbcTemplate.queryForList(
            "select openDays from BookingConfiguration_AUD where id = ? order by REV",
            String.class,
            id);
    assertEquals(
        List.of("[1,2,3,4,5,6,7]", "[1,2,3,4,5,6]", "[1,2,3,4,5,6]", "[1,2,3,4,5]"), auditedDays);

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
      BookingConfiguration second = (BookingConfiguration) ((Object[]) revisions.get(1))[0];
      assertEquals(List.of(1, 2, 3, 4, 5, 6), second.getOpenDays());
      assertEquals(
          List.of(new BookingOpeningException(6, "10:00", "16:00")), second.getOpeningExceptions());
      Object[] deletion = (Object[]) revisions.get(4);
      assertEquals(RevisionType.DEL, deletion[2]);
      assertNull(((BookingConfiguration) deletion[0]).getOpenDays());
    }
  }

  @Test
  void aPartialDayMayCloseAtMidnightAndBookingsMayEndThereButNotLater() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = "/api/v2/booking-configurations/" + id;

    String patched =
        patchConfiguration(
                path,
                "\"0\"",
                "{\"openingStart\":\"18:00\",\"openingEnd\":\"24:00\",\"openingExceptions\":"
                    + "[{\"dayOfWeek\":6,\"start\":\"20:00\",\"end\":\"24:00\"}]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openingStart").value("18:00"))
            .andExpect(jsonPath("$.openingEnd").value("24:00"))
            .andExpect(jsonPath("$.openingExceptions[0].start").value("20:00"))
            .andExpect(jsonPath("$.openingExceptions[0].end").value("24:00"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The configuration uses the institution time zone; times are local midnights in it.
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
                    "{\"target\":{\"relationTo\":\"booking-instruments\",\"value\":%d},"
                            .formatted(instrumentId)
                        + "\"start\":\"%s\",\"end\":\"%s\"}"
                            .formatted(
                                day.plusDays(1).atTime(22, 0).atZone(zone).toInstant(),
                                day.plusDays(2).atTime(0, 5).atZone(zone).toInstant())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.booking.openingHours"));
  }

  @Test
  void createCopiesDefaultsWhenOmittedAndRejectsExplicitNull() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    fixture.enableBookings();

    mockMvc
        .perform(
            post("/api/v2/booking-configurations")
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"openDays\":null,\"target\":{\"relationTo\":\"booking-instruments\","
                        + "\"value\":"
                        + instrumentId
                        + "}}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v2/booking-configurations")
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"openDays\":[1],\"openingExceptions\":["
                        + EXCEPTION
                        + "],"
                        + "\"target\":{\"relationTo\":\"booking-instruments\",\"value\":"
                        + instrumentId
                        + "}}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.code")
                .value("errors.api.v2.bookingConfiguration.openingExceptions.invalid"));
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());

    mockMvc
        .perform(get("/api/v2/booking-configurations/" + id).header("apiKey", fixture.userKey()))
        .andExpect(
            jsonPath("$.openDays").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6, 7)))
        .andExpect(jsonPath("$.openingExceptions").isEmpty());
  }

  @Test
  void usersWithoutTheSchedulingCapabilityCannotChangeOpenDaysOrExceptions() throws Exception {
    User owner = fixture.user();
    fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    fixture.thirdUser();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = "/api/v2/booking-configurations/" + id;

    // The owner's PI can edit the default-shared instrument (BOOKER); once the owner restricts
    // sharing, the PI can only read it (VIEWER). Neither holds the scheduling capability.
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
      for (String body :
          List.of(
              "{\"openDays\":[1,2,3,4,5]}",
              "{\"openingExceptions\":[" + EXCEPTION + "]}",
              "{\"openDays\":[6],\"openingExceptions\":[" + EXCEPTION + "]}")) {
        patchConfigurationAs(fixture.otherUserKey(), path, "\"0\"", body)
            .andExpect(status().isForbidden());
        patchConfigurationAs(fixture.thirdUserKey(), path, "\"0\"", body)
            .andExpect(status().isNotFound());
      }
    }

    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "select openDays, openingExceptions, configurationVersion from BookingConfiguration"
                + " where id = ?",
            id);
    assertEquals("[1,2,3,4,5,6,7]", stored.get("openDays"));
    assertEquals("[]", stored.get("openingExceptions"));
    assertEquals(0L, ((Number) stored.get("configurationVersion")).longValue());
    assertEquals(
        Integer.valueOf(1),
        jdbcTemplate.queryForObject(
            "select count(*) from BookingConfiguration_AUD where id = ?", Integer.class, id));
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

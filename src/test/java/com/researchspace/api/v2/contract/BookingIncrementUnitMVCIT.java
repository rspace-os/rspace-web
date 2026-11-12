package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

/** Increment unit through the collection API, storage and Envers history. */
@ApiV2WebIntegrationTest
class BookingIncrementUnitMVCIT {

  private final WebApplicationContext context;
  private final JdbcTemplate jdbcTemplate;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @Autowired
  BookingIncrementUnitMVCIT(WebApplicationContext context, JdbcTemplate jdbcTemplate) {
    this.context = context;
    this.jdbcTemplate = jdbcTemplate;
  }

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
  void freshSchemaDefaultsToFifteenMinutes() {
    Map<String, Object> defaults =
        jdbcTemplate.queryForMap(
            "select slotGranularityMinutes, incrementUnit"
                + " from BookingConfigurationDefaults where id = 1");

    assertEquals(15L, ((Number) defaults.get("slotGranularityMinutes")).longValue());
    assertEquals("MINUTES", defaults.get("incrementUnit"));
  }

  @Test
  void twentyFourHoursAndOneDayStayDistinctAndOmittedFieldsKeepTheirValues() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String path = "/api/v2/booking-configurations/" + id;

    mockMvc
        .perform(get(path).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slotGranularityMinutes").value(15))
        .andExpect(jsonPath("$.incrementUnit").value("MINUTES"));

    patchConfiguration(
            path, "\"0\"", "{\"slotGranularityMinutes\":1440,\"incrementUnit\":\"HOURS\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slotGranularityMinutes").value(1440))
        .andExpect(jsonPath("$.incrementUnit").value("HOURS"));
    mockMvc
        .perform(get(path).header("apiKey", fixture.userKey()))
        .andExpect(jsonPath("$.incrementUnit").value("HOURS"));

    patchConfiguration(path, "\"1\"", "{\"incrementUnit\":\"DAYS\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slotGranularityMinutes").value(1440))
        .andExpect(jsonPath("$.incrementUnit").value("DAYS"));
    patchConfiguration(path, "\"2\"", "{\"allowDoubleBooking\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incrementUnit").value("DAYS"));

    for (String invalid :
        List.of(
            "{\"incrementUnit\":\"WEEKS\"}",
            "{\"incrementUnit\":1}",
            "{\"slotGranularityMinutes\":720}",
            "{\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"00:00\",\"end\":\"24:00\"}]}",
            "{\"maxBookingDurationMinutes\":1380}")) {
      patchConfiguration(path, "\"3\"", invalid).andExpect(status().isBadRequest());
    }
    patchConfiguration(
            path,
            "\"3\"",
            "{\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"06:00\",\"end\":\"24:00\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.code").value("errors.api.v2.bookingConfiguration.granularity.invalid"));

    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "select slotGranularityMinutes, incrementUnit, configurationVersion"
                + " from BookingConfiguration where id = ?",
            id);
    assertEquals(1440L, ((Number) stored.get("slotGranularityMinutes")).longValue());
    assertEquals("DAYS", stored.get("incrementUnit"));
    assertEquals(3L, ((Number) stored.get("configurationVersion")).longValue());
    assertEquals(
        List.of("MINUTES", "HOURS", "DAYS", "DAYS"),
        jdbcTemplate.queryForList(
            "select incrementUnit from BookingConfiguration_AUD where id = ? order by REV",
            String.class,
            id));
  }

  @Test
  void dayBookingsEndAtTheStartTimeAWholeNumberOfDaysLater() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long id = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    patchConfiguration(
            "/api/v2/booking-configurations/" + id,
            "\"0\"",
            "{\"slotGranularityMinutes\":1440,\"incrementUnit\":\"DAYS\"}")
        .andExpect(status().isOk());
    LocalDate day = LocalDate.now(ZoneOffset.UTC).plusDays(7);

    fixture.booking(
        instrumentId,
        day.atTime(9, 7).toInstant(ZoneOffset.UTC),
        day.plusDays(2).atTime(9, 7).toInstant(ZoneOffset.UTC));
    mockMvc
        .perform(
            post("/api/v2/bookings")
                .header("apiKey", fixture.userKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"target\":{\"relationTo\":\"instruments\",\"value\":%d},"
                            .formatted(instrumentId)
                        + "\"start\":\"%s\",\"end\":\"%s\"}"
                            .formatted(
                                day.plusDays(3).atTime(9, 0).toInstant(ZoneOffset.UTC),
                                day.plusDays(3).atTime(10, 0).toInstant(ZoneOffset.UTC))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.booking.granularity"));
  }

  private ResultActions patchConfiguration(String path, String etag, String body) throws Exception {
    return mockMvc.perform(
        patch(path)
            .header("apiKey", fixture.userKey())
            .header(HttpHeaders.IF_MATCH, etag)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}

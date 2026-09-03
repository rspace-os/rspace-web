package com.researchspace.api.v2.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
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
import org.springframework.web.context.WebApplicationContext;

/** HTTP acceptance contract for the Active/Archived booking-configuration lifecycle. */
@ApiV2WebIntegrationTest
class BookingConfigurationLifecycleMVCIT {

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
  void archiveRestoreAndPermanentDeleteHonorStateVersionAndActorRules() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    String configurationPath = "/api/v2/booking-configurations/" + configurationId;

    MvcResult active =
        mockMvc
            .perform(get(configurationPath).header("apiKey", fixture.userKey()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ACTIVE"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
            .andReturn();

    long bookingId = createBooking(instrumentId);
    mockMvc
        .perform(
            post(configurationPath + "/calendar-subscription").header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(
            patch(configurationPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, active.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"ARCHIVED\"}"))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            delete(configurationPath)
                .queryParam("permanent", "true")
                .header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isPreconditionRequired());
    mockMvc
        .perform(
            post(configurationPath + "/archive")
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, active.getResponse().getHeader(HttpHeaders.ETAG)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(configurationPath + "/unarchive")
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, active.getResponse().getHeader(HttpHeaders.ETAG)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(delete(configurationPath).header("apiKey", fixture.userKey()))
        .andExpect(status().isPreconditionRequired());
    mockMvc
        .perform(
            delete(configurationPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, "\"99\""))
        .andExpect(status().isPreconditionFailed());
    mockMvc
        .perform(
            delete(configurationPath)
                .header("apiKey", fixture.otherUserKey())
                .header(HttpHeaders.IF_MATCH, active.getResponse().getHeader(HttpHeaders.ETAG)))
        .andExpect(status().isForbidden());

    MvcResult archived =
        mockMvc
            .perform(
                delete(configurationPath)
                    .header("apiKey", fixture.userKey())
                    .header(HttpHeaders.IF_MATCH, active.getResponse().getHeader(HttpHeaders.ETAG)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ARCHIVED"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
            .andReturn();

    mockMvc
        .perform(
            delete(configurationPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, archived.getResponse().getHeader(HttpHeaders.ETAG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ARCHIVED"))
        .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

    mockMvc
        .perform(get(configurationPath).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ARCHIVED"));
    mockMvc
        .perform(
            get(configurationPath + "/calendar-subscription").header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    mockMvc
        .perform(
            post(configurationPath + "/calendar-subscription").header("apiKey", fixture.userKey()))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch(configurationPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, archived.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            patch(configurationPath)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, archived.getResponse().getHeader(HttpHeaders.ETAG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"ACTIVE\",\"enabled\":false}"))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            patch("/api/v2/bookings/" + bookingId)
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"CANCELLED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CANCELLED"));

    MvcResult restored =
        mockMvc
            .perform(
                patch(configurationPath)
                    .header("apiKey", fixture.userKey())
                    .header(
                        HttpHeaders.IF_MATCH, archived.getResponse().getHeader(HttpHeaders.ETAG))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"state\":\"ACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ACTIVE"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
            .andReturn();

    mockMvc
        .perform(
            delete(configurationPath)
                .queryParam("permanent", "true")
                .header("apiKey", fixture.userKey())
                .header(HttpHeaders.IF_MATCH, restored.getResponse().getHeader(HttpHeaders.ETAG)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete(configurationPath)
                .queryParam("permanent", "true")
                .header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isPreconditionRequired());
    mockMvc
        .perform(
            delete(configurationPath)
                .queryParam("permanent", "true")
                .header("apiKey", fixture.sysadminKey())
                .header(HttpHeaders.IF_MATCH, "\"99\""))
        .andExpect(status().isPreconditionFailed());
    mockMvc
        .perform(
            delete(configurationPath)
                .queryParam("permanent", "true")
                .header("apiKey", fixture.sysadminKey())
                .header(HttpHeaders.IF_MATCH, restored.getResponse().getHeader(HttpHeaders.ETAG)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(configurationPath).header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isNotFound());
  }

  private long createBooking(long instrumentId) throws Exception {
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    String body =
        """
        {"target":{"relationTo":"booking-instruments","value":%d},"start":"%s","end":"%s"}
        """
            .formatted(instrumentId, start, start.plus(1, ChronoUnit.HOURS));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v2/bookings")
                    .header("apiKey", fixture.userKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsByteArray())
        .path("id")
        .longValue();
  }
}

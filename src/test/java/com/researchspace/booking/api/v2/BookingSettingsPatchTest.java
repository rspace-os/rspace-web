package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2PreHandlerProblemResolver;
import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.booking.api.v2.BookingSettingsController.SettingsPatch;
import com.researchspace.booking.service.BookingConfigurationDefaultsManager;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.resourceaccess.ResourceAccessDirectoryManager;
import com.researchspace.webapp.config.WebConfig.YamlJackson2HttpMessageConverter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;

class BookingSettingsPatchTest {

  private static final String PATH = "/api/v2/booking-settings/admin";

  private final ObjectMapper mapper = new ObjectMapper();
  private final BookingConfigurationDefaultsManager manager =
      mock(BookingConfigurationDefaultsManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final ResourceAccessDirectoryManager directoryManager =
      mock(ResourceAccessDirectoryManager.class);
  private final User user = mock(User.class);
  private final StaticMessageSource messageSource = new StaticMessageSource();
  private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    messageSource.addMessage("errors.api.v2.invalidRequest", Locale.ENGLISH, "Invalid request");
    messageSource.addMessage(
        "errors.api.v2.bookingConfiguration.ifMatchRequired",
        Locale.ENGLISH,
        "If-Match is required.");
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    ApiV2JsonMappers.register(converter, BookingSettingsController.SettingsPatch.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new BookingSettingsController(
                    manager, featureFlags, directoryManager, Clock.systemUTC()))
            .setMessageConverters(converter, new YamlJackson2HttpMessageConverter())
            .setControllerAdvice(
                new BookingApiV2ControllerAdvice(new MessageSourceUtils(messageSource)))
            .setValidator(validator)
            .build();

    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, user)).thenReturn(true);
    when(manager.updateDefaults(any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(new BookingConfigurationDefaults());
  }

  @Test
  void wrapsTheSettingsGranteeDirectoryInDocs() throws Exception {
    when(directoryManager.searchForSettings("ab", 20, user)).thenReturn(List.of());
    var result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/v2/booking-settings/access-grantees")
                    .param("query", "ab")
                    .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docs").isEmpty())
            .andReturn();
    assertEquals(1, mapper.readTree(result.getResponse().getContentAsString()).size());
  }

  @Test
  void disabledBookingHidesSettingsWrites() throws Exception {
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, user)).thenReturn(false);
    messageSource.addMessage("errors.api.v2.notFound", Locale.ENGLISH, "Not found.");
    mockMvc.perform(patchRequest("{}")).andExpect(status().isNotFound());
    verifyNoInteractions(manager);
  }

  @Test
  void rejectsYamlSettingsPatchBodies() throws Exception {
    messageSource.addMessage(
        "errors.api.v2.unsupportedMediaType", Locale.ENGLISH, "Unsupported media type.");
    var composite =
        mockMvc
            .getDispatcherServlet()
            .getWebApplicationContext()
            .getBean(HandlerExceptionResolverComposite.class);
    var resolvers = new ArrayList<>(composite.getExceptionResolvers());
    resolvers.add(
        0,
        new ApiV2PreHandlerProblemResolver(
            new ApiV2ControllerAdvice(new MessageSourceUtils(messageSource)), mapper));
    composite.setExceptionResolvers(resolvers);

    mockMvc
        .perform(patchRequest("configurationVersion: 0").contentType("application/x-yaml"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("errors.api.v2.unsupportedMediaType"));
    verifyNoInteractions(manager);
  }

  @Test
  void decodesTheCompleteWeekInOrderAndTreatsOmissionAndNullAsNoChange() throws Exception {
    BookingSchedulingSettings.Patch supplied =
        patch(
            """
            {"openingHours":[{"dayOfWeek":6,"start":"10:00","end":"16:00"},
                             {"dayOfWeek":1,"start":"09:00","end":"17:00"}]
            }
            """);
    assertEquals(
        List.of(
            new BookingOpeningHoursEntry(1, "09:00", "17:00"),
            new BookingOpeningHoursEntry(6, "10:00", "16:00")),
        supplied.openingHours());
    BookingSchedulingSettings current =
        BookingSchedulingSettings.from(new BookingConfigurationDefaults());
    for (String json : List.of("{}", "{\"openingHours\":null}")) {
      BookingSchedulingSettings.Patch unchanged = patch(json);
      assertTrue(unchanged.isEmpty());
      assertEquals(current, unchanged.merge(current));
    }
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    supplied.merge(current).applyTo(defaults);
    Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"));
    for (Object document :
        List.of(
            BookingSettingsController.SettingsDocument.from(defaults, clock),
            BookingSettingsController.AdminSettingsDocument.from(defaults, clock))) {
      JsonNode rendered = mapper.valueToTree(document);
      assertEquals(mapper.valueToTree(supplied.openingHours()), rendered.get("openingHours"));
      assertFalse(rendered.has("openingStart"));
      assertFalse(rendered.has("openingEnd"));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "[null]",
        "[1]",
        "{}",
        "[{\"dayOfWeek\":\"6\",\"start\":\"10:00\",\"end\":\"16:00\"}]",
        "[{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\",\"extra\":true}]",
        "[{\"dayOfWeek\":6,\"start\":\"10:00\"}]",
        "[{\"dayOfWeek\":6,\"start\":1000,\"end\":\"16:00\"}]"
      })
  void rejectsInvalidValuesBeforeJacksonCanCoerceRowChildren(String value) {
    assertEquals(
        Reason.OPENING_HOURS,
        assertThrows(
                InvalidBookingSchedulingSettingsException.class,
                () -> patch("{\"openingHours\":" + value + "}"))
            .reason());
  }

  @Test
  void strictHttpBindingRejectsUnknownFieldsAndScalarCoercions() throws Exception {
    mockMvc
        .perform(patchRequest("{\"futureField\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
        .andExpect(jsonPath("$.invalidParams[0].name").value("futureField"))
        .andExpect(jsonPath("$.invalidParams[0].reason").value("unknownField"));
    for (String body :
        List.of(
            "{\"slotGranularityMinutes\":15.5}",
            "{\"slotGranularityMinutes\":15.0}",
            "{\"timezoneMode\":0}",
            "{\"defaultSharedWith\":0}",
            "{\"allowDoubleBooking\":\"true\"}",
            "{\"slotGranularityMinutes\":\"15\"}",
            "{\"availabilityWindowStart\":1}")) {
      mockMvc
          .perform(patchRequest(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    }
    verifyNoInteractions(manager);
  }

  @Test
  void requiresIfMatchAndRejectsMalformedTagsBeforeWriting() throws Exception {
    mockMvc
        .perform(
            patchRequest("{}")
                .with(
                    request -> {
                      request.removeHeader("If-Match");
                      return request;
                    }))
        .andExpect(status().isPreconditionRequired());
    for (String tag : List.of("*", "0", "\"-1\"", "\"abc\"", "\"0\", \"1\"")) {
      mockMvc
          .perform(
              patchRequest("{}")
                  .with(
                      request -> {
                        request.removeHeader("If-Match");
                        request.addHeader("If-Match", tag);
                        return request;
                      }))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(manager);
  }

  @Test
  void rejectsBodyVersions() throws Exception {
    mockMvc
        .perform(patchRequest("{\"configurationVersion\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.invalidParams[0].name").value("configurationVersion"));
    verifyNoInteractions(manager);
  }

  @Test
  void strictHttpBindingPreservesValidPartialAndNullPatchSemantics() throws Exception {
    mockMvc
        .perform(
            patchRequest(
                """
                {"slotGranularityMinutes":15,
                 "allowDoubleBooking":false}
                """))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patchRequest(
                """
                {"slotGranularityMinutes":null,"openingHours":null,
                 "bufferBeforeMinutes":null,"bufferAfterMinutes":null,
                 "maxBookingDurationMinutes":null,"allowDoubleBooking":null,
                 "availabilityWindowStart":null,"availabilityWindowEnd":null,"timezoneMode":null,
                 "customTimezone":null,"defaultSharedWith":null,"selectedGranteeKeys":null}
                """))
        .andExpect(status().isOk());

    ArgumentCaptor<BookingSchedulingSettings.Patch> scheduling =
        ArgumentCaptor.forClass(BookingSchedulingSettings.Patch.class);
    ArgumentCaptor<BookingDisplaySettings.Patch> display =
        ArgumentCaptor.forClass(BookingDisplaySettings.Patch.class);
    verify(manager, Mockito.times(2))
        .updateDefaults(
            scheduling.capture(), display.capture(), any(), any(), eq(0L), eq(user), eq(user));

    assertEquals(15L, scheduling.getAllValues().get(0).slotGranularityMinutes());
    assertEquals(Boolean.FALSE, scheduling.getAllValues().get(0).allowDoubleBooking());
    assertFalse(scheduling.getAllValues().get(0).isEmpty());
    assertTrue(scheduling.getAllValues().get(1).isEmpty());
    assertEquals(BookingDisplaySettings.Patch.empty(), display.getAllValues().get(0));
    assertEquals(BookingDisplaySettings.Patch.empty(), display.getAllValues().get(1));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchRequest(
      String body) {
    return MockMvcRequestBuilders.patch(PATH)
        .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user))
        .header("If-Match", "\"0\"")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private BookingSchedulingSettings.Patch patch(String json) throws Exception {
    return mapper.readValue(json, SettingsPatch.class).schedulingPatch();
  }
}

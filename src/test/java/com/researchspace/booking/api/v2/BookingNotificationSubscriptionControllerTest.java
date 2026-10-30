package com.researchspace.booking.api.v2;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2PreHandlerProblemResolver;
import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.PreferenceChanges;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Preferences;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Status;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.webapp.config.WebConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;

class BookingNotificationSubscriptionControllerTest {
  private static final String DEFAULTS = "/api/v2/users/me/booking-notification-preferences";
  private static final String SUBSCRIPTIONS = "/api/v2/users/me/booking-notification-subscriptions";
  private static final String ITEM = "/api/v2/booking-configurations/12/notification-subscription";
  private final BookingNotificationSubscriptionManager manager =
      mock(BookingNotificationSubscriptionManager.class);
  private final User subject = mock(User.class);
  private final User actor = mock(User.class);
  private final ApiV2Caller caller = new ApiV2Caller(subject, actor);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
    ApiV2JsonMappers.register(
        jsonConverter,
        BookingNotificationSubscriptionController.PreferenceReplacement.class,
        BookingNotificationSubscriptionController.SubscriptionReplacement.class,
        BookingNotificationSubscriptionController.Lookup.class,
        BookingNotificationSubscriptionController.BulkReplacement.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new BookingNotificationSubscriptionController(manager))
            .setControllerAdvice(
                new ApiV2ControllerAdvice(messages()), new BookingApiV2ControllerAdvice(messages()))
            .setMessageConverters(jsonConverter, new WebConfig.YamlJackson2HttpMessageConverter())
            .build();
  }

  @Test
  void routesPreferencesToEffectiveSubjectAndRetainsActor() throws Exception {
    PreferenceChanges changes = new PreferenceChanges(false, true, true, false);
    when(manager.replacePreferences(changes, subject, actor))
        .thenReturn(new Preferences(false, true, true, false, true));
    mvc.perform(
            put(DEFAULTS)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"autoSubscribeOwnedItems\":false,\"notifyOwnActions\":true,\"notifyOnCreated\":true,"
                        + "\"notifyOnCancelled\":false}"))
        .andExpect(status().isOk())
        .andExpectAll(preferencesShape());
    verify(manager).replacePreferences(changes, subject, actor);
  }

  @Test
  void readsPreferencesInTheirPublicShape() throws Exception {
    when(manager.getPreferences(subject, actor))
        .thenReturn(new Preferences(false, true, true, false, true));
    mvc.perform(get(DEFAULTS).requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk())
        .andExpectAll(preferencesShape());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"autoSubscribeOwnedItems\":true,\"notifyOnCreated\":true,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":true,\"notifyOwnActions\":null,\"notifyOnCreated\":true,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":true,\"notifyOwnActions\":\"false\",\"notifyOnCreated\":true,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":false,\"notifyOwnActions\":true,\"notifyOnCreated\":true}",
        "{\"notifyOwnActions\":true,\"notifyOnCreated\":true,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":null,\"notifyOwnActions\":true,\"notifyOnCreated\":true,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":\"false\",\"notifyOwnActions\":true,\"notifyOnCreated\":true,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":false,\"notifyOwnActions\":true,\"notifyOnCreated\":1,\"notifyOnCancelled\":true}",
        "{\"autoSubscribeOwnedItems\":false,\"notifyOwnActions\":true,\"notifyOnCreated\":true,\"notifyOnCancelled\":\"no\"}",
        "{\"autoSubscribeOwnedItems\":true,\"notifyOwnActions\":true,\"notifyOnCreated\":true,\"notifyOnCancelled\":true,"
            + "\"emailDelivery\":false}"
      })
  void rejectsInvalidOrForgedDefaults(String json) throws Exception {
    mvc.perform(
            put(DEFAULTS)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  /** Exactly the four documented members, for Preferences(false, true, false, true). */
  private static ResultMatcher[] preferencesShape() {
    return new ResultMatcher[] {
      jsonPath("$.*", hasSize(5)),
      jsonPath("$.autoSubscribeOwnedItems").value(false),
      jsonPath("$.notifyOwnActions").value(true),
      jsonPath("$.notifyOnCreated").value(true),
      jsonPath("$.notifyOnCancelled").value(false),
      jsonPath("$.emailDelivery").value(true)
    };
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"enabled\":true}",
        "{\"enabled\":\"true\",\"version\":0}",
        "{\"enabled\":1,\"version\":0}",
        "{\"enabled\":false,\"version\":-2}",
      })
  void rejectsInvalidOrForgedItemChanges(String json) throws Exception {
    mvc.perform(
            put(ITEM)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test
  void returnsSavedAndEffectiveDeliverySeparately() throws Exception {
    Status state = new Status(12, true, 4, true, false, false);
    when(manager.get(12, subject, actor)).thenReturn(state);
    when(manager.replace(12, false, 4, subject, actor))
        .thenReturn(new Status(12, false, 5, false, false, false));
    mvc.perform(get(ITEM).requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.cancelledEnabled").value(false));
    mvc.perform(
            put(ITEM)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":4}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(5));
  }

  @Test
  void supportsBatchLookupExplicitBulkChoiceAndUnsubscribeAll() throws Exception {
    when(manager.getMany(List.of(12L, 13L), subject, actor)).thenReturn(List.of());
    mvc.perform(
            post(SUBSCRIPTIONS + "/lookup")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configurationIds\":[12,13]}"))
        .andExpect(status().isOk());
    mvc.perform(
            put(SUBSCRIPTIONS)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configurationIds\":[12,13],\"enabled\":false}"))
        .andExpect(status().isOk());
    verify(manager).replaceMany(List.of(12L, 13L), false, subject, actor);
    when(manager.unsubscribeAll(subject, actor)).thenReturn(2);
    mvc.perform(delete(SUBSCRIPTIONS).requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedCount").value(2));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"configurationIds\":[],\"enabled\":true}",
        "{\"configurationIds\":[null],\"enabled\":true}",
        "{\"configurationIds\":[0],\"enabled\":true}",
        "{\"configurationIds\":[1],\"enabled\":null}",
        "{\"configurationIds\":[1],\"enabled\":\"true\"}"
      })
  void rejectsInvalidBatch(String json) throws Exception {
    mvc.perform(
            put(SUBSCRIPTIONS)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test
  void rejectsUnknownFieldsAsFieldViolations() throws Exception {
    List<Map.Entry<MockHttpServletRequestBuilder, String>> requests =
        List.of(
            Map.entry(
                put(DEFAULTS)
                    .content(
                        "{\"autoSubscribeOwnedItems\":true,\"notifyOwnActions\":true,\"notifyOnCreated\":true,"
                            + "\"notifyOnCancelled\":true,\"userId\":99}"),
                "userId"),
            Map.entry(
                put(ITEM).content("{\"enabled\":false,\"version\":0,\"ownerId\":99}"), "ownerId"),
            Map.entry(
                post(SUBSCRIPTIONS + "/lookup")
                    .content("{\"configurationIds\":[12],\"ownerId\":99}"),
                "ownerId"),
            Map.entry(
                put(SUBSCRIPTIONS)
                    .content("{\"configurationIds\":[12],\"enabled\":true,\"ownerId\":99}"),
                "ownerId"));
    for (var request : requests) {
      mvc.perform(
              request
                  .getKey()
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
          .andExpect(jsonPath("$.invalidParams[0].name").value(request.getValue()))
          .andExpect(jsonPath("$.invalidParams[0].reason").value("unknownField"));
    }
    verifyNoInteractions(manager);
  }

  @Test
  void rejectsBooleanCoercionsAndFloatingPointVersions() throws Exception {
    for (String invalidBoolean : List.of("1", "0", "\"true\"")) {
      mvc.perform(
              put(DEFAULTS)
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"autoSubscribeOwnedItems\":" + invalidBoolean + "}"))
          .andExpect(
              result ->
                  assertInstanceOf(
                      HttpMessageNotReadableException.class, result.getResolvedException()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
          .andExpect(jsonPath("$.invalidParams[0].name").value("autoSubscribeOwnedItems"))
          .andExpect(jsonPath("$.invalidParams[0].reason").value("wrongType"));
    }
    mvc.perform(
            put(ITEM)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"version\":15.0}"))
        .andExpect(
            result ->
                assertInstanceOf(
                    HttpMessageNotReadableException.class, result.getResolvedException()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"))
        .andExpect(jsonPath("$.invalidParams[0].name").value("version"))
        .andExpect(jsonPath("$.invalidParams[0].reason").value("wrongType"));
    verifyNoInteractions(manager);
  }

  @Test
  void rejectsYamlNotificationRequestBodies() throws Exception {
    var composite =
        mvc.getDispatcherServlet()
            .getWebApplicationContext()
            .getBean(HandlerExceptionResolverComposite.class);
    var resolvers = new ArrayList<>(composite.getExceptionResolvers());
    resolvers.add(
        0,
        new ApiV2PreHandlerProblemResolver(
            new ApiV2ControllerAdvice(messages()), new ObjectMapper()));
    composite.setExceptionResolvers(resolvers);

    for (var request :
        List.of(
            put(DEFAULTS)
                .content(
                    "autoSubscribeOwnedItems: true\n"
                        + "notifyOnCreated: true\n"
                        + "notifyOnCancelled: true"),
            put(ITEM).content("enabled: true\nversion: 0"),
            post(SUBSCRIPTIONS + "/lookup").content("configurationIds: [12]"),
            put(SUBSCRIPTIONS).content("configurationIds: [12]\nenabled: true"))) {
      mvc.perform(
              request
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                  .contentType("application/x-yaml")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.code").value("errors.api.v2.unsupportedMediaType"));
    }
    verifyNoInteractions(manager);
  }

  private static MessageSourceUtils messages() {
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    return messages;
  }
}

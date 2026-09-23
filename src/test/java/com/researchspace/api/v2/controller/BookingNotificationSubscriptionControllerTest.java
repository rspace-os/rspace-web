package com.researchspace.api.v2.controller;

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

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Preferences;
import com.researchspace.booking.service.BookingNotificationSubscriptionManager.Status;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    mvc =
        MockMvcBuilders.standaloneSetup(new BookingNotificationSubscriptionController(manager))
            .setControllerAdvice(new ApiV2ControllerAdvice(mock(MessageSourceUtils.class)))
            .build();
  }

  @Test
  void routesDefaultToEffectiveSubjectAndRetainsActor() throws Exception {
    when(manager.replacePreferences(false, subject, actor)).thenReturn(new Preferences(false));
    mvc.perform(
            put(DEFAULTS)
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"autoSubscribeOwnedItems\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autoSubscribeOwnedItems").value(false));
    verify(manager).replacePreferences(false, subject, actor);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"autoSubscribeOwnedItems\":null}",
        "{\"autoSubscribeOwnedItems\":\"false\"}",
        "{\"autoSubscribeOwnedItems\":0}",
        "{\"autoSubscribeOwnedItems\":true,\"userId\":99}"
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"enabled\":true}",
        "{\"enabled\":\"true\",\"version\":0}",
        "{\"enabled\":1,\"version\":0}",
        "{\"enabled\":false,\"version\":-2}",
        "{\"enabled\":false,\"version\":0,\"ownerId\":99}"
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
}

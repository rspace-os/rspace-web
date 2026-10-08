package com.researchspace.api.v2.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingConfigurationTarget;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.model.User;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingConfigurationTargetControllerTest {

  private static final String TARGETS = "/api/v2/booking-configuration-targets";

  private final BookingConfigurationTargetManager manager =
      mock(BookingConfigurationTargetManager.class);
  private final User subject = mock(User.class);
  private final ApiV2Caller caller = new ApiV2Caller(subject, mock(User.class));
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    MessageSourceUtils messages = new MessageSourceUtils();
    messages.setMessageSource(new JsonMessageSource());
    mvc =
        MockMvcBuilders.standaloneSetup(new BookingConfigurationTargetController(manager))
            .setControllerAdvice(new ApiV2ControllerAdvice(messages))
            .build();
  }

  @Test
  void browsesTheFirstEligibleTargetsWhenNoQueryIsSupplied() throws Exception {
    when(manager.search(null, 20, subject))
        .thenReturn(List.of(new BookingConfigurationTarget(7L, "IN7", "Autoclave", false)));

    mvc.perform(get(TARGETS).requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(7))
        .andExpect(jsonPath("$[0].name").value("Autoclave"));
    verify(manager).search(null, 20, subject);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void treatsABlankQueryAsBrowsing(String query) throws Exception {
    mvc.perform(
            get(TARGETS)
                .queryParam("query", query)
                .queryParam("limit", "5")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk());
    verify(manager).search(null, 5, subject);
  }

  @Test
  void trimsANonBlankQuery() throws Exception {
    mvc.perform(
            get(TARGETS)
                .queryParam("query", "  ab ")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isOk());
    verify(manager).search("ab", 20, subject);
  }

  @Test
  void rejectsAOneCharacterQueryAndOutOfRangeLimits() throws Exception {
    mvc.perform(
            get(TARGETS)
                .queryParam("query", "x")
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.invalidRequest"));
    for (String limit : List.of("0", "51")) {
      mvc.perform(
              get(TARGETS)
                  .queryParam("limit", limit)
                  .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, caller))
          .andExpect(status().isBadRequest());
    }
    verify(manager, never()).search(anyString(), anyInt(), org.mockito.ArgumentMatchers.any());
    verify(manager, never()).search(isNull(), anyInt(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void requiresAnAuthenticatedCaller() throws Exception {
    mvc.perform(get(TARGETS)).andExpect(status().isUnauthorized());
    verifyNoInteractions(manager);
  }
}

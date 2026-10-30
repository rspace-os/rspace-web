package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingConfigurationTarget;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.model.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookingConfigurationTargetControllerTest {
  @Test
  void wrapsBoundedTargetsInDocsWithoutPaging() throws Exception {
    var manager = mock(BookingConfigurationTargetManager.class);
    var user = mock(User.class);
    when(manager.search("ab", 20, user))
        .thenReturn(List.of(new BookingConfigurationTarget(1L, "IN1", "Instrument", false)));
    var mvc =
        MockMvcBuilders.standaloneSetup(new BookingConfigurationTargetController(manager)).build();
    var result =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v2/booking-configuration-targets")
                    .param("query", "ab")
                    .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docs[0].id").value(1))
            .andReturn();
    assertEquals(1, new ObjectMapper().readTree(result.getResponse().getContentAsString()).size());
  }
}

package com.researchspace.api.v2.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.webapp.controller.MVCTestBase;
import org.junit.Test;
import org.springframework.http.MediaType;

/** Verifies anonymous collection reads and authenticated feature flag writes. */
public class FeatureFlagResourceAuthenticationMVCIT extends MVCTestBase {

  private static final String ENDPOINT = "/api/v2/feature-flags";

  @Test
  public void anonymousCallersReadBaselinesButCannotWrite() throws Exception {
    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isArray());

    mockMvc
        .perform(
            patch(ENDPOINT + "/bookingEnabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overrideValue\":true}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  public void rejectsInvalidCredentialsOnAnAnonymouslyReadableCollection() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).header("apiKey", "not-a-valid-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }
}

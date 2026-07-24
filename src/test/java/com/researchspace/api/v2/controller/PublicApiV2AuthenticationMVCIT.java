package com.researchspace.api.v2.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.api.v1.controller.API_VERSION;
import org.junit.Test;

public class PublicApiV2AuthenticationMVCIT extends API_MVC_TestBase {

  @Test
  public void configAndMaintenancesArePublicWhileOtherV2EndpointsRemainAuthenticated()
      throws Exception {
    mockMvc
        .perform(createBuilderForAnonymousGet(API_VERSION.TWO, "config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branding").exists())
        .andExpect(jsonPath("$.nextMaintenance").doesNotExist())
        .andExpect(jsonPath("$.deploymentDescription").isString())
        .andExpect(jsonPath("$.deploymentHelpEmail").isString());

    mockMvc
        .perform(createBuilderForAnonymousGet(API_VERSION.TWO, "maintenances"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isArray());

    mockMvc.perform(get("/api/v2/config/")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/maintenances/")).andExpect(status().isOk());

    mockMvc
        .perform(createBuilderForAnonymousGet(API_VERSION.TWO, "users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401));
  }
}

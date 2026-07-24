package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.properties.IPropertyHolder;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigV2ControllerTest {

  private final IPropertyHolder properties = mock(IPropertyHolder.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ConfigV2Controller controller = new ConfigV2Controller();
    ReflectionTestUtils.setField(controller, "properties", properties);
    when(properties.getDeploymentDescription()).thenReturn("");
    when(properties.getDeploymentHelpEmail()).thenReturn("");
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void returnsBrandingAndOrderedHelpLinks() throws Exception {
    LinkedHashMap<String, String> links = new LinkedHashMap<>();
    links.put("Support", "https://example.org/support");
    links.put("Training", "https://example.org/training");
    when(properties.getUiFooterUrls()).thenReturn(links);

    mockMvc
        .perform(get("/api/v2/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branding.bannerImageUrl").value("/public/banner"))
        .andExpect(jsonPath("$.helpLinks.length()").value(2))
        .andExpect(jsonPath("$.helpLinks[0].label").value("Support"))
        .andExpect(jsonPath("$.helpLinks[0].url").value("https://example.org/support"))
        .andExpect(jsonPath("$.helpLinks[1].label").value("Training"));
  }

  @Test
  void returnsEmptyHelpLinks() throws Exception {
    when(properties.getUiFooterUrls()).thenReturn(new LinkedHashMap<>());

    mockMvc
        .perform(get("/api/v2/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.helpLinks").isEmpty());
  }

  @Test
  void doesNotIncludeMaintenanceData() throws Exception {
    when(properties.getUiFooterUrls()).thenReturn(new LinkedHashMap<>());

    mockMvc
        .perform(get("/api/v2/config"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("nextMaintenance"))));
  }

  @Test
  void returnsPublicDeploymentDetailsWithoutAUserRequestAttribute() throws Exception {
    when(properties.getUiFooterUrls()).thenReturn(new LinkedHashMap<>());
    when(properties.getDeploymentDescription())
        .thenReturn("Configured for advanced research teams");
    when(properties.getDeploymentHelpEmail()).thenReturn("groups@example.com");

    mockMvc
        .perform(get("/api/v2/config"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.deploymentDescription").value("Configured for advanced research teams"))
        .andExpect(jsonPath("$.deploymentHelpEmail").value("groups@example.com"));
  }
}

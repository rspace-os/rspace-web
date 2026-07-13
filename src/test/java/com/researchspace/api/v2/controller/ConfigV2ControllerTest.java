package com.researchspace.api.v2.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.properties.IPropertyHolder;
import java.util.Calendar;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigV2ControllerTest {

  private final IPropertyHolder properties = mock(IPropertyHolder.class);
  private final MaintenanceManager maintenanceManager = mock(MaintenanceManager.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ConfigV2Controller controller = new ConfigV2Controller();
    ReflectionTestUtils.setField(controller, "properties", properties);
    ReflectionTestUtils.setField(controller, "maintenanceManager", maintenanceManager);
    when(maintenanceManager.getNextScheduledMaintenance()).thenReturn(ScheduledMaintenance.NULL);
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
  void omitsNextMaintenanceWhenNoneScheduled() throws Exception {
    when(properties.getUiFooterUrls()).thenReturn(new LinkedHashMap<>());

    mockMvc
        .perform(get("/api/v2/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextMaintenance").isEmpty());
  }

  @Test
  void includesNextMaintenanceWhenScheduled() throws Exception {
    when(properties.getUiFooterUrls()).thenReturn(new LinkedHashMap<>());
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.HOUR_OF_DAY, 2);
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(calendar.getTime(), calendar.getTime());
    maintenance.setMessage("Planned upgrade");
    when(maintenanceManager.getNextScheduledMaintenance()).thenReturn(maintenance);

    mockMvc
        .perform(get("/api/v2/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextMaintenance.startDate").exists())
        .andExpect(jsonPath("$.nextMaintenance.message").value("Planned upgrade"));
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

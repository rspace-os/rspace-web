package com.researchspace.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.apps.App;
import org.junit.jupiter.api.Test;

class IntegrationInfoTest {
  @Test
  void appToInfoNameRoundTrip() {
    String FIGSHARE_INFO_NAME = "FIGSHARE";
    String figshare = IntegrationInfo.getAppNameFromIntegrationName(FIGSHARE_INFO_NAME);
    App app = new App(figshare, "fs", true);
    assertEquals(FIGSHARE_INFO_NAME, app.toIntegrationInfoName());
  }
}

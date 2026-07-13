package com.researchspace.api.v2.controller;

import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v2.model.ApiV2Config;
import com.researchspace.api.v2.model.ApiV2Config.Branding;
import com.researchspace.api.v2.model.ApiV2Config.HelpLink;
import com.researchspace.api.v2.model.ApiV2Maintenance;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/api/v2/config")
public class ConfigV2Controller extends BaseApiController {

  @Autowired private MaintenanceManager maintenanceManager;

  @GetMapping
  public ApiV2Config getConfig() {
    List<HelpLink> helpLinks =
        properties.getUiFooterUrls().entrySet().stream()
            .map(entry -> new HelpLink(entry.getKey(), entry.getValue()))
            .toList();
    return new ApiV2Config(
        new Branding("/public/banner"),
        helpLinks,
        nextMaintenance(),
        properties.getDeploymentDescription(),
        properties.getDeploymentHelpEmail());
  }

  private ApiV2Maintenance nextMaintenance() {
    ScheduledMaintenance next = maintenanceManager.getNextScheduledMaintenance();
    return ScheduledMaintenance.NULL.equals(next) ? null : new ApiV2Maintenance(next);
  }
}

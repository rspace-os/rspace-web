package com.researchspace.api.v2.controller;

import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v1.controller.PublicApi;
import com.researchspace.api.v2.model.ApiV2Config;
import com.researchspace.api.v2.model.ApiV2Config.Branding;
import com.researchspace.api.v2.model.ApiV2Config.HelpLink;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@PublicApi
@RequestMapping("/api/v2/config")
public class ConfigV2Controller extends BaseApiController {

  @GetMapping
  public ApiV2Config getConfig() {
    List<HelpLink> helpLinks =
        properties.getUiFooterUrls().entrySet().stream()
            .map(entry -> new HelpLink(entry.getKey(), entry.getValue()))
            .toList();
    return new ApiV2Config(
        new Branding("/public/banner"),
        helpLinks,
        properties.getDeploymentDescription(),
        properties.getDeploymentHelpEmail());
  }
}

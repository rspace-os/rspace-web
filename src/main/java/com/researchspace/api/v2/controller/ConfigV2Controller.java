package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.model.ApiV2Config;
import com.researchspace.api.v2.model.ApiV2Config.Branding;
import com.researchspace.api.v2.model.ApiV2Config.HelpLink;
import com.researchspace.properties.IPropertyHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/config")
public class ConfigV2Controller {

  private final IPropertyHolder properties;

  @GetMapping
  @Operation(
      operationId = "getApiV2Config",
      summary = "Get public REST API v2 configuration",
      description = "Returns branding, help links, and deployment information.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Public deployment configuration."),
        @ApiResponse(responseCode = "429", description = "The request was throttled.")
      })
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

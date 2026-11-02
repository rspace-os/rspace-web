package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiV2Config(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "RSpace application version.",
            example = "v2.24.0")
        String version,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Deployment branding displayed by API clients.")
        Branding branding,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Deployment-specific help links.")
        List<HelpLink> helpLinks,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Human-readable description of this RSpace deployment.",
            nullable = true)
        String deploymentDescription,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Support email address for this deployment.",
            format = "email",
            nullable = true,
            example = "support@example.org")
        String deploymentHelpEmail) {

  public record Branding(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Root-relative URL of the deployment banner image.",
              format = "uri-reference",
              example = "/public/banner")
          String bannerImageUrl) {}

  public record HelpLink(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Link label shown to users.",
              example = "Help")
          String label,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Absolute or root-relative help URL.",
              format = "uri-reference")
          String url) {}
}

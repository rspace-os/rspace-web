package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    requiredProperties = {"branding", "helpLinks", "deploymentDescription", "deploymentHelpEmail"})
public record ApiV2Config(
    @Schema(description = "Deployment branding displayed by API clients.") Branding branding,
    @Schema(description = "Deployment-specific help links.") List<HelpLink> helpLinks,
    @Schema(description = "Human-readable description of this RSpace deployment.", nullable = true)
        String deploymentDescription,
    @Schema(
            description = "Support email address for this deployment.",
            format = "email",
            nullable = true,
            example = "support@example.org")
        String deploymentHelpEmail) {

  @Schema(requiredProperties = "bannerImageUrl")
  public record Branding(
      @Schema(
              description = "Root-relative URL of the deployment banner image.",
              format = "uri-reference",
              example = "/public/banner")
          String bannerImageUrl) {}

  @Schema(requiredProperties = {"label", "url"})
  public record HelpLink(
      @Schema(description = "Link label shown to users.", example = "Help") String label,
      @Schema(description = "Absolute or root-relative help URL.", format = "uri-reference")
          String url) {}
}

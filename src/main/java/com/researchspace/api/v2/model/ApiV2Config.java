package com.researchspace.api.v2.model;

import java.util.List;

public record ApiV2Config(
    Branding branding,
    List<HelpLink> helpLinks,
    String deploymentDescription,
    String deploymentHelpEmail) {

  public record Branding(String bannerImageUrl) {}

  public record HelpLink(String label, String url) {}
}

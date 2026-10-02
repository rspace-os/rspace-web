package com.researchspace.integrations.galaxy.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GalaxyAliasToServer {
  public static final GalaxyAliasToServer NONE = new GalaxyAliasToServer("", "");

  private String alias;
  private String url;

  public GalaxyAliasToServer(String alias, String url) {
    this.alias = alias;
    this.url = url;
  }

  @JsonInclude(value = Include.NON_EMPTY)
  // RSDEV-1525: read from deployment.properties, never serialized back to the browser
  @JsonProperty(access = Access.WRITE_ONLY)
  private String token;
}

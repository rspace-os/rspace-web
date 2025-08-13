package com.researchspace.integrations.galaxy.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GalaxyAliasToServer {

  private String alias;
  private String url;

  public GalaxyAliasToServer(String alias, String url) {
    this.alias = alias;
    this.url = url;
  }

  @JsonInclude(value = Include.NON_EMPTY)
  private String token;
}

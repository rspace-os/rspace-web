package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DSWRole {
  @JsonProperty private String name;
  @JsonProperty private String[] permissions;
  @JsonProperty private String uuid;
}

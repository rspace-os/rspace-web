package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties({"knowledgeModelPackage", "permissions"})
public class DSWProject {
  @JsonProperty private String createdAt;
  @JsonProperty private String description;
  @JsonProperty private boolean isTemplate;
  // @JsonProperty private String knowledgeModelPackage; // Ignore?
  @JsonProperty private String name;
  // @JsonProperty private String permissions; // NOT a string, ignore?
  @JsonProperty private String sharing;
  @JsonProperty private String state;
  @JsonProperty private String updatedAt;
  @JsonProperty private String uuid;
  @JsonProperty private String visibility;
}

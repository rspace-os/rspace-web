package com.researchspace.webapp.integrations.fieldmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldmarkNotebookMetadata {
  @JsonProperty("Age")
  private String age;

  @JsonProperty("Size")
  private String size;

  @JsonProperty("ispublic")
  private Boolean isPublic;

  @JsonProperty("isrequest")
  private Boolean isPequest;

  @JsonProperty("lead_institution")
  private String leadInstitution;

  private String name;

  @JsonProperty("notebook_version")
  private String notebook_version;

  @JsonProperty("pre_description")
  private String preDescription;

  @JsonProperty("project_lead")
  private String projectLead;

  @JsonProperty("project_status")
  private String projectStatus;

  @JsonProperty("schema_version")
  private String schemaSersion;

  private String showQRCodeButton;

  @JsonProperty("project_id")
  private String projectId;
}

package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class DSWDocumentDTO {
  @JsonProperty private String name;
  @JsonProperty private String uuid;
  @JsonProperty private String projectName;
  @JsonProperty private String projectUuid;

  public DSWDocumentDTO(DSWDocument document) {
    this.name = document.getName();
    this.uuid = document.getUuid();
    this.projectName = document.getProject().getName();
    this.projectUuid = document.getProject().getUuid();
  }
}

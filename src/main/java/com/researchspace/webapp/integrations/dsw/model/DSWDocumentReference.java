package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class DSWDocumentReference {
  @JsonProperty private String contentType;
  @JsonProperty private String url;
}

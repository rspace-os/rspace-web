package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties({"page"})
public class DSWDocuments {
  @JsonProperty private DocumentsArr _embedded;

  public DSWDocument[] getDocuments() {
    return this._embedded.documents;
  }

  @Getter
  public class DocumentsArr {
    @JsonProperty private DSWDocument[] documents;

    public DocumentsArr() {}
  }
}

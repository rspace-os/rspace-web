package com.researchspace.webapp.integrations.fieldmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FieldmarkApiImportRequest {

  @JsonProperty(required = true)
  private String notebookId;

  private String identifier;

  public FieldmarkApiImportRequest(FieldmarkApiImportRequest fieldmarkImportRequest) {
    this.notebookId = fieldmarkImportRequest.getNotebookId();
    this.identifier = fieldmarkImportRequest.getIdentifier();
  }

  public FieldmarkApiImportRequest(String notebookId) {
    this.notebookId = notebookId;
  }
}

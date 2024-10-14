package com.researchspace.webapp.integrations.fieldmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldmarkNotebook {

  private String name;
  private String status;

  @JsonProperty("project_id")
  private String projectId;

  @JsonProperty("listing_id")
  private String listingId;

  @JsonProperty("non_unique_project_id")
  private String nonUniqueProjectId;

  private FieldmarkNotebookMetadata metadata;
}

package com.researchspace.webapp.integrations.fieldmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldmarkRecord {

  @JsonProperty("project_id")
  private String projectId;

  @JsonProperty("record_id")
  private String recordId;

  @JsonProperty("revision_id")
  private String revisionId;

  private String type;
  private Map<String, Object> data;

  @JsonProperty("updated_by")
  private String updatedBy;

  private Date updated;
  private Date created;

  @JsonProperty("created_by")
  private String createdBy;

  @JsonProperty("field_types")
  private Map<String, String> fieldTypes;

  private boolean deleted;
}

package com.researchspace.service.inventory.csvexport.model;

/* Model for SubSampleNote as exported to CSV. */

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.SubSampleNote;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class CsvSubSampleNote {

  @JsonProperty("creationTime")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long creationTime;

  @JsonProperty("createdBy")
  private String createdBy;

  @JsonProperty("content")
  private String content;

  public CsvSubSampleNote(SubSampleNote subSampleNote) {
    this.creationTime = subSampleNote.getCreationDateMillis();
    this.createdBy =
        subSampleNote.getCreatedBy() != null ? subSampleNote.getCreatedBy().getUsername() : "";
    this.content = subSampleNote.getContent();
  }

  public String toCsvString() {
    return String.format(
        "Note created by \"%s\" at %s: \"%s\"",
        createdBy, Instant.ofEpochMilli(creationTime).toString(), content);
  }
}

/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.SubSampleNote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonPropertyOrder({"id", "created", "createdBy", "content"})
public class ApiSubSampleNote {

  public static final int MAX_CONTENT_LENGTH = 2000;

  @JsonProperty("id")
  private Long id;

  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long creationDateMillis;

  @JsonProperty("createdBy")
  private ApiUser createdBy;

  @JsonProperty("content")
  @NotBlank
  @NotNull
  @Size(max = MAX_CONTENT_LENGTH)
  private String content;

  public ApiSubSampleNote(SubSampleNote note) {
    setId(note.getId());
    setCreationDateMillis(note.getCreationDateMillis());
    setCreatedBy(new ApiUser(note.getCreatedBy()));
    setContent(note.getContent());
  }

  public ApiSubSampleNote(String content) {
    setContent(content);
  }
}

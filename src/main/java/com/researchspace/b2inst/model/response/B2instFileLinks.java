package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-entry links carried on a {@link B2instDraftFile}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instFileLinks {

  /** Endpoint to upload or download the file bytes. */
  @JsonProperty("content")
  private String content;

  /** Self link to the file entry. */
  @JsonProperty("self")
  private String self;

  /** Endpoint to commit the upload. */
  @JsonProperty("commit")
  private String commit;
}

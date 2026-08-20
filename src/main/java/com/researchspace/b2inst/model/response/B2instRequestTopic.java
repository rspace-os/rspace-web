package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Topic of a {@link B2instRequestResponse}: identifies the draft record the request
 * concerns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRequestTopic {

  /** Record id (RID) the request is about. */
  @JsonProperty("record")
  private String record;
}

package com.researchspace.b2inst.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * B2instEmbargo settings attached to a record's {@link B2instAccess}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instEmbargo {

  /** Whether an embargo is currently active. */
  @JsonProperty("active")
  private Boolean active;

  /** Reason for the embargo, or {@code null} when none applies. */
  @JsonProperty("reason")
  private String reason;
}

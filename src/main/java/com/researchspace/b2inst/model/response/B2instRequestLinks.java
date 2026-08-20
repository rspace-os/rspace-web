package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Links carried on a {@link B2instRequestResponse}, including the
 * {@link #actions} block whose {@code submit} link submits the request to the community.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRequestLinks {

  /** Self link to the request. */
  @JsonProperty("self")
  private String self;

  /** Comments endpoint for the request. */
  @JsonProperty("comments")
  private String comments;

  /** Timeline (events) endpoint for the request. */
  @JsonProperty("timeline")
  private String timeline;

  /** Available state-transition action links for the current status. */
  @JsonProperty("actions")
  private B2instRequestActions actions;
}

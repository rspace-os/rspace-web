package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State-transition action links on a {@link B2instRequestLinks} block. Which fields are
 * present depends on the request status: a {@code created} request exposes
 * {@link #submit}; once {@code submitted} the curator actions {@link #accept},
 * {@link #decline} and {@link #cancel} are exposed instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRequestActions {

  /** Link to submit the request to the community. */
  @JsonProperty("submit")
  private String submit;

  /** Link to accept the submission (curator action). */
  @JsonProperty("accept")
  private String accept;

  /** Link to decline the submission (curator action). */
  @JsonProperty("decline")
  private String decline;

  /** Link to cancel the submission (requester action). */
  @JsonProperty("cancel")
  private String cancel;

  /** Link to expire the request, when applicable. */
  @JsonProperty("expire")
  private String expire;
}

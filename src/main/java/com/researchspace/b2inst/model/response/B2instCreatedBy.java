package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Creator of a {@link B2instRequestResponse}. Exactly one field is populated depending
 * on whether the request was created by a user or on behalf of a community.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instCreatedBy {

  /** Id of the creating user, when created by a user. */
  @JsonProperty("user")
  private String user;

  /** Id of the creating community, when created by a community. */
  @JsonProperty("community")
  private String community;
}

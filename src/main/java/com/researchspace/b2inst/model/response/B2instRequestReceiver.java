package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Receiver of a {@link B2instRequestResponse}. For a community submission the
 * {@link #community} field holds the target community id.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRequestReceiver {

  /** Id of the receiving community. */
  @JsonProperty("community")
  private String community;

  /** Id of the receiving user, when the receiver is a user. */
  @JsonProperty("user")
  private String user;
}

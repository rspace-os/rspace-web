package com.researchspace.b2inst.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PUT /api/records/{id}/draft/review}, which
 * creates the community-submission review request.
 *
 * <p>The response is
 * modelled by {@code com.researchspace.b2inst.model.response.B2instRequestResponse}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instReviewRequest {

  /** The receiver of the request: the target community. */
  @JsonProperty("receiver")
  private B2instReviewReceiver receiver;

  /** Request type; for community submission this is {@code "community-submission"}. */
  @JsonProperty("type")
  private String type;
}

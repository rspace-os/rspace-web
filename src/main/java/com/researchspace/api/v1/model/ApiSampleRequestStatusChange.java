/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.inventory.SampleRequestStatusChange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry in a sample request's status history. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "status", "created", "createdBy", "reason", "transferredSample"})
public class ApiSampleRequestStatusChange {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("status")
  private SampleRequestStatus status;

  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis;

  @JsonProperty("createdBy")
  private ApiUser createdBy;

  /** Populated for a rejection, absent otherwise. */
  @JsonProperty("reason")
  private String reason;

  /**
   * The sample this transition fulfilled the request with, if any: transferred or newly created.
   */
  @JsonProperty("transferredSample")
  private ApiSampleInfo transferredSample;

  public ApiSampleRequestStatusChange(SampleRequestStatusChange change) {
    this.id = change.getId();
    this.status = change.getStatus();
    this.createdMillis = change.getCreated().getTime();
    this.reason = change.getReason();
    this.transferredSample =
        change.getTransferredSample() != null
            ? new ApiSampleInfo(change.getTransferredSample())
            : null;
  }
}

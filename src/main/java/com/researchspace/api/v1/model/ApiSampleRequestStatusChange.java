/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.SampleRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry in a sample request's status history. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "status", "created", "createdBy", "reason"})
public class ApiSampleRequestStatusChange {

  /** Null for the synthesised creation entry, until RSDEV-1368 persists these. */
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

  /** Populated for a rejection. Always null until RSDEV-1368. */
  @JsonProperty("reason")
  private String reason;
}

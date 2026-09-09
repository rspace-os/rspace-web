/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Summary of a request for material from a sample, as returned in listings. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder({"id", "status", "note", "created", "requester", "sample"})
public class ApiSampleRequestInfo extends LinkableApiObject implements IdentifiableObject {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("status")
  private SampleRequestStatus status;

  @JsonProperty("note")
  private String note;

  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis;

  @JsonProperty("requester")
  private ApiUser requester;

  /** Summary only. Callers needing subsample locations fetch the sample itself. */
  @JsonProperty("sample")
  private ApiSampleInfo sample;

  public ApiSampleRequestInfo(SampleRequest request) {
    this.id = request.getId();
    this.status = request.getStatus();
    this.note = request.getNote();
    this.createdMillis = request.getCreated().getTime();
    this.requester = new ApiUser(request.getRequester());
    this.sample = new ApiSampleInfo(request.getSample());
  }
}

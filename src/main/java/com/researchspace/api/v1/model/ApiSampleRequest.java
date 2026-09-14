/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.SampleRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A request for material from a sample, with its full status history. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder({"id", "status", "note", "created", "requester", "sample", "statusChanges"})
public class ApiSampleRequest extends ApiSampleRequestInfo {

  /** Oldest first, starting with the request's creation. */
  @JsonProperty("statusChanges")
  private List<ApiSampleRequestStatusChange> statusChanges = new ArrayList<>();

  public ApiSampleRequest(SampleRequest request) {
    super(request);
    this.statusChanges =
        request.getStatusChanges().stream()
            .map(ApiSampleRequestStatusChange::new)
            .collect(Collectors.toCollection(ArrayList::new));
  }
}

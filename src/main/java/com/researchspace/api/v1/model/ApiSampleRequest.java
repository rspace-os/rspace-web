/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestStatus;
import java.util.ArrayList;
import java.util.List;
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
    this.statusChanges = new ArrayList<>(List.of(creationEntry(request)));
  }

  /**
   * TODO RSDEV-1368: the creation entry is synthesised from the request's own requester and created
   * fields, because no status-change table exists yet. Once SampleRequestStatusChange lands,
   * replace this with a straight mapping of that table's rows, which will include the PENDING row.
   */
  private static ApiSampleRequestStatusChange creationEntry(SampleRequest request) {
    return new ApiSampleRequestStatusChange(
        null,
        SampleRequestStatus.PENDING,
        request.getCreated().getTime(),
        new ApiUser(request.getRequester()),
        null);
  }
}

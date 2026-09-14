/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.inventory.SampleRequestStatusChange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload for moving a sample request to a new status. */
@Data
@NoArgsConstructor
public class ApiSampleRequestStatusPut {

  @NotNull
  @JsonProperty("status")
  private SampleRequestStatus status;

  /** Required when rejecting, and not accepted for any other transition. */
  @Size(max = SampleRequestStatusChange.REASON_COLUMN_LENGTH)
  @JsonProperty("reason")
  private String reason;
}

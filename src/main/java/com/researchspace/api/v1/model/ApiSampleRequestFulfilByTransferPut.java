/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload for fulfilling a sample request by transferring a sample to its requester. */
@Data
@NoArgsConstructor
public class ApiSampleRequestFulfilByTransferPut {

  /**
   * Optional global id of the sample to transfer, e.g. one created from the requested sample. When
   * absent, the requested sample itself is transferred.
   */
  @JsonProperty("sampleToTransferGlobalId")
  private String sampleToTransferGlobalId;
}

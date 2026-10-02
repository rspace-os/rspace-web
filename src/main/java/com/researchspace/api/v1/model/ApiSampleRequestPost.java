/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.researchspace.model.inventory.SampleRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload for raising a request against a requestable sample. */
@Data
@NoArgsConstructor
public class ApiSampleRequestPost {

  @NotBlank private String sampleGlobalId;

  @NotBlank
  @Size(max = SampleRequest.NOTE_COLUMN_LENGTH)
  private String note;
}

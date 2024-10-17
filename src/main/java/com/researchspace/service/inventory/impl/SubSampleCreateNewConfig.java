package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Configure subsample creation. <br> */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubSampleCreateNewConfig {

  /** The id of the sample to which new subsamples should be added */
  @Min(1)
  private Long sampleId;

  /** The total number of new subsamples desired. */
  @Min(1)
  private int numSubSamples;

  /** A quantity of each of the newly added subsamples. */
  private ApiQuantityInfo singleSubSampleQuantity;
}

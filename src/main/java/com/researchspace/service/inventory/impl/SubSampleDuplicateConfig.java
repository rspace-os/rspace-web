package com.researchspace.service.inventory.impl;

import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configure subsample duplication/splitting. <br>
 * <code>numSamples</code> is the desired number of subsamples. E.g. 1 will have no effect, 2 will
 * duplicate, 10 will configure 9 duplicates to be made. <br>
 * <code>split</code> whether the original subsample quantity should be split equally, or not.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubSampleDuplicateConfig {
  /** The id of the subsample to split/duplicate */
  @Min(1)
  private Long subSampleId;

  /**
   * The total number of subsamples desired, including the original. E.g. to duplicate, this value
   * should be 2.
   */
  @Min(2)
  private int numSubSamples;

  /** Whether to split (true) or merely duplicate the subsample (false) */
  private boolean split = false;

  /**
   * Configuration for a simple duplication of a subsample
   *
   * @param subSampleId
   * @return
   */
  public static SubSampleDuplicateConfig simpleDuplicate(Long subSampleId) {
    return new SubSampleDuplicateConfig(subSampleId, 2, false);
  }

  /**
   * Configuration to split a subsample.
   *
   * @param subSampleId
   * @param numberSubsamples
   * @return
   */
  public static SubSampleDuplicateConfig split(Long subSampleId, int numberSubsamples) {
    return new SubSampleDuplicateConfig(subSampleId, numberSubsamples, true);
  }
}

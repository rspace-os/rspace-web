package com.researchspace.api.v1.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An operation reduced to what the transactional core executes, with the origins in the order the
 * caller listed them. Not a wire format: the public contract is {@link
 * ApiInventoryOperationRequests}, and an operation class turns one of those bodies into this.
 */
@Data
@NoArgsConstructor
public class ApiInventoryOperationPost {

  private List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();

  /** Whether each origin must be left holding nothing (Destroy, and Pool with takeAll). */
  private boolean emptiesOrigin;

  /** The sample the operation creates; null for a terminal operation (Destroy). */
  private ApiSampleWithFullSubSamples newSample;
}

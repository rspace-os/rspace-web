package com.researchspace.api.v1.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An operation reduced to what the transactional core executes: one update per origin, in the order
 * the caller listed them, and the sample to create (null for a terminal operation).
 *
 * <p>Not a wire format. The public contract is {@link ApiInventoryOperationRequests}; an operation
 * class turns one of those bodies into this. The emptiesOrigin flag is what the core's live-state
 * rules need to know about the operation that built it, so the core does not have to look it up.
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

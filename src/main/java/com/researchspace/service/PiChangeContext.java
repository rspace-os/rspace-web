package com.researchspace.service;

import lombok.Value;

/** Contains state information during a swap PI process. */
@Value
public class PiChangeContext {
  private boolean initialPiReadEditPermission;
}

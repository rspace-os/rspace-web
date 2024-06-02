package com.researchspace.api.v1.throttling;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class APIFileUploadStats {

  private double fileUploadLimit;

  private double remainingCapacityInPeriod;
}

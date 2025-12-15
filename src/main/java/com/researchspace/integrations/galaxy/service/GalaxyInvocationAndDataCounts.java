package com.researchspace.integrations.galaxy.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GalaxyInvocationAndDataCounts {

  private int invocationCount;
  private int dataCount;
}

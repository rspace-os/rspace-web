package com.researchspace.export.externalworkflows;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalWorkflowDataLink {
  private final String name;
  private final String href;

  public boolean hasHref() {
    return href != null && !href.isEmpty();
  }
}

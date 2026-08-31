package com.researchspace.service.resourceaccess;

import java.util.List;

/** Complete resource access representation returned by the generic service. */
public record ResourceAccessDocument(
    String scheme,
    long version,
    List<ResourceAccessAssignmentDocument> assignments,
    ResourceAccessCallerDocument caller) {

  public ResourceAccessDocument {
    assignments = List.copyOf(assignments);
  }
}

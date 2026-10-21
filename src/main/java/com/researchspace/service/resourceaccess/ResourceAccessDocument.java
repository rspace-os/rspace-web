package com.researchspace.service.resourceaccess;

import java.util.List;

/** Complete resource access representation returned by the generic service. */
public record ResourceAccessDocument(
    String scheme,
    long version,
    List<ResourceAccessAssignmentDocument> assignments,
    ResourceAccessCallerDocument caller,
    boolean inherited) {

  /** Creates an independently managed access document. */
  public ResourceAccessDocument(
      String scheme,
      long version,
      List<ResourceAccessAssignmentDocument> assignments,
      ResourceAccessCallerDocument caller) {
    this(scheme, version, assignments, caller, false);
  }

  public ResourceAccessDocument {
    assignments = List.copyOf(assignments);
  }

  /** Returns this document marked as inherited while retaining its compatible projection. */
  public ResourceAccessDocument asInherited() {
    return inherited
        ? this
        : new ResourceAccessDocument(scheme, version, assignments, caller, true);
  }
}

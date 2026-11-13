package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceSlice;
import java.util.Map;

/** Reads authorized public scalar documents without hydrating entity aggregates. */
@FunctionalInterface
public interface ScalarReader {
  /** Supplies a counted page of complete scalar documents. */
  ResourcePage<Map<String, Object>> read(ResourceRequest request, User subject);

  /** Supplies a count-free slice, or null when only counted pages are supported. */
  default ResourceSlice<Map<String, Object>> slice(ResourceRequest request, User subject) {
    return null;
  }
}

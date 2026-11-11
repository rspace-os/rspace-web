package com.researchspace.model.collection;

import java.util.List;

/** Count-free collection window with navigation derived from one extra matching row. */
public record ResourceSlice<T>(List<T> resources, boolean hasNext) {
  public ResourceSlice {
    resources = List.copyOf(resources);
  }
}

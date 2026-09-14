package com.researchspace.model.collection;

import java.util.List;

/** Persistence-neutral page returned to the generic REST collection dispatcher. */
public record ResourcePage<T>(List<T> resources, long total) {

  public ResourcePage {
    resources = List.copyOf(resources);
  }
}

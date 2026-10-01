package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Internal catalog entry that can resolve one readable relationship target. */
interface ApiV2ReadableResourceTarget {

  CollectionDescription<?> description();

  Map<Object, ResolvedTarget> resolveReadable(Set<Object> ids, User actor);

  default Optional<ResolvedTarget> resolveReadable(Object id, User actor) {
    return Optional.ofNullable(resolveReadable(Set.of(id), actor).get(id));
  }
}

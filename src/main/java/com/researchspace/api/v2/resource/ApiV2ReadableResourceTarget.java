package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import java.util.Optional;

/** Internal catalog entry that can resolve one readable relationship target. */
interface ApiV2ReadableResourceTarget {

  CollectionDescription<?> description();

  Optional<ResolvedTarget> resolveReadable(Object id, User actor);
}

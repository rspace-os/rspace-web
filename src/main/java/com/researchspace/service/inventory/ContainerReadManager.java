package com.researchspace.service.inventory;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.ContainerReadSummary;

/** Transactional reads for the REST v2 container collection. */
public interface ContainerReadManager {
  /** Returns the database-filtered visible page. */
  ResourcePage<Container> find(ResourceRequest request, User actor);

  /** Projects visible containers without loading their Inventory aggregates. */
  ResourcePage<ContainerReadSummary> findSummaries(ResourceRequest request, User actor);

  /** Counts visible containers matching the request. */
  long count(ResourceRequest request, User actor);
}

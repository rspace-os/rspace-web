package com.researchspace.dao;

import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.io.Serializable;
import java.util.List;

/** Persistence seam for a collection exposed through the standard resource manager. */
public interface CollectionDao<T, ID extends Serializable> extends GenericDao<T, ID> {

  /** Returns one page selected by a fully parsed resource request. */
  ResourcePage<T> getResources(ResourceRequest request, RelationshipReadAccess relationshipAccess);

  /** Counts rows selected by a fully parsed resource request. */
  long countResources(ResourceRequest request, RelationshipReadAccess relationshipAccess);

  /** Returns at most {@code limit} rows selected by a fully parsed resource request. */
  List<T> getResources(
      ResourceRequest request, int limit, RelationshipReadAccess relationshipAccess);
}

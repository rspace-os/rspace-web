package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/** Standard manager interface for a mutable REST API v2 collection. */
public interface CollectionManager<T, ID extends Serializable> {

  /** Returns one page selected by a fully parsed resource request. */
  ResourcePage<T> getResources(ResourceRequest request);

  /** Counts resources selected by a fully parsed resource request. */
  long countResources(ResourceRequest request);

  /** Finds one resource without throwing when the identifier is absent. */
  Optional<T> getResource(ID id);

  /** Validates and persists a new resource. */
  T createResource(T resource, User actor);

  /** Atomically validates and persists new resources in input order. */
  List<T> createResources(List<T> resources, User actor);

  /** Applies a validated patch to one resource, if it exists. */
  Optional<T> updateResource(ID id, ParsedDocument patch, User actor);

  /** Removes one resource and returns its pre-delete value, if it exists. */
  Optional<T> removeResource(ID id, User actor);

  /** Atomically applies a validated patch to the resources selected by {@code request}. */
  List<T> updateResources(ResourceRequest request, ParsedDocument patch, User actor);

  /** Atomically removes the resources selected by {@code request}. */
  List<T> removeResources(ResourceRequest request, User actor);
}

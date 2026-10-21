package com.researchspace.model.collection;

/** A public, uniquely named member of a collection resource: a scalar field or a relationship. */
public sealed interface ResourceMember<T> permits Field, Relationship {

  String name();
}

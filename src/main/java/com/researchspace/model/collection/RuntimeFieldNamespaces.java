package com.researchspace.model.collection;

/**
 * The published runtime-field namespaces, named once.
 *
 * <p>A namespace appears in three places that have to agree: the selector a client writes, the
 * catalog route that lists the namespace's definitions, and the search-index field a value is
 * written under. The first two would fail loudly on a mismatch; the third would not, because an
 * index lookup that finds nothing falls back to the database, so a renamed namespace would quietly
 * cost the index rather than break. Sharing the constant is what keeps that failure impossible.
 */
public final class RuntimeFieldNamespaces {

  public static final String CUSTOM_FIELDS = "customFields";

  public static final String EXTRA_FIELDS = "extraFields";

  private RuntimeFieldNamespaces() {}
}

package com.researchspace.service.inventory;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies an inventory collection's ad-hoc extra fields to REST API v2.
 *
 * <p>The methods are re-declared rather than inherited so they sit on a {@code *Manager} interface
 * in this package, which is what puts them under the transaction advice; the DAO behind them
 * assumes an open transaction.
 *
 * <p>Unlike a template-backed custom field, an extra field is typed onto one record and shares
 * nothing with any other. A definition is therefore the pair (name, declared type), which is the
 * only thing two records can agree on, and a rename produces a different definition.
 *
 * @param <T> the entity this collection exposes
 */
public interface ExtraFieldRuntimeManager<T> extends RuntimeCollectionFields<T> {

  @Override
  String namespace();

  @Override
  RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query);

  @Override
  Optional<ResolvedRuntimeField> resolve(String selector, User actor);

  @Override
  Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor);

  @Override
  Map<Object, Map<String, Object>> values(List<T> resources, Set<String> fieldIds, User actor);
}

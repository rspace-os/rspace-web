package com.researchspace.service.inventory;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeCollectionFields;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Supplies the instrument collection's template-backed custom fields to REST API v2.
 *
 * <p>The three methods are re-declared rather than inherited so they sit on a {@code *Manager}
 * interface in this package, which is what puts them under the transaction advice; the DAO behind
 * them assumes an open transaction.
 *
 * <p>Only fields copied from a template are published, identified by the template field's Global
 * ID. An ad-hoc extra field has no shared definition, so there is nothing stable to filter a whole
 * collection by and nothing that survives a rename.
 */
public interface InstrumentCustomFieldManager extends RuntimeCollectionFields<Instrument> {

  @Override
  String namespace();

  @Override
  RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query);

  @Override
  Optional<ResolvedRuntimeField> resolve(String selector, User actor);

  @Override
  Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor);

  @Override
  Map<Object, Map<String, Object>> values(
      List<Instrument> resources, Set<String> fieldIds, User actor);
}

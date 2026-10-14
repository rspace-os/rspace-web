package com.researchspace.service.inventory.impl;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.dao.ExtraFieldDao.ExtraFieldScope;
import com.researchspace.dao.query.LookAheadPagination.Result;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldNamespaces;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.Definition;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.PublishedType;
import com.researchspace.service.inventory.ExtraFieldRuntimeManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Publishes one inventory collection's ad-hoc extra fields as REST API v2 runtime fields.
 *
 * <p>One instance per collection: the entity, its description and the owning property are supplied,
 * so samples, subsamples, containers and instruments all use this class rather than a copy of it.
 *
 * @param <T> the entity this collection exposes
 */
public class ExtraFieldRuntimeManagerImpl<T> implements ExtraFieldRuntimeManager<T> {

  public static final String NAMESPACE = RuntimeFieldNamespaces.EXTRA_FIELDS;

  private static final Set<FieldType> PUBLISHED_TYPES = ExtraFieldIdentity.publishedTypes();

  private final ExtraFieldDao extraFieldDao;
  private final ExtraFieldScope scope;
  private final String resourceName;
  private final Function<AccessContext, AccessResult> readAccess;
  private final Function<T, Object> idOf;

  public ExtraFieldRuntimeManagerImpl(
      ExtraFieldDao extraFieldDao,
      Class<?> parentEntity,
      CollectionDescription<T> description,
      String parentProperty,
      Function<AccessContext, AccessResult> readAccess) {
    this.extraFieldDao = extraFieldDao;
    this.scope = new ExtraFieldScope(parentEntity, description, parentProperty);
    this.resourceName = description.resourceName();
    this.readAccess = readAccess;
    this.idOf = description::idValue;
  }

  @Override
  public String namespace() {
    return NAMESPACE;
  }

  @Override
  public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
    AccessResult access = readAccess.apply(new AccessContext(actor, Operation.READ, resourceName));
    if (access.isDenied()) {
      return RuntimeFieldCatalogPage.empty();
    }
    Set<Definition> wanted = new LinkedHashSet<>();
    if (query.hydratesIds()) {
      query
          .ids()
          .forEach(id -> RuntimeFieldManagerSupport.add(wanted, ExtraFieldIdentity.decode(id)));
      if (wanted.isEmpty()) {
        return RuntimeFieldCatalogPage.empty();
      }
    }
    Result<Definition> page =
        extraFieldDao.readableDefinitions(
            scope,
            access.constraintOrEmpty().orElse(null),
            query.normalisedSearch(),
            wanted,
            PUBLISHED_TYPES,
            query.offset(),
            query.limit());
    return new RuntimeFieldCatalogPage(definitions(page.rows()), page.total(), page.hasMore());
  }

  @Override
  public Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor) {
    return RuntimeFieldManagerSupport.resolveAll(
        selectors,
        selector ->
            ExtraFieldIdentity.decode(RuntimeFieldManagerSupport.selectorId(selector, NAMESPACE)),
        definition -> ExtraFieldIdentity.encode(definition.name(), definition.type()),
        this::binding,
        ids -> discover(actor, RuntimeFieldCatalogQuery.byIds(ids)));
  }

  @Override
  public Map<Object, Map<String, Object>> values(
      List<T> resources, Set<String> fieldIds, User actor) {
    return valuesForAuthorizedIds(resources.stream().map(idOf).toList(), fieldIds, actor);
  }

  @Override
  public Map<Object, Map<String, Object>> valuesForAuthorizedIds(
      Collection<?> resourceIds, Set<String> fieldIds, User actor) {
    Set<Definition> definitions = new LinkedHashSet<>();
    fieldIds.forEach(
        id -> RuntimeFieldManagerSupport.add(definitions, ExtraFieldIdentity.decode(id)));
    Map<Object, Long> parentIds = new LinkedHashMap<>();
    for (Object id : resourceIds) {
      if (id instanceof Number number) {
        parentIds.put(id, number.longValue());
      }
    }
    if (definitions.isEmpty() || parentIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Map<Definition, String>> rows =
        extraFieldDao.valuesByParent(scope, new LinkedHashSet<>(parentIds.values()), definitions);
    Map<Object, Map<String, Object>> values = new LinkedHashMap<>();
    parentIds.forEach(
        (publicId, databaseId) -> {
          Map<Definition, String> owned = rows.get(databaseId);
          if (owned == null) {
            return;
          }
          Map<String, Object> document = new LinkedHashMap<>();
          owned.forEach(
              (definition, stored) ->
                  document.put(
                      ExtraFieldIdentity.encode(definition.name(), definition.type()),
                      valueType(definition.type()).serialize(stored)));
          values.put(publicId, document);
        });
    return values;
  }

  private List<RuntimeFieldDefinition> definitions(List<Definition> rows) {
    List<RuntimeFieldDefinition> definitions = new ArrayList<>(rows.size());
    for (Definition row : rows) {
      RuntimeFieldValueType type = valueType(row.type());
      if (type == null) {
        continue;
      }
      String id = ExtraFieldIdentity.encode(row.name(), row.type());
      definitions.add(
          new RuntimeFieldDefinition(
              id, NAMESPACE + "." + id, row.name(), type, "", "", List.of()));
    }
    return definitions;
  }

  private RuntimeFieldBinding binding(Definition row) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("editInfo.name", row.name());
    match.put("deleted", false);
    PublishedType published = PublishedType.fromFieldType(row.type());
    return new RuntimeFieldBinding(
        published == null ? null : published.entityType(),
        scope.parentProperty() + ".id",
        "editInfo.description",
        match);
  }

  private static RuntimeFieldValueType valueType(FieldType type) {
    PublishedType published = PublishedType.fromFieldType(type);
    return published == null ? null : published.runtimeType();
  }
}

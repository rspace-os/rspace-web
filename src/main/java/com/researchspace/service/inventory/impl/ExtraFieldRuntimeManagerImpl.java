package com.researchspace.service.inventory.impl;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.dao.ExtraFieldDao.ExtraFieldPage;
import com.researchspace.dao.ExtraFieldDao.ExtraFieldRow;
import com.researchspace.dao.ExtraFieldDao.ExtraFieldScope;
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
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.service.inventory.ExtraFieldRuntimeManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  static final int MAX_NAME_LENGTH = ExtraFieldIdentity.MAX_NAME_LENGTH;

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
    Set<ExtraFieldRow> wanted = new LinkedHashSet<>();
    if (query.hydratesIds()) {
      query.ids().forEach(id -> add(wanted, decode(id)));
      if (wanted.isEmpty()) {
        return RuntimeFieldCatalogPage.empty();
      }
    }
    ExtraFieldPage page =
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
  public Optional<ResolvedRuntimeField> resolve(String selector, User actor) {
    return Optional.ofNullable(resolveAll(Set.of(selector), actor).get(selector));
  }

  @Override
  public Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor) {
    Map<String, ExtraFieldRow> requested = new LinkedHashMap<>();
    for (String selector : selectors) {
      ExtraFieldRow row = decode(idOf(selector));
      if (row != null) {
        requested.put(selector, row);
      }
    }
    if (requested.isEmpty()) {
      return Map.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    requested.values().forEach(row -> ids.add(encode(row)));
    Map<String, RuntimeFieldDefinition> found = new LinkedHashMap<>();
    List<String> ordered = new ArrayList<>(ids);
    for (int from = 0; from < ordered.size(); from += RuntimeFieldCatalogQuery.MAX_IDS) {
      Set<String> batch =
          new LinkedHashSet<>(
              ordered.subList(
                  from, Math.min(from + RuntimeFieldCatalogQuery.MAX_IDS, ordered.size())));
      discover(actor, RuntimeFieldCatalogQuery.byIds(batch))
          .fields()
          .forEach(definition -> found.put(definition.id(), definition));
    }
    Map<String, ResolvedRuntimeField> resolved = new LinkedHashMap<>();
    requested.forEach(
        (selector, row) -> {
          RuntimeFieldDefinition definition = found.get(encode(row));
          if (definition != null) {
            resolved.put(selector, new ResolvedRuntimeField(definition, binding(row)));
          }
        });
    return resolved;
  }

  @Override
  public Map<Object, Map<String, Object>> values(
      List<T> resources, Set<String> fieldIds, User actor) {
    Set<ExtraFieldRow> definitions = new LinkedHashSet<>();
    fieldIds.forEach(id -> add(definitions, decode(id)));
    Map<Object, Long> parentIds = new LinkedHashMap<>();
    for (T resource : resources) {
      Object id = idOf.apply(resource);
      if (id instanceof Number number) {
        parentIds.put(id, number.longValue());
      }
    }
    if (definitions.isEmpty() || parentIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Map<ExtraFieldRow, String>> rows =
        extraFieldDao.valuesByParent(scope, new LinkedHashSet<>(parentIds.values()), definitions);
    Map<Object, Map<String, Object>> values = new LinkedHashMap<>();
    parentIds.forEach(
        (publicId, databaseId) -> {
          Map<ExtraFieldRow, String> owned = rows.get(databaseId);
          if (owned == null) {
            return;
          }
          Map<String, Object> document = new LinkedHashMap<>();
          owned.forEach(
              (definition, stored) ->
                  document.put(encode(definition), valueType(definition.type()).serialize(stored)));
          values.put(publicId, document);
        });
    return values;
  }

  private List<RuntimeFieldDefinition> definitions(List<ExtraFieldRow> rows) {
    List<RuntimeFieldDefinition> definitions = new ArrayList<>(rows.size());
    for (ExtraFieldRow row : rows) {
      RuntimeFieldValueType type = valueType(row.type());
      if (type == null || row.name().length() > MAX_NAME_LENGTH) {
        continue;
      }
      String id = encode(row);
      definitions.add(
          new RuntimeFieldDefinition(
              id, NAMESPACE + "." + id, row.name(), type, "", "", List.of()));
    }
    return definitions;
  }

  private RuntimeFieldBinding binding(ExtraFieldRow row) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("editInfo.name", row.name());
    match.put("deleted", false);
    return new RuntimeFieldBinding(
        entityType(row.type()), scope.parentProperty() + ".id", "editInfo.description", match);
  }

  private static Class<?> entityType(FieldType type) {
    return switch (type) {
      case NUMBER -> ExtraNumberField.class;
      case LINK -> ExtraLinkField.class;
      default -> ExtraTextField.class;
    };
  }

  private static RuntimeFieldValueType valueType(FieldType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case TEXT, LINK -> RuntimeFieldValueType.TEXT;
      case NUMBER -> RuntimeFieldValueType.NUMBER;
      default -> null;
    };
  }

  /**
   * The stable ID of a definition, as {@link ExtraFieldIdentity} encodes it.
   *
   * <p>Shared with the search index writer, which has to name a value's index field the same way
   * this provider names the definition a filter resolves to.
   */
  static String encode(ExtraFieldRow row) {
    return ExtraFieldIdentity.encode(row.name(), row.type());
  }

  static ExtraFieldRow decode(String id) {
    ExtraFieldIdentity.Definition definition = ExtraFieldIdentity.decode(id);
    return definition == null ? null : new ExtraFieldRow(definition.name(), definition.type());
  }

  private static String idOf(String selector) {
    String prefix = NAMESPACE + ".";
    return selector == null || !selector.startsWith(prefix)
        ? null
        : selector.substring(prefix.length());
  }

  private static void add(Set<ExtraFieldRow> rows, ExtraFieldRow row) {
    if (row != null) {
      rows.add(row);
    }
  }
}

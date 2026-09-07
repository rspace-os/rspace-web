package com.researchspace.service.inventory.impl;

import com.researchspace.dao.InstrumentCustomFieldDao;
import com.researchspace.dao.InstrumentCustomFieldDao.DefinitionPage;
import com.researchspace.dao.InstrumentCustomFieldDao.DefinitionRow;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldCatalogPage;
import com.researchspace.model.collection.RuntimeFieldCatalogQuery;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldNamespaces;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Publishes template-backed instrument fields as REST API v2 runtime fields. */
@Service("instrumentCustomFieldManager")
public class InstrumentCustomFieldManagerImpl implements InstrumentCustomFieldManager {

  static final String NAMESPACE = RuntimeFieldNamespaces.CUSTOM_FIELDS;

  private static final Set<FieldType> PUBLISHED_TYPES =
      Arrays.stream(FieldType.values())
          .filter(type -> valueType(type) != null)
          .collect(Collectors.toUnmodifiableSet());

  private final InstrumentCustomFieldDao customFieldDao;
  private final InstrumentReadAccess readAccess;

  public InstrumentCustomFieldManagerImpl(
      InstrumentCustomFieldDao customFieldDao, InstrumentReadAccess readAccess) {
    this.customFieldDao = customFieldDao;
    this.readAccess = readAccess;
  }

  @Override
  public String namespace() {
    return NAMESPACE;
  }

  @Override
  public RuntimeFieldCatalogPage discover(User actor, RuntimeFieldCatalogQuery query) {
    AccessResult access = readAccess.check(new AccessContext(actor, Operation.READ, "instruments"));
    if (access.isDenied()) {
      return RuntimeFieldCatalogPage.empty();
    }
    Set<Long> ids = new LinkedHashSet<>();
    if (query.hydratesIds()) {
      query.ids().forEach(id -> add(ids, globalIdToDatabaseId(id)));
      if (ids.isEmpty()) {
        return RuntimeFieldCatalogPage.empty();
      }
    }
    DefinitionPage page =
        customFieldDao.readableDefinitions(
            access.constraintOrEmpty().orElse(null),
            query.normalisedSearch(),
            ids,
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
    Map<String, Long> requested = new LinkedHashMap<>();
    for (String selector : selectors) {
      Long definitionId = definitionId(selector);
      if (definitionId != null) {
        requested.put(selector, definitionId);
      }
    }
    if (requested.isEmpty()) {
      return Map.of();
    }
    Map<String, RuntimeFieldDefinition> found = new LinkedHashMap<>();
    List<Long> ids = new ArrayList<>(new LinkedHashSet<>(requested.values()));
    for (int from = 0; from < ids.size(); from += RuntimeFieldCatalogQuery.MAX_IDS) {
      Set<String> batch = new LinkedHashSet<>();
      ids.subList(from, Math.min(from + RuntimeFieldCatalogQuery.MAX_IDS, ids.size()))
          .forEach(id -> batch.add(globalId(id)));
      discover(actor, RuntimeFieldCatalogQuery.byIds(batch))
          .fields()
          .forEach(definition -> found.put(definition.id(), definition));
    }
    Map<String, ResolvedRuntimeField> resolved = new LinkedHashMap<>();
    requested.forEach(
        (selector, definitionId) -> {
          RuntimeFieldDefinition definition = found.get(globalId(definitionId));
          if (definition != null) {
            resolved.put(selector, new ResolvedRuntimeField(definition, binding(definitionId)));
          }
        });
    return resolved;
  }

  @Override
  public Map<Object, Map<String, Object>> values(
      List<Instrument> resources, Set<String> fieldIds, User actor) {
    Set<Long> definitionIds = new LinkedHashSet<>();
    fieldIds.forEach(id -> add(definitionIds, globalIdToDatabaseId(id)));
    Set<Long> instrumentIds = new LinkedHashSet<>();
    resources.forEach(instrument -> instrumentIds.add(instrument.getId()));
    if (definitionIds.isEmpty() || instrumentIds.isEmpty()) {
      return Map.of();
    }
    return valuesOf(instrumentIds, definitionIds);
  }

  private Map<Object, Map<String, Object>> valuesOf(
      Set<Long> instrumentIds, Set<Long> definitionIds) {
    Map<Long, Map<Long, InventoryEntityField>> rows =
        customFieldDao.valuesByInstrument(instrumentIds, definitionIds);
    Map<Object, Map<String, Object>> values = new LinkedHashMap<>();
    rows.forEach(
        (instrumentId, fields) -> {
          Map<String, Object> document = new LinkedHashMap<>();
          fields.forEach(
              (definitionId, field) -> {
                RuntimeFieldValueType type = valueType(field.getType());
                if (type != null) {
                  document.put(globalId(definitionId), type.serialize(field.getData()));
                }
              });
          values.put(instrumentId, document);
        });
    return values;
  }

  @Override
  public boolean projectsThroughRelationship() {
    return true;
  }

  @Override
  public Map<Object, Map<String, Object>> valuesForIds(
      Collection<?> resourceIds, Set<String> fieldIds, User actor) {
    Set<Long> instrumentIds = new LinkedHashSet<>();
    for (Object id : resourceIds) {
      if (id instanceof Number number) {
        instrumentIds.add(number.longValue());
      }
    }
    Set<Long> definitionIds = new LinkedHashSet<>();
    fieldIds.forEach(id -> add(definitionIds, globalIdToDatabaseId(id)));
    if (instrumentIds.isEmpty() || definitionIds.isEmpty()) {
      return Map.of();
    }
    AccessResult access = readAccess.check(new AccessContext(actor, Operation.READ, "instruments"));
    if (access.isDenied()) {
      return Map.of();
    }
    Set<Long> readable =
        customFieldDao.readableInstruments(access.constraintOrEmpty().orElse(null), instrumentIds);
    if (readable.isEmpty()) {
      return Map.of();
    }
    return valuesOf(readable, definitionIds);
  }

  private List<RuntimeFieldDefinition> definitions(List<DefinitionRow> rows) {
    Set<Long> optionBearing = new LinkedHashSet<>();
    for (DefinitionRow row : rows) {
      RuntimeFieldValueType type = valueType(row.type());
      if (type == RuntimeFieldValueType.RADIO || type == RuntimeFieldValueType.CHOICE) {
        optionBearing.add(row.id());
      }
    }
    Map<Long, List<String>> options =
        optionBearing.isEmpty() ? Map.of() : customFieldDao.optionsFor(optionBearing);
    List<RuntimeFieldDefinition> definitions = new ArrayList<>(rows.size());
    for (DefinitionRow row : rows) {
      RuntimeFieldValueType type = valueType(row.type());
      if (type == null) {
        continue;
      }
      String id = globalId(row.id());
      definitions.add(
          new RuntimeFieldDefinition(
              id,
              NAMESPACE + "." + id,
              row.name(),
              type,
              row.sourceId() == null ? "" : GlobalIdPrefix.IT.name() + row.sourceId(),
              row.sourceName() == null ? "" : row.sourceName(),
              options.getOrDefault(row.id(), List.of())));
    }
    return definitions;
  }

  private static void add(Set<Long> ids, Long id) {
    if (id != null) {
      ids.add(id);
    }
  }

  private static RuntimeFieldBinding binding(Long definitionId) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("templateField.id", definitionId);
    match.put("deleted", false);
    return new RuntimeFieldBinding(
        InventoryEntityField.class, "instrumentEntity.id", "data", match);
  }

  private static RuntimeFieldValueType valueType(FieldType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case STRING, TEXT, URI, IDENTIFIER -> RuntimeFieldValueType.TEXT;
      case NUMBER -> RuntimeFieldValueType.NUMBER;
      case DATE -> RuntimeFieldValueType.DATE;
      case TIME -> RuntimeFieldValueType.TIME;
      case RADIO -> RuntimeFieldValueType.RADIO;
      case CHOICE -> RuntimeFieldValueType.CHOICE;
      default -> null;
    };
  }

  private static Long definitionId(String selector) {
    String prefix = NAMESPACE + ".";
    if (selector == null || !selector.startsWith(prefix)) {
      return null;
    }
    return globalIdToDatabaseId(selector.substring(prefix.length()));
  }

  private static Long globalIdToDatabaseId(String globalId) {
    if (globalId == null || !GlobalIdentifier.isValid(globalId)) {
      return null;
    }
    GlobalIdentifier identifier = new GlobalIdentifier(globalId);
    if (identifier.hasVersionId() || identifier.getPrefix() != GlobalIdPrefix.SF) {
      return null;
    }
    return identifier.getDbId();
  }

  private static String globalId(Long definitionId) {
    return definitionId == null
        ? null
        : new GlobalIdentifier(GlobalIdPrefix.SF, definitionId).getIdString();
  }
}

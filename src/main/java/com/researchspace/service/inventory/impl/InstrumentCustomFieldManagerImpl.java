package com.researchspace.service.inventory.impl;

import com.researchspace.dao.InstrumentCustomFieldDao;
import com.researchspace.dao.InstrumentCustomFieldDao.DefinitionRow;
import com.researchspace.dao.InstrumentCustomFieldDao.ValueRow;
import com.researchspace.dao.query.LookAheadPagination.Result;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Publishes template-backed instrument fields as REST API v2 runtime fields. */
@Service("instrumentCustomFieldManager")
public class InstrumentCustomFieldManagerImpl implements InstrumentCustomFieldManager {

  static final String NAMESPACE = RuntimeFieldNamespaces.CUSTOM_FIELDS;

  private static final Set<FieldType> PUBLISHED_TYPES =
      java.util.Arrays.stream(FieldType.values())
          .filter(type -> valueType(type) != null)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());

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
      query.ids().forEach(id -> RuntimeFieldManagerSupport.add(ids, globalIdToDatabaseId(id)));
      if (ids.isEmpty()) {
        return RuntimeFieldCatalogPage.empty();
      }
    }
    Result<DefinitionRow> page =
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
  public Map<String, ResolvedRuntimeField> resolveAll(Set<String> selectors, User actor) {
    return RuntimeFieldManagerSupport.resolveAll(
        selectors,
        selector -> definitionId(selector),
        InstrumentCustomFieldManagerImpl::globalId,
        InstrumentCustomFieldManagerImpl::binding,
        ids -> discover(actor, RuntimeFieldCatalogQuery.byIds(ids)));
  }

  @Override
  public Map<Object, Map<String, Object>> values(
      List<Instrument> resources, Set<String> fieldIds, User actor) {
    Set<Long> definitionIds = definitionIds(fieldIds);
    Set<Long> instrumentIds = instrumentIds(resources);
    if (definitionIds.isEmpty() || instrumentIds.isEmpty()) {
      return Map.of();
    }
    return valuesOf(instrumentIds, definitionIds);
  }

  private Map<Object, Map<String, Object>> valuesOf(
      Set<Long> instrumentIds, Set<Long> definitionIds) {
    Map<Long, Map<Long, ValueRow>> rows =
        customFieldDao.valuesByInstrument(instrumentIds, definitionIds);
    Map<Object, Map<String, Object>> values = new LinkedHashMap<>();
    rows.forEach(
        (instrumentId, fields) -> {
          Map<String, Object> document = new LinkedHashMap<>();
          fields.forEach(
              (definitionId, field) -> {
                RuntimeFieldValueType type = valueType(field.type());
                if (type != null) {
                  document.put(globalId(definitionId), type.serialize(field.data()));
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
    Set<Long> instrumentIds = instrumentIds(resourceIds);
    Set<Long> definitionIds = definitionIds(fieldIds);
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

  private static Set<Long> instrumentIds(Collection<?> resources) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Object resource : resources) {
      if (resource instanceof Instrument instrument) {
        RuntimeFieldManagerSupport.add(ids, instrument.getId());
      } else if (resource instanceof Number id) {
        RuntimeFieldManagerSupport.add(ids, id.longValue());
      }
    }
    return ids;
  }

  private static Set<Long> definitionIds(Set<String> selectors) {
    Set<Long> ids = new LinkedHashSet<>();
    selectors.forEach(
        selector -> RuntimeFieldManagerSupport.add(ids, globalIdToDatabaseId(selector)));
    return ids;
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
    return globalIdToDatabaseId(RuntimeFieldManagerSupport.selectorId(selector, NAMESPACE));
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

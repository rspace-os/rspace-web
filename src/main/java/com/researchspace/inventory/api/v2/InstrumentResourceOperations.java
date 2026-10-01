package com.researchspace.inventory.api.v2;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.inventory.ExtraFieldRuntimeManager;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.service.inventory.impl.ExtraFieldRuntimeManagerImpl;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Instruments as a read-only REST API v2 collection, so clients can search the instruments
 * they may read and use one as a relationship value.
 *
 * <p>Registering the resource also registers it as a relationship target, which is why there is no
 * separate target-only registration: {@code ApiV2ResourceCatalog} keeps one entry per resource
 * name.
 *
 * <p>This class holds no query logic and no transaction. {@code ApiV2InstrumentResource} declares
 * soft deletion as a read constraint and the registration folds it into each read. The manager owns
 * the transaction, and the DAO applies the filter, the sort, the inventory permission rules, and
 * the page in the database.
 */
@Configuration(proxyBeanMethods = false)
public class InstrumentResourceOperations implements ResourceOperations<Instrument, Long> {

  private static final Set<String> LOCATION_FIELDS =
      Set.of("parentContainerName", "parentContainerGlobalId");

  private final InstrumentEntityApiManager instrumentManager;
  private final InstrumentReadAccess readAccess;
  private final InstrumentCustomFieldManager customFields;
  private final ExtraFieldDao extraFieldDao;

  public InstrumentResourceOperations(
      InstrumentEntityApiManager instrumentManager,
      InstrumentReadAccess readAccess,
      InstrumentCustomFieldManager customFields,
      ExtraFieldDao extraFieldDao) {
    this.instrumentManager = instrumentManager;
    this.readAccess = readAccess;
    this.customFields = customFields;
    this.extraFieldDao = extraFieldDao;
  }

  /**
   * This collection's ad-hoc fields, which every inventory record type reaches the same way.
   *
   * <p>A bean rather than a new instance per call, because its DAO needs a transaction and only a
   * Spring-managed object gets one: the {@code *..service.inventory.*Manager} advisor matches the
   * interface, so the proxy Spring creates here is what opens the session the catalogue query runs
   * in. Constructed by hand rather than component-scanned because it is parameterised by the
   * collection it serves, and one class serves four of them.
   */
  @Bean
  ExtraFieldRuntimeManager<Instrument> instrumentExtraFieldRuntimeManager() {
    return new ExtraFieldRuntimeManagerImpl<>(
        extraFieldDao,
        Instrument.class,
        ApiV2InstrumentResource.DESCRIPTION,
        "instrumentEntity",
        readAccess::check);
  }

  /**
   * @param extraFields injected rather than called, because {@code proxyBeanMethods = false} makes
   *     a direct call to the bean method a plain constructor call: the object it returns is not the
   *     Spring bean, so it carries no transaction advice and its DAO finds no session.
   */
  @Bean
  ApiV2ResourceSpec<Instrument, Long> instrumentApiV2Resource(
      ExtraFieldRuntimeManager<Instrument> extraFields) {
    return new ApiV2ResourceSpec<>(
        ApiV2InstrumentResource.description(readAccess),
        this,
        Long::valueOf,
        "errors.api.v2.instrument.create",
        "errors.api.v2.instrument.patch",
        Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
        Map.of(),
        Map.of(),
        CollectionMutationLimits.DEFAULT,
        List.of(customFields, extraFields));
  }

  @Override
  public ResourcePage<Instrument> find(ResourceRequest request, User actor) {
    return instrumentManager.getReadableInstruments(request, actor);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return instrumentManager.countReadableInstruments(request, actor);
  }

  /**
   * Reads one instrument. Unreachable while the collection declares a read constraint, because the
   * registration then answers a single read through {@link #find}, but it must stay correct: it
   * repeats the deletion rule the constraint would otherwise apply.
   */
  @Override
  public Optional<Instrument> findById(Long id, User actor) {
    return instrumentManager.findReadableInstrument(id, actor).filter(this::isActive);
  }

  @Override
  public Map<Object, Map<String, Object>> readOverrides(List<Instrument> instruments, User actor) {
    Set<Long> instrumentIds =
        instruments.stream().map(Instrument::getId).collect(java.util.stream.Collectors.toSet());
    Map<Long, InstrumentParentLocationSummary> locations =
        instrumentManager.getParentLocationSummaries(instrumentIds);
    Set<Long> parentIds =
        locations.values().stream()
            .map(InstrumentParentLocationSummary::containerId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<Long> readable = instrumentManager.getReadableParentContainerIds(parentIds, actor);
    Map<Object, Map<String, Object>> overrides = new LinkedHashMap<>();
    for (Instrument instrument : instruments) {
      InstrumentParentLocationSummary location = locations.get(instrument.getId());
      boolean show = location != null && readable.contains(location.containerId());
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("parentContainerName", show ? location.containerName() : null);
      values.put(
          "parentContainerGlobalId",
          show ? containerGlobalId(location.containerId(), location.containerType()) : null);
      overrides.put(instrument.getId(), values);
    }
    return overrides;
  }

  @Override
  public Set<String> readOverrideFields() {
    return LOCATION_FIELDS;
  }

  @Override
  public Optional<Map<Object, Map<String, Object>>> relationshipReadDocuments(
      Set<Long> ids, User actor) {
    Map<Long, InstrumentReadSummary> summaries =
        instrumentManager.getReadableInstrumentSummaries(ids, actor);
    Set<Long> parentIds =
        summaries.values().stream()
            .map(InstrumentReadSummary::parentContainerId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    Set<Long> readableParents = instrumentManager.getReadableParentContainerIds(parentIds, actor);
    Map<Object, Map<String, Object>> documents = new LinkedHashMap<>();
    summaries.forEach(
        (id, summary) -> {
          boolean showParent =
              summary.parentContainerId() != null
                  && readableParents.contains(summary.parentContainerId());
          Map<String, Object> document = new LinkedHashMap<>();
          document.put("id", summary.id());
          document.put("name", summary.name());
          document.put("globalId", "IN" + summary.id());
          document.put("parentContainerName", showParent ? summary.parentContainerName() : null);
          document.put(
              "parentContainerGlobalId",
              showParent
                  ? containerGlobalId(summary.parentContainerId(), summary.parentContainerType())
                  : null);
          document.put("deleted", summary.deleted());
          documents.put(id, document);
        });
    return Optional.of(documents);
  }

  private boolean isActive(Instrument instrument) {
    return !instrument.isDeleted();
  }

  private static String containerGlobalId(long id, ContainerType type) {
    return (type == ContainerType.WORKBENCH ? "BE" : "IC") + id;
  }
}

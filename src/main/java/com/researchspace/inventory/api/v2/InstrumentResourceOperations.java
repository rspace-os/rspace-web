package com.researchspace.inventory.api.v2;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
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

  private final InstrumentEntityApiManager instrumentManager;

  public InstrumentResourceOperations(InstrumentEntityApiManager instrumentManager) {
    this.instrumentManager = instrumentManager;
  }

  @Bean
  ApiV2ResourceSpec<Instrument, Long> instrumentApiV2Resource() {
    return new ApiV2ResourceSpec<>(
        ApiV2InstrumentResource.DESCRIPTION,
        this,
        Long::valueOf,
        "errors.api.v2.instrument.create",
        "errors.api.v2.instrument.patch",
        Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
        Map.of(),
        Map.of(),
        CollectionMutationLimits.DEFAULT);
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

  private boolean isActive(Instrument instrument) {
    return !instrument.isDeleted();
  }
}

package com.researchspace.maintenance.api.v2;

import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.maintenance.service.MaintenanceOperationException;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/** Maintenance manager adapter for the generic REST v2 CRUD dispatcher. */
@Configuration(proxyBeanMethods = false)
public final class MaintenanceResourceOperations
    implements ResourceOperations<ScheduledMaintenance, Long> {

  private final MaintenanceManager manager;

  public MaintenanceResourceOperations(MaintenanceManager manager) {
    this.manager = manager;
  }

  @Bean
  ApiV2ResourceSpec<ScheduledMaintenance, Long> maintenanceApiV2Resource() {
    List<ApiV2ErrorMapping> invalidWindow =
        List.of(
            ApiV2ErrorMapping.of(
                MaintenanceOperationException.class,
                HttpStatus.BAD_REQUEST,
                "errors.api.v2.maintenance.window",
                "The maintenance window is invalid."));
    return new ApiV2ResourceSpec<>(
        ApiV2MaintenanceResource.DESCRIPTION,
        this,
        Long::valueOf,
        "errors.api.v2.invalidRequest",
        "errors.api.v2.maintenance.patch",
        Map.of(
            ResourceOperation.CREATE,
            invalidWindow,
            ResourceOperation.BULK_CREATE,
            invalidWindow,
            ResourceOperation.UPDATE,
            invalidWindow,
            ResourceOperation.BULK_UPDATE,
            invalidWindow));
  }

  @Override
  public ResourcePage<ScheduledMaintenance> find(ResourceRequest request) {
    return manager.getResources(request);
  }

  @Override
  public long count(ResourceRequest request) {
    return manager.countResources(request);
  }

  @Override
  public Optional<ScheduledMaintenance> findById(Long id) {
    return manager.getResource(id);
  }

  @Override
  public ScheduledMaintenance create(ParsedDocument document, User actor) {
    return manager.createResource(
        ApiV2MaintenanceInput.from(document).toScheduledMaintenance(), actor);
  }

  @Override
  public List<ScheduledMaintenance> createMany(List<ParsedDocument> documents, User actor) {
    return manager.createResources(
        documents.stream()
            .map(ApiV2MaintenanceInput::from)
            .map(ApiV2MaintenanceInput::toScheduledMaintenance)
            .toList(),
        actor);
  }

  @Override
  public Optional<ScheduledMaintenance> update(Long id, ParsedDocument document, User actor) {
    return manager.updateResource(id, document, actor);
  }

  @Override
  public List<ScheduledMaintenance> updateMany(
      ResourceRequest request, ParsedDocument document, User actor) {
    return manager.updateResources(request, document, actor);
  }

  @Override
  public Optional<ScheduledMaintenance> delete(Long id, User actor) {
    return manager.removeResource(id, actor);
  }

  @Override
  public List<ScheduledMaintenance> deleteMany(ResourceRequest request, User actor) {
    return manager.removeResources(request, actor);
  }
}

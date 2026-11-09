package com.researchspace.maintenance.api.v2;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.CreateResult;
import com.researchspace.api.v2.resource.DeletePolicy;
import com.researchspace.api.v2.resource.IdempotencyPolicy;
import com.researchspace.api.v2.resource.MutationPolicy;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceDeleteOptions;
import com.researchspace.api.v2.resource.ResourceDeleteResult;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.resource.VersionPolicy;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.maintenance.service.MaintenanceOperationException;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.EnumSet;
import java.util.List;
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
            ApiV2ErrorMapping.forException(MaintenanceOperationException.class)
                .status(HttpStatus.BAD_REQUEST)
                .documentedError(
                    "errors.api.v2.maintenance.window", "The maintenance window is invalid.")
                .build());
    return ApiV2ResourceSpec.builder(ApiV2MaintenanceResource.DESCRIPTION, this, Long::valueOf)
        .mutationPolicy(
            new MutationPolicy(
                VersionPolicy.optional("version"), DeletePolicy.HARD, IdempotencyPolicy.NONE))
        .mutationErrors("errors.api.v2.invalidRequest", "errors.api.v2.maintenance.patch")
        .expose(EnumSet.allOf(ResourceOperation.class))
        .documentation(
            ResourceOperation.DELETE,
            OpenApiOperationDocumentation.builder()
                .responseDescription(204, "Maintenance permanently deleted.")
                .build())
        .errorMappings(ResourceOperation.CREATE, invalidWindow)
        .errorMappings(ResourceOperation.BULK_CREATE, invalidWindow)
        .errorMappings(ResourceOperation.UPDATE, invalidWindow)
        .errorMappings(ResourceOperation.BULK_UPDATE, invalidWindow)
        .mutationLimits(ApiV2MaintenanceResource.MUTATION_LIMITS)
        .tagDocumentation("Planned system maintenance and its access policy.", "Maintenance")
        .build();
  }

  @Override
  public ResourcePage<ScheduledMaintenance> find(ResourceRequest request, User subject) {
    return manager.getResources(request, subject);
  }

  @Override
  public long count(ResourceRequest request, User subject) {
    return manager.countResources(request, subject);
  }

  @Override
  public Optional<ScheduledMaintenance> findById(Long id, User subject) {
    return manager.getResource(id, subject);
  }

  @Override
  public CreateResult<ScheduledMaintenance> create(
      ParsedDocument document, ApiV2Caller caller, String idempotencyKey) {
    return new CreateResult<>(
        manager.createResource(
            ApiV2MaintenanceInput.from(document).toScheduledMaintenance(), caller.subject()),
        false);
  }

  @Override
  public List<ScheduledMaintenance> createMany(List<ParsedDocument> documents, ApiV2Caller caller) {
    return manager.createResources(
        documents.stream()
            .map(ApiV2MaintenanceInput::from)
            .map(ApiV2MaintenanceInput::toScheduledMaintenance)
            .toList(),
        caller.subject());
  }

  @Override
  public Optional<ScheduledMaintenance> update(
      Long id, ParsedDocument document, Long expectedVersion, ApiV2Caller caller) {
    return manager.updateResource(id, document, expectedVersion, caller.subject());
  }

  @Override
  public Optional<ResourceDeleteResult<ScheduledMaintenance>> delete(
      Long id, ResourceDeleteOptions options, ApiV2Caller caller) {
    return manager
        .removeResource(id, options.expectedVersion(), caller.subject())
        .map(ignored -> ResourceDeleteResult.permanentlyDeleted());
  }

  @Override
  public List<ScheduledMaintenance> updateMany(
      ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
    return manager.updateResources(request, document, caller.subject());
  }

  @Override
  public List<ScheduledMaintenance> deleteMany(ResourceRequest request, ApiV2Caller caller) {
    return manager.removeResources(request, caller.subject());
  }
}

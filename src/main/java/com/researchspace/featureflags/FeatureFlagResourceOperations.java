package com.researchspace.featureflags;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.InMemoryCollectionQuery;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/** Adapts REST API v2 feature flag operations to the feature flag manager. */
@Configuration(proxyBeanMethods = false)
public class FeatureFlagResourceOperations
    implements ResourceOperations<FeatureFlagResource, String> {

  private final FeatureFlagManager manager;
  private final InMemoryCollectionQuery<FeatureFlagResource> resourceQuery =
      new InMemoryCollectionQuery<>(ApiV2FeatureFlagResource.DESCRIPTION);

  public FeatureFlagResourceOperations(FeatureFlagManager manager) {
    this.manager = manager;
  }

  @Bean
  ApiV2ResourceSpec<FeatureFlagResource, String> featureFlagApiV2Resource() {
    List<ApiV2ErrorMapping> updateErrors =
        List.of(
            ApiV2ErrorMapping.of(
                FeatureFlagPermissionException.class,
                HttpStatus.FORBIDDEN,
                "api.v2.featureFlags.errors.notPermitted",
                "The caller cannot change this feature flag."),
            ApiV2ErrorMapping.of(
                FeatureFlagReadOnlyException.class,
                HttpStatus.CONFLICT,
                "api.v2.featureFlags.errors.readOnly",
                "The properties file controls this feature flag.",
                exception -> new Object[] {exception.getFlagName()}));
    return new ApiV2ResourceSpec<>(
        ApiV2FeatureFlagResource.DESCRIPTION,
        this,
        value -> value,
        "api.v2.featureFlags.errors.invalidPatch",
        "api.v2.featureFlags.errors.invalidPatch",
        EnumSet.of(
            ResourceOperation.LIST,
            ResourceOperation.COUNT,
            ResourceOperation.READ,
            ResourceOperation.UPDATE),
        Map.of(
            ResourceOperation.UPDATE,
            OpenApiOperationDocumentation.builder()
                .description(
                    "Changes the caller override for one feature flag. A real sysadmin can also"
                        + " change the instance baseline.")
                .requestExample(Map.of("overrideValue", true))
                .build()),
        Map.of(ResourceOperation.UPDATE, updateErrors));
  }

  @Override
  public ResourcePage<FeatureFlagResource> find(ResourceRequest request, User actor) {
    return resourceQuery.page(manager.getFeatureFlags(actor), request);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return resourceQuery.count(manager.getFeatureFlags(actor), request);
  }

  @Override
  public Optional<FeatureFlagResource> findById(String id, User actor) {
    return manager.getFeatureFlag(id, actor);
  }

  @Override
  public Optional<FeatureFlagResource> update(
      String id, ParsedDocument document, ApiV2Caller caller) {
    Map<String, Object> values = document.values();
    return manager.updateFeatureFlag(
        id,
        new Patch(
            (Boolean) values.get("baselineValue"),
            values.containsKey("overrideValue"),
            (Boolean) values.get("overrideValue")),
        caller.subject(),
        caller.actor());
  }
}

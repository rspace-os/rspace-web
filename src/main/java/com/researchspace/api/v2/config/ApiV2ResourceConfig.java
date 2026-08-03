package com.researchspace.api.v2.config;

import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import jakarta.servlet.ServletContext;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers collections whose standard CRUD routes are provided automatically.
 *
 * <p>Contributions are aggregated flatly rather than nested. A module exposes a collection by
 * declaring one {@link ApiV2ResourceSpec} bean in any scanned {@code @Configuration} and needs no
 * edit here: Spring collects every spec into the catalog. The component scan in {@code
 * applicationContext-service.xml} covers all of {@code com.researchspace}, so a sibling artifact on
 * the classpath is included automatically; a module outside that package prefix must have its
 * configuration imported explicitly.
 */
@Configuration
public class ApiV2ResourceConfig {

  /**
   * Builds the single registry and binds every resource spec to it.
   *
   * <p>Deliberately one flat catalog rather than one per module. It validates duplicate resource
   * names, duplicate entity types, and that every relationship target resolves — checks that are
   * only meaningful over one namespace. Nesting catalogs would either make a relationship spanning
   * two modules unverifiable or push that check to the first request, losing the fail-at-startup
   * guarantee.
   *
   * <p>Because this is now a bean rather than a static constant, an invalid graph surfaces during
   * context refresh wrapped in a {@code BeanCreationException} instead of as a bare {@code
   * IllegalArgumentException} at class initialisation. It is still a startup failure, but look for
   * the cause when diagnosing one.
   */
  @Bean
  ApiV2ResourceCatalog apiV2ResourceCatalog(
      List<ApiV2ResourceSpec<?, ?>> specs,
      List<ApiV2RelationshipTargetSpec<?, ?>> relationshipTargets) {
    return new ApiV2ResourceCatalog(specs, relationshipTargets);
  }

  @Bean
  ApiV2OpenApiGenerator apiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog, ObjectProvider<ServletContext> servletContext) {
    String contextPath =
        servletContext.stream()
            .findFirst()
            .map(ServletContext::getContextPath)
            .filter(path -> !path.isBlank())
            .orElse("/");
    return new ApiV2OpenApiGenerator(catalog, "RSpace REST API v2", "2.0.0", contextPath);
  }

  @Bean
  ApiV2OpenApiDocumentService apiV2OpenApiDocumentService(
      ApiV2OpenApiGenerator generator,
      ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
    return new ApiV2OpenApiDocumentService(generator, handlerMappings::orderedStream);
  }
}

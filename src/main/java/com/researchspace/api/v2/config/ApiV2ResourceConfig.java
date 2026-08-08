package com.researchspace.api.v2.config;

import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.controller.ConfigV2Controller;
import com.researchspace.api.v2.controller.OAuthTokensV2Controller;
import com.researchspace.api.v2.openapi.ApiV2OpenApiController;
import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2EndpointSpec;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.model.collection.AccessFunction;
import jakarta.servlet.ServletContext;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Registers REST API v2 resources, endpoints, and OpenAPI services. */
@Configuration
public class ApiV2ResourceConfig {

  private static final AccessFunction CRUD_ENDPOINT_ACCESS =
      AccessFunction.documented(
          AccessFunction.anyone().documentation().orElseThrow(),
          context ->
              context.operation() == com.researchspace.model.collection.AccessContext.Operation.READ
                  ? com.researchspace.model.collection.AccessResult.allowed()
                  : AccessFunction.authenticated().check(context));

  @Bean
  ApiV2ResourceCatalog apiV2ResourceCatalog(
      List<ApiV2ResourceSpec<?, ?>> specs,
      List<ApiV2RelationshipTargetSpec<?, ?>> relationshipTargets) {
    return new ApiV2ResourceCatalog(specs, relationshipTargets);
  }

  @Bean
  ApiV2EndpointCatalog apiV2EndpointCatalog() {
    return new ApiV2EndpointCatalog(
        List.of(
            // Reads may be public for resources such as maintenance notices. Every mutation
            // requires authentication before Spring materializes its request body; the resource
            // policy subsequently applies the more specific role/row authorization.
            new ApiV2EndpointSpec(ApiV2CrudController.class, CRUD_ENDPOINT_ACCESS),
            new ApiV2EndpointSpec(ConfigV2Controller.class, AccessFunction.anyone()),
            new ApiV2EndpointSpec(
                OAuthTokensV2Controller.class,
                AccessFunction.authenticated(),
                ApiV2AuthenticationMode.BROWSER_SESSION),
            new ApiV2EndpointSpec(ApiV2OpenApiController.class, AccessFunction.anyone())));
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
      ApiV2EndpointCatalog endpoints,
      ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
    return new ApiV2OpenApiDocumentService(generator, endpoints, handlerMappings::orderedStream);
  }
}

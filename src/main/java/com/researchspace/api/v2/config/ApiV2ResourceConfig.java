package com.researchspace.api.v2.config;

import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.controller.ConfigV2Controller;
import com.researchspace.api.v2.controller.OAuthTokensV2Controller;
import com.researchspace.api.v2.openapi.ApiV2OpenApiController;
import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.openapi.ApiV2OpenApiText;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2EndpointSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.service.ResourceMutationManager;
import jakarta.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
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
              context.operation() == AccessContext.Operation.READ
                  ? AccessResult.allowed()
                  : AccessFunction.authenticated().check(context));

  @Bean
  ApiV2ResourceCatalog apiV2ResourceCatalog(
      List<ApiV2ResourceSpec<?, ?>> specs, ResourceMutationManager mutations) {
    return new ApiV2ResourceCatalog(specs, mutations);
  }

  /** Shares the catalog's validated relationship graph with collection persistence adapters. */
  @Bean
  ResourceRegistry apiV2ResourceRegistry(ApiV2ResourceCatalog catalog) {
    return catalog.registry();
  }

  /**
   * Sets who may call each REST API v2 controller.
   *
   * <p>The four rules below are for the API's own controllers, so they are written here. Do not
   * move them into separate beans. Writing them here means they always exist when this catalog
   * exists. A controller with no rule quietly gets the default rule: sign in with API credentials.
   * Public endpoints would then ask for a login, and the token page, which uses the browser login,
   * would stop working.
   *
   * <p>Features may add their own rules as {@link ApiV2EndpointSpec} beans. When the app starts,
   * Spring puts every one of those beans into {@code contributions}. Shared API code must not name
   * a feature, so feature rules arrive this way instead of being listed here.
   */
  @Bean
  ApiV2EndpointCatalog apiV2EndpointCatalog(List<ApiV2EndpointSpec> contributions) {
    var specs =
        new ArrayList<>(
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
    specs.addAll(contributions);
    return new ApiV2EndpointCatalog(specs);
  }

  @Bean
  ApiV2OpenApiText apiV2OpenApiText(MessageSource messageSource) {
    return new ApiV2OpenApiText(messageSource);
  }

  @Bean
  ApiV2OpenApiGenerator apiV2OpenApiGenerator(
      ApiV2ResourceCatalog catalog,
      ApiV2OpenApiText text,
      ObjectProvider<ServletContext> servletContext) {
    String contextPath =
        servletContext.stream()
            .findFirst()
            .map(ServletContext::getContextPath)
            .filter(path -> !path.isBlank())
            .orElse("/");
    return new ApiV2OpenApiGenerator(
        catalog, text, text.get(ApiV2OpenApiText.Key.INFO_TITLE), "2.0.0", contextPath);
  }

  @Bean
  ApiV2OpenApiDocumentService apiV2OpenApiDocumentService(
      ApiV2OpenApiGenerator generator,
      ApiV2EndpointCatalog endpoints,
      ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
    return new ApiV2OpenApiDocumentService(generator, endpoints, handlerMappings::orderedStream);
  }
}

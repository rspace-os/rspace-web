package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.openapi.ApiV2OpenApiTestText;
import com.researchspace.api.v2.openapi.ApiV2TestHandlerMappings;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.model.collection.AccessDocumentation;
import io.swagger.v3.core.util.Json31;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiSecurityTest {

  @Test
  void controllerSecurityMatchesTheRuntimeEndpointCatalog() {
    var endpoints = new ApiV2ResourceConfig().apiV2EndpointCatalog();
    try (var handlers = ApiV2TestHandlerMappings.newContext()) {
      var mapping = ApiV2TestHandlerMappings.mapping(handlers);
      var generator =
          new ApiV2OpenApiGenerator(
              new ApiV2ResourceCatalog(List.of()), ApiV2OpenApiTestText.english(), "API", "2.0.0");
      var document =
          Json31.mapper()
              .valueToTree(
                  new ApiV2OpenApiDocumentService(generator, endpoints, () -> Stream.of(mapping))
                      .generate());
      int checked = 0;
      for (var entry : mapping.getHandlerMethods().entrySet()) {
        for (String path : entry.getKey().getPatternValues()) {
          for (var method : entry.getKey().getMethodsCondition().getMethods()) {
            var operation =
                document.path("paths").path(path).path(method.name().toLowerCase(Locale.ROOT));
            if (operation.isMissingNode()) continue;
            var handler = entry.getValue();
            var expected =
                endpoints.isPublic(handler)
                    ? List.of()
                    : endpoints.authenticationMode(handler)
                            == ApiV2AuthenticationMode.BROWSER_SESSION
                        ? List.of(Map.of("browserSession", List.of()))
                        : List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of()));
            assertEquals(
                Json31.mapper().valueToTree(expected),
                operation.path("security"),
                method + " " + path);
            checked++;
          }
        }
      }
      assertTrue(checked > 0);
    }
  }

  @Test
  void resourceReadSecurityMatchesTheRegisteredAccessPolicies() {
    try (var context = ApiV2ResourceConfigTest.newContext()) {
      context.register(ApiV2ResourceConfig.class);
      context.refresh();
      var catalog = context.getBean(ApiV2ResourceCatalog.class);
      var document =
          Json31.mapper()
              .valueToTree(
                  new ApiV2OpenApiGenerator(
                          catalog,
                          com.researchspace.api.v2.openapi.ApiV2OpenApiTestText.english(),
                          "API",
                          "2.0.0")
                      .generate());
      for (var resource : catalog.routableResources()) {
        boolean anonymous =
            resource.description().schema().access().readAccess().authenticationRequirement()
                == AccessDocumentation.AuthenticationRequirement.PUBLIC;
        String root = "/api/v2/" + resource.resourceName();
        for (String path : List.of(root, root + "/count", root + "/{id}")) {
          var operation = document.path("paths").path(path).path("get");
          assertEquals(anonymous, operation.path("security").isEmpty(), path);
          assertTrue(operation.has("security"), path);
        }
      }
    }
  }
}

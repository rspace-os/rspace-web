package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.openapi.ApiV2TestHandlerMappings;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.core.util.Json31;
import java.util.List;
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
          new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "API", "2.0.0");
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
                document
                    .path("paths")
                    .path(path)
                    .path(method.name().toLowerCase(java.util.Locale.ROOT));
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
}

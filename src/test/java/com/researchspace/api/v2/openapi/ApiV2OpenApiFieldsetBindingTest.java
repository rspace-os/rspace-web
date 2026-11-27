package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ApiV2OpenApiFieldsetBindingTest {

  @Test
  void documentsBoundFieldsetsAsDeepObjectsWithoutInventingCollectionControls() throws Exception {
    var parameters = parameters("projection", ApiV2FieldsetQuery.class);
    assertEquals(
        List.of("fields", "exclude"), parameters.stream().map(p -> p.get("name")).toList());
    for (var parameter : parameters) {
      assertEquals("query", parameter.get("in"));
      assertEquals("deepObject", parameter.get("style"));
      assertEquals(true, parameter.get("explode"));
      assertEquals(true, parameter.get("x-rspace-comma-separated-values"));
      assertEquals(
          Map.of("type", "object", "additionalProperties", Map.of("type", "string")),
          parameter.get("schema"));
    }
  }

  @Test
  void preservesControlsThatTheHandlerActuallyBinds() throws Exception {
    var parameters =
        parameters("projectionWithControls", ApiV2FieldsetQuery.class, int.class, boolean.class);
    assertEquals(
        List.of("fields", "exclude", "depth", "totals"),
        parameters.stream().map(p -> p.get("name")).toList());
  }

  @Test
  void doesNotAddFieldsetsToOtherHandlers() throws Exception {
    assertTrue(parameters("ordinary").isEmpty());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> parameters(String method, Class<?>... types)
      throws Exception {
    var mapping = mock(RequestMappingHandlerMapping.class);
    when(mapping.getHandlerMethods())
        .thenReturn(
            Map.of(
                RequestMappingInfo.paths("/api/v2/projection").methods(RequestMethod.GET).build(),
                new HandlerMethod(new Controller(), Controller.class.getMethod(method, types))));
    var document =
        new ApiV2OpenApiDocumentService(
                new ApiV2OpenApiGenerator(
                    new ApiV2ResourceCatalog(List.of()),
                    com.researchspace.api.v2.openapi.ApiV2OpenApiTestText.english(),
                    "API",
                    "2.0.0"),
                new ApiV2EndpointCatalog(List.of()),
                () -> Stream.of(mapping))
            .generate();
    var paths = (Map<String, Object>) document.get("paths");
    var path = (Map<String, Object>) paths.get("/api/v2/projection");
    return (List<Map<String, Object>>)
        ((Map<String, Object>) path.get("get")).getOrDefault("parameters", List.of());
  }

  @Tag(
      name = "projection",
      description = "Field projection test operations.",
      extensions =
          @Extension(properties = @ExtensionProperty(name = "x-tag-group", value = "Test")))
  private static class Controller {
    @Operation(operationId = "projection")
    public String projection(@ModelAttribute ApiV2FieldsetQuery fieldsets) {
      return "";
    }

    @Operation(operationId = "projectionWithControls")
    public String projectionWithControls(
        @ModelAttribute ApiV2FieldsetQuery fieldsets,
        @RequestParam(defaultValue = "0") int depth,
        @RequestParam(defaultValue = "false") boolean totals) {
      return "";
    }

    @Operation(operationId = "ordinary")
    public String ordinary() {
      return "";
    }
  }
}

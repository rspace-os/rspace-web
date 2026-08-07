package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.controller.ConfigV2Controller;
import com.researchspace.api.v2.controller.OAuthTokensV2Controller;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2EndpointSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.OAuthTokenManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ApiV2OpenApiAnnotationMergerTest {

  @Test
  void mergesConcretePublicControllerMetadataIntoTheGeneratedDocument() throws Exception {
    ApiV2OpenApiGenerator generator =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0");
    ConfigV2Controller controller = new ConfigV2Controller(mock(IPropertyHolder.class));
    Method method = ConfigV2Controller.class.getMethod("getConfig");
    RequestMappingInfo mapping =
        RequestMappingInfo.paths("/api/v2/config").methods(RequestMethod.GET).build();

    RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(mapping, new HandlerMethod(controller, method)));
    Map<String, Object> document =
        new ApiV2OpenApiDocumentService(
                generator,
                new ApiV2EndpointCatalog(
                    List.of(
                        new ApiV2EndpointSpec(ConfigV2Controller.class, AccessFunction.anyone()))),
                () -> Stream.of(handlerMapping))
            .generate();

    Map<String, Object> paths = objectMap(document.get("paths"));
    Map<String, Object> operation = objectMap(objectMap(paths.get("/api/v2/config")).get("get"));
    assertEquals("getApiV2Config", operation.get("operationId"));
    assertEquals(List.of(), operation.get("security"));
    assertTrue(schemas(document).containsKey("ApiV2Config"));
    Map<String, Object> configSchema = objectMap(schemas(document).get("ApiV2Config"));
    Map<String, Object> configProperties = objectMap(configSchema.get("properties"));
    assertEquals("email", objectMap(configProperties.get("deploymentHelpEmail")).get("format"));
    assertTrue(((List<?>) configSchema.get("required")).contains("deploymentHelpEmail"));
    assertEquals(
        List.of("string", "null"),
        objectMap(configProperties.get("deploymentHelpEmail")).get("type"));
    assertTrue(schemas(document).containsKey("Branding"));
  }

  @Test
  void mergesParametersRequestBodiesAndExplicitResponseSchemas() throws Exception {
    ApiV2OpenApiGenerator generator =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0");
    CustomController controller = new CustomController();
    Method method =
        CustomController.class.getMethod("create", long.class, String.class, CustomInput.class);
    RequestMappingInfo mapping =
        RequestMappingInfo.paths("/api/v2/things/{id}").methods(RequestMethod.POST).build();
    RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(mapping, new HandlerMethod(controller, method)));

    Map<String, Object> document =
        new ApiV2OpenApiDocumentService(
                generator, new ApiV2EndpointCatalog(List.of()), () -> Stream.of(handlerMapping))
            .generate();
    Map<String, Object> operation =
        objectMap(
            objectMap(objectMap(document.get("paths")).get("/api/v2/things/{id}")).get("post"));

    List<Map<String, Object>> parameters = objectMapList(operation.get("parameters"));
    assertEquals(
        List.of("id", "mode"), parameters.stream().map(value -> value.get("name")).toList());
    assertEquals(true, parameters.get(0).get("required"));
    assertTrue(objectMap(operation.get("requestBody")).containsKey("content"));
    assertEquals(
        List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of())),
        operation.get("security"));
    assertTrue(schemas(document).containsKey("CustomInput"));
    assertTrue(schemas(document).containsKey("CustomOutput"));
  }

  @Test
  void documentsBrowserSessionAuthenticationOnlyForTheUiTokenEndpoint() throws Exception {
    ApiV2OpenApiGenerator generator =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0");
    OAuthTokensV2Controller controller = new OAuthTokensV2Controller(mock(OAuthTokenManager.class));
    Method method = OAuthTokensV2Controller.class.getMethod("createToken", User.class);
    RequestMappingInfo mapping =
        RequestMappingInfo.paths("/api/v2/oauth/tokens").methods(RequestMethod.POST).build();
    RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(mapping, new HandlerMethod(controller, method)));

    Map<String, Object> document =
        new ApiV2OpenApiDocumentService(
                generator,
                new ApiV2EndpointCatalog(
                    List.of(
                        new ApiV2EndpointSpec(
                            OAuthTokensV2Controller.class,
                            AccessFunction.authenticated(),
                            ApiV2AuthenticationMode.BROWSER_SESSION))),
                () -> Stream.of(handlerMapping))
            .generate();

    Map<String, Object> operation =
        objectMap(
            objectMap(objectMap(document.get("paths")).get("/api/v2/oauth/tokens")).get("post"));
    assertEquals(List.of(Map.of("browserSession", List.of())), operation.get("security"));
  }

  private static final class CustomController {
    @PostMapping
    @Operation(
        operationId = "createCustomThing",
        summary = "Create custom thing",
        requestBody =
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Developer-supplied body documentation.",
                required = true),
        responses =
            @ApiResponse(
                responseCode = "201",
                description = "Created.",
                content = @Content(schema = @Schema(implementation = CustomOutput.class))))
    public CustomOutput create(
        @PathVariable long id, @RequestParam String mode, @RequestBody CustomInput input) {
      return new CustomOutput(id, input.name());
    }
  }

  private record CustomInput(@Schema(description = "Name to assign.", minLength = 1) String name) {}

  private record CustomOutput(long id, String name) {}

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return objectMap(objectMap(document.get("components")).get("schemas"));
  }

  private static Map<String, Object> objectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
      throw new AssertionError("Expected an object with string keys");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, item) -> result.put((String) key, item));
    return result;
  }

  private static List<Map<String, Object>> objectMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      throw new AssertionError("Expected an array");
    }
    return list.stream().map(ApiV2OpenApiAnnotationMergerTest::objectMap).toList();
  }
}

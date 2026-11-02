package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ConfigV2Controller;
import com.researchspace.api.v2.controller.OAuthTokensV2Controller;
import com.researchspace.api.v2.model.ApiV2Config;
import com.researchspace.api.v2.model.ApiV2CurrentUser;
import com.researchspace.api.v2.model.ApiV2UiToken;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2EndpointSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.OAuthTokenManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  void rejectsUndocumentedV2HandlersButSkipsHiddenAndUnrelatedHandlers() throws Exception {
    var generator = new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "API", "2.0.0");
    var endpoints = new ApiV2EndpointCatalog(List.of());
    var handlerMapping = mock(RequestMappingHandlerMapping.class);
    var handler = new HandlerMethod(new UndocumentedController(), "missing");
    var mapping = RequestMappingInfo.paths("/api/v2/missing").methods(RequestMethod.GET).build();
    when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(mapping, handler));
    var documents =
        new ApiV2OpenApiDocumentService(generator, endpoints, () -> Stream.of(handlerMapping));
    var error = assertThrows(IllegalStateException.class, documents::generate);
    assertTrue(error.getMessage().contains("missing"));
    var hidden = new HandlerMethod(new UndocumentedController(), "hidden");
    var unrelated = RequestMappingInfo.paths("/api/v1/missing").methods(RequestMethod.GET).build();
    when(handlerMapping.getHandlerMethods())
        .thenReturn(Map.of(mapping, hidden, unrelated, handler));
    assertTrue(objectMap(documents.generate().get("paths")).isEmpty());
  }

  private static final class UndocumentedController {
    public void missing() {}

    @io.swagger.v3.oas.annotations.Hidden
    public void hidden() {}
  }

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
  void generatedDtoSchemasPreserveRequiredFieldsAndNullableMetadata() throws Exception {
    SchemaController controller = new SchemaController();
    RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
    when(handlerMapping.getHandlerMethods())
        .thenReturn(
            Map.of(
                RequestMappingInfo.paths("/api/v2/schema/config")
                    .methods(RequestMethod.GET)
                    .build(),
                new HandlerMethod(controller, SchemaController.class.getMethod("config")),
                RequestMappingInfo.paths("/api/v2/schema/current-user")
                    .methods(RequestMethod.GET)
                    .build(),
                new HandlerMethod(controller, SchemaController.class.getMethod("currentUser")),
                RequestMappingInfo.paths("/api/v2/schema/ui-token")
                    .methods(RequestMethod.GET)
                    .build(),
                new HandlerMethod(controller, SchemaController.class.getMethod("uiToken"))));

    Map<String, Object> document =
        new ApiV2OpenApiDocumentService(
                new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0"),
                new ApiV2EndpointCatalog(List.of()),
                () -> Stream.of(handlerMapping))
            .generate();
    Map<String, Object> schemas = schemas(document);

    assertRequiredProperties(
        schemas,
        "ApiV2Config",
        Set.of("version", "branding", "helpLinks", "deploymentDescription", "deploymentHelpEmail"));
    assertRequiredProperties(schemas, "Branding", Set.of("bannerImageUrl"));
    assertRequiredProperties(schemas, "HelpLink", Set.of("label", "url"));
    assertRequiredProperties(
        schemas,
        "ApiV2CurrentUser",
        Set.of(
            "id",
            "username",
            "email",
            "firstName",
            "lastName",
            "homeFolderId",
            "workbenchId",
            "hasPiRole",
            "hasSysAdminRole",
            "profileImageUrl",
            "profileImageApiUrl",
            "orcid",
            "capabilities",
            "livechat",
            "session"));
    assertRequiredProperties(schemas, "Orcid", Set.of("available", "id"));
    assertRequiredProperties(
        schemas, "Capabilities", Set.of("canUseInventory", "canPublish", "canViewSystem"));
    assertRequiredProperties(schemas, "LiveChat", Set.of("enabled", "serverKey"));
    assertRequiredProperties(
        schemas,
        "Session",
        Set.of(
            "operatedAs",
            "lastSession",
            "canUseDevtools",
            "canOverrideFeatureFlags",
            "canChangeFeatureFlagBaselines"));
    assertRequiredProperties(schemas, "ApiV2UiToken", Set.of("accessToken"));

    assertEquals(
        "RSpace application version.",
        property(schemas, "ApiV2Config", "version").get("description"));
    assertEquals("v2.24.0", property(schemas, "ApiV2Config", "version").get("example"));
    assertEquals(
        List.of("string", "null"),
        property(schemas, "ApiV2Config", "deploymentDescription").get("type"));
    assertEquals(
        List.of("string", "null"),
        property(schemas, "ApiV2Config", "deploymentHelpEmail").get("type"));
    assertEquals("email", property(schemas, "ApiV2Config", "deploymentHelpEmail").get("format"));
    assertEquals(
        "support@example.org",
        property(schemas, "ApiV2Config", "deploymentHelpEmail").get("example"));
    assertEquals("uri-reference", property(schemas, "Branding", "bannerImageUrl").get("format"));
    assertEquals("/public/banner", property(schemas, "Branding", "bannerImageUrl").get("example"));
    assertEquals("Help", property(schemas, "HelpLink", "label").get("example"));
    assertEquals("uri-reference", property(schemas, "HelpLink", "url").get("format"));

    assertEquals(
        "Stable user identifier.", property(schemas, "ApiV2CurrentUser", "id").get("description"));
    assertEquals("email", property(schemas, "ApiV2CurrentUser", "email").get("format"));
    assertEquals(
        List.of("integer", "null"),
        property(schemas, "ApiV2CurrentUser", "homeFolderId").get("type"));
    assertEquals("int64", property(schemas, "ApiV2CurrentUser", "homeFolderId").get("format"));
    assertEquals(
        List.of("integer", "null"),
        property(schemas, "ApiV2CurrentUser", "workbenchId").get("type"));
    assertEquals(
        List.of("string", "null"),
        property(schemas, "ApiV2CurrentUser", "profileImageUrl").get("type"));
    assertEquals(
        "uri-reference", property(schemas, "ApiV2CurrentUser", "profileImageUrl").get("format"));
    assertEquals(
        List.of("string", "null"),
        property(schemas, "ApiV2CurrentUser", "profileImageApiUrl").get("type"));
    assertEquals(
        "uri-reference", property(schemas, "ApiV2CurrentUser", "profileImageApiUrl").get("format"));

    assertEquals(List.of("string", "null"), property(schemas, "Orcid", "id").get("type"));
    assertEquals(
        "^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$", property(schemas, "Orcid", "id").get("pattern"));
    assertEquals("0000-0002-1825-0097", property(schemas, "Orcid", "id").get("example"));
    assertEquals(List.of("string", "null"), property(schemas, "LiveChat", "serverKey").get("type"));
    assertEquals(
        List.of("string", "null"), property(schemas, "Session", "lastSession").get("type"));
    assertEquals("date-time", property(schemas, "Session", "lastSession").get("format"));
    assertEquals(
        "2026-08-01T20:00:00Z", property(schemas, "Session", "lastSession").get("example"));
    assertEquals(
        "OAuth bearer token for REST API requests.",
        property(schemas, "ApiV2UiToken", "accessToken").get("description"));
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
    Method method =
        OAuthTokensV2Controller.class.getMethod(
            "createToken", ApiV2Caller.class, HttpServletRequest.class);
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

  private static final class SchemaController {
    @Operation(
        operationId = "schemaConfig",
        summary = "Config schema",
        responses = @ApiResponse(responseCode = "200", description = "OK"))
    public ApiV2Config config() {
      return null;
    }

    @Operation(
        operationId = "schemaCurrentUser",
        summary = "Current user schema",
        responses = @ApiResponse(responseCode = "200", description = "OK"))
    public ApiV2CurrentUser currentUser() {
      return null;
    }

    @Operation(
        operationId = "schemaUiToken",
        summary = "UI token schema",
        responses = @ApiResponse(responseCode = "200", description = "OK"))
    public ApiV2UiToken uiToken() {
      return null;
    }
  }

  private record CustomInput(@Schema(description = "Name to assign.", minLength = 1) String name) {}

  private record CustomOutput(long id, String name) {}

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return objectMap(objectMap(document.get("components")).get("schemas"));
  }

  private static void assertRequiredProperties(
      Map<String, Object> schemas, String name, Set<String> expected) {
    List<?> required = (List<?>) objectMap(schemas.get(name)).get("required");
    assertEquals(expected, Set.copyOf(required), name);
  }

  private static Map<String, Object> property(
      Map<String, Object> schemas, String component, String name) {
    return objectMap(objectMap(objectMap(schemas.get(component)).get("properties")).get(name));
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

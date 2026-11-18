package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.model.resourceaccess.ResourceRoleSourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class ApiV2OpenApiSchemasTest {

  record Child(String name) {}

  record Document(
      Optional<String> role,
      @JsonInclude(JsonInclude.Include.NON_NULL) Optional<String> includedEmptyRole,
      @JsonInclude(JsonInclude.Include.NON_ABSENT)
          @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
          Optional<String> omittedRole,
      @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
          String detail,
      @Schema(nullable = true) String nullableText,
      @Schema(nullable = true) Child child,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title) {}

  static final class Clash {
    record Name(String first) {}
  }

  record ClashName(String second) {}

  @Test
  void rejectsTwoTypesWithOneSchemaName() {
    Map<String, Object> components = new LinkedHashMap<>();
    ApiV2OpenApiSchemas.schemaFor(Clash.Name.class, components);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ApiV2OpenApiSchemas.schemaFor(ClashName.class, components));
    assertTrue(error.getMessage().contains("ApiV2OpenApiSchemasTestClashName"), error.getMessage());
  }

  @Test
  void responseSchemasMatchJacksonEmptyOptionalsAndOmittedNulls() throws Exception {
    var mapper = new MappingJackson2HttpMessageConverter().getObjectMapper();
    var json =
        mapper.valueToTree(
            new Document(
                Optional.empty(), Optional.empty(), Optional.empty(), null, null, null, "Title"));
    assertTrue(json.has("role"));
    assertTrue(json.path("role").isNull());
    assertTrue(json.path("includedEmptyRole").isNull());
    assertFalse(json.has("omittedRole"));
    assertFalse(json.has("detail"));
    assertTrue(json.path("nullableText").isNull());
    assertTrue(json.path("child").isNull());

    Map<String, Object> components = new LinkedHashMap<>();
    ApiV2OpenApiSchemas.responseSchemaFor(Document.class, components);
    Map<String, Object> schema = map(components.get("ApiV2OpenApiSchemasTestDocument"));
    Map<String, Object> properties = map(schema.get("properties"));
    assertEquals(List.of("string", "null"), map(properties.get("role")).get("type"));
    assertEquals(List.of("string", "null"), map(properties.get("includedEmptyRole")).get("type"));
    assertEquals("string", map(properties.get("omittedRole")).get("type"));
    assertEquals("string", map(properties.get("detail")).get("type"));
    assertEquals(List.of("string", "null"), map(properties.get("nullableText")).get("type"));
    assertTrue(
        ((List<?>) map(properties.get("child")).get("anyOf")).contains(Map.of("type", "null")));
    assertEquals(
        List.of("title", "role", "includedEmptyRole", "nullableText", "child"),
        schema.get("required"));
  }

  record Sample(
      String name,
      @Schema(nullable = true) String note,
      int count,
      @JsonInclude(JsonInclude.Include.NON_NULL) Long total,
      @JsonInclude(JsonInclude.Include.NON_NULL) boolean flag,
      @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String hint,
      ResourceRoleSource source) {}

  @Test
  void requiresExactlyTheKeysTheWireMapperAlwaysWrites() {
    var mapper = new MappingJackson2HttpMessageConverter().getObjectMapper();
    var sample =
        new Sample(
            "name",
            null,
            0,
            null,
            false,
            "hint",
            new ResourceRoleSource(
                ResourceRoleSourceKind.GROUP, "editor", ResourceGranteeKeys.group(7), "Group"));
    var json = mapper.valueToTree(sample);
    Map<String, Object> components = new LinkedHashMap<>();
    ApiV2OpenApiSchemas.responseSchemaFor(Sample.class, components);
    Map<String, Object> schema = map(components.get("ApiV2OpenApiSchemasTestSample"));
    List<?> required = (List<?>) schema.get("required");

    assertEquals(List.of("name", "note", "count", "flag", "source"), required);
    for (Object name : required) {
      assertTrue(json.has((String) name), String.valueOf(name));
    }
    assertTrue(json.path("note").isNull());
    assertFalse(json.has("total"));

    Map<String, Object> source = map(components.get("ResourceRoleSource"));
    assertEquals(List.of("kind", "role", "grantee"), source.get("required"));
    Map<String, Object> grantee = map(components.get("ResourceRoleSourceGrantee"));
    assertEquals(List.of("kind", "key", "name"), grantee.get("required"));
    assertEquals("GROUP", json.path("source").path("grantee").path("kind").asText());
    assertEquals(7, json.path("source").path("grantee").path("id").asInt());
    Set<String> granteeKeys = new HashSet<>();
    json.path("source").path("grantee").fieldNames().forEachRemaining(granteeKeys::add);
    assertEquals(Set.of("kind", "key", "id", "name"), granteeKeys);
    var direct =
        mapper.valueToTree(new ResourceRoleSource(ResourceRoleSourceKind.DIRECT, "editor", "", ""));
    assertTrue(direct.has("grantee"));
    assertTrue(direct.path("grantee").isNull());
  }

  record SharedRow(String label, @Schema(nullable = true) String note) {}

  record RequiredRow(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer size) {}

  @Test
  void rejectsResponseRequirementsOnAComponentThatARequestBodyReaches() {
    Map<String, Object> components = new LinkedHashMap<>();
    Map<String, Object> request = ApiV2OpenApiSchemas.schemaFor(SharedRow.class, components);
    ApiV2OpenApiSchemas.responseSchemaFor(SharedRow.class, components);
    Map<String, Object> paths = pathWithRequestBody(request);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ApiV2OpenApiSchemas.requireResponseOnlyRequired(paths, components));
    assertTrue(error.getMessage().contains("ApiV2OpenApiSchemasTestSharedRow"), error.getMessage());
  }

  @Test
  void acceptsSharedComponentsThatAreAlreadyRequiredAndRemovesTheMarkers() {
    Map<String, Object> components = new LinkedHashMap<>();
    Map<String, Object> request = ApiV2OpenApiSchemas.schemaFor(RequiredRow.class, components);
    ApiV2OpenApiSchemas.responseSchemaFor(RequiredRow.class, components);
    ApiV2OpenApiSchemas.responseSchemaFor(Sample.class, components);

    ApiV2OpenApiSchemas.requireResponseOnlyRequired(pathWithRequestBody(request), components);
    assertEquals(
        List.of("label", "size"),
        map(components.get("ApiV2OpenApiSchemasTestRequiredRow")).get("required"));
    assertFalse(
        map(components.get("ApiV2OpenApiSchemasTestSample"))
            .containsKey(ApiV2OpenApiSchemas.RESPONSE_REQUIRED));
  }

  static final class Views {
    static final class Public {}
  }

  record ViewedRow(String label, @JsonView(Views.Public.class) String secret) {}

  record OptionalViewedRow(
      String label,
      @JsonView(Views.Public.class) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          String secret) {}

  @JsonFilter("audience")
  record FilteredRow(String label) {}

  @Test
  void rejectsPropertiesOmittedPerCallerUnlessExplicitlyOptional() {
    IllegalStateException viewed =
        assertThrows(
            IllegalStateException.class,
            () -> ApiV2OpenApiSchemas.responseSchemaFor(ViewedRow.class, new LinkedHashMap<>()));
    assertTrue(viewed.getMessage().contains("ViewedRow.secret"), viewed.getMessage());
    assertTrue(viewed.getMessage().contains("NOT_REQUIRED"), viewed.getMessage());
    assertThrows(
        IllegalStateException.class,
        () -> ApiV2OpenApiSchemas.responseSchemaFor(FilteredRow.class, new LinkedHashMap<>()));

    Map<String, Object> components = new LinkedHashMap<>();
    ApiV2OpenApiSchemas.responseSchemaFor(OptionalViewedRow.class, components);
    assertEquals(
        List.of("label"),
        map(components.get("ApiV2OpenApiSchemasTestOptionalViewedRow")).get("required"));
  }

  static final class WidgetController {
    record Item(String name) {}

    @Schema(name = "ApiV2OpenApiSchemasTestNamedWidget")
    record Named(String name) {}
  }

  @Schema(name = "ApiV2OpenApiSchemasTestTwin")
  record TwinOne(String one) {}

  @Schema(name = "ApiV2OpenApiSchemasTestTwin")
  record TwinTwo(String two) {}

  static final class Derived {
    record Name(String value) {}
  }

  @Schema(name = "ApiV2OpenApiSchemasTestDerivedName")
  record ExplicitDerivedName(String other) {}

  @Test
  void requiresExplicitNamesForTypesNestedInControllersOrManagers() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                ApiV2OpenApiSchemas.schemaFor(WidgetController.Item.class, new LinkedHashMap<>()));
    assertTrue(error.getMessage().contains("WidgetController"), error.getMessage());

    Map<String, Object> components = new LinkedHashMap<>();
    ApiV2OpenApiSchemas.schemaFor(WidgetController.Named.class, components);
    assertTrue(components.containsKey("ApiV2OpenApiSchemasTestNamedWidget"));
  }

  @Test
  void rejectsTwoTypesWithOneExplicitName() {
    ApiV2OpenApiSchemas.schemaFor(TwinOne.class, new LinkedHashMap<>());
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ApiV2OpenApiSchemas.schemaFor(TwinTwo.class, new LinkedHashMap<>()));
    assertTrue(error.getMessage().contains("ApiV2OpenApiSchemasTestTwin"), error.getMessage());
  }

  @Test
  void rejectsAnExplicitNameEqualToAnotherTypesDerivedName() {
    ApiV2OpenApiSchemas.schemaFor(Derived.Name.class, new LinkedHashMap<>());
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> ApiV2OpenApiSchemas.schemaFor(ExplicitDerivedName.class, new LinkedHashMap<>()));
    assertTrue(
        error.getMessage().contains("ApiV2OpenApiSchemasTestDerivedName"), error.getMessage());
  }

  private static Map<String, Object> pathWithRequestBody(Map<String, Object> schema) {
    return Map.of(
        "/rows",
        Map.of(
            "post",
            Map.of(
                "requestBody",
                Map.of("content", Map.of("application/json", Map.of("schema", schema))))));
  }

  @Test
  void requestSchemasRetainTheirDeclaredNullability() {
    Map<String, Object> components = new LinkedHashMap<>();
    ApiV2OpenApiSchemas.schemaFor(Document.class, components);
    Map<String, Object> schema = map(components.get("ApiV2OpenApiSchemasTestDocument"));
    assertEquals(
        List.of("string", "null"), map(map(schema.get("properties")).get("detail")).get("type"));
    assertTrue(((List<?>) schema.get("required")).contains("detail"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }
}

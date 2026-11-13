package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    assertEquals(List.of("title"), schema.get("required"));
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

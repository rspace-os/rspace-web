package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiValidatorTest {

  private Map<String, Object> document;

  @BeforeEach
  void setUp() {
    document =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0")
            .generate();
  }

  @Test
  void rejectsDuplicateOperationIds() {
    Map<String, Object> paths = objectMap(document.get("paths"));
    paths.put("/one", Map.of("get", operation("duplicate")));
    paths.put("/two", Map.of("get", operation("duplicate")));

    assertThrows(IllegalArgumentException.class, () -> ApiV2OpenApiValidator.validate(document));
  }

  @Test
  void rejectsUnresolvedComponentReferences() {
    Map<String, Object> operation = operation("one");
    operation.put(
        "responses",
        Map.of(
            "200",
            Map.of(
                "description",
                "ok",
                "content",
                Map.of(
                    "application/json",
                    Map.of("schema", Map.of("$ref", "#/components/schemas/Missing"))))));
    objectMap(document.get("paths")).put("/one", Map.of("get", operation));

    assertThrows(IllegalArgumentException.class, () -> ApiV2OpenApiValidator.validate(document));
  }

  @Test
  void generatedDocumentIsAcceptedByTheStandardOpenApiParser() {
    ParseOptions options = new ParseOptions();
    options.setResolve(false);
    SwaggerParseResult parsed =
        new OpenAPIV3Parser().readContents(Json31.pretty(document), null, options);

    assertNotNull(parsed.getOpenAPI(), String.join("; ", parsed.getMessages()));
    assertTrue(parsed.getMessages().isEmpty(), String.join("; ", parsed.getMessages()));
  }

  private static Map<String, Object> operation(String id) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operationId", id);
    operation.put("responses", Map.of());
    return operation;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectMap(Object value) {
    return (Map<String, Object>) value;
  }
}

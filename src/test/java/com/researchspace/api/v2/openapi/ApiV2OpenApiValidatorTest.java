package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiValidatorTest {

  private OpenAPI document;

  @BeforeEach
  void setUp() {
    document =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0")
            .generate();
  }

  @Test
  void rejectsDuplicateOperationIds() {
    document.getPaths().addPathItem("/one", new PathItem().get(operation("duplicate")));
    document.getPaths().addPathItem("/two", new PathItem().get(operation("duplicate")));

    assertThrows(IllegalArgumentException.class, () -> ApiV2OpenApiValidator.validate(document));
  }

  @Test
  void rejectsUnresolvedComponentReferences() {
    Operation operation = operation("one");
    operation
        .getResponses()
        .addApiResponse(
            "200",
            new ApiResponse()
                .description("ok")
                .content(
                    new Content()
                        .addMediaType(
                            "application/json",
                            new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/Missing")))));
    document.getPaths().addPathItem("/one", new PathItem().get(operation));

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

  private static Operation operation(String id) {
    return new Operation().operationId(id).responses(new ApiResponses());
  }
}

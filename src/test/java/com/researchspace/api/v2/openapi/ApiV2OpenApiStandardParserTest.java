package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiStandardParserTest {

  @Test
  void generatedDocumentIsAcceptedByTheStandardOpenApiParser() {
    OpenAPI document =
        new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "Test API", "2.0.0")
            .generate();
    ParseOptions options = new ParseOptions();
    options.setResolve(false);
    SwaggerParseResult parsed =
        new OpenAPIV3Parser().readContents(Json31.pretty(document), null, options);

    assertNotNull(parsed.getOpenAPI(), String.join("; ", parsed.getMessages()));
    assertTrue(parsed.getMessages().isEmpty(), String.join("; ", parsed.getMessages()));
  }
}

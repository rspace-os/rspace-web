package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json31;
import java.lang.reflect.Type;
import java.util.Map;

/** Converts Java response types to OpenAPI 3.1 component schemas. */
final class ApiV2OpenApiSchemas {

  private ApiV2OpenApiSchemas() {}

  static Map<String, Object> schemaFor(Type type, Map<String, Object> components) {
    if (type == byte[].class) {
      return ordered("type", "string", "format", "binary");
    }
    ResolvedSchema resolved =
        ModelConverters.getInstance(true)
            .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
    resolved.referencedSchemas.forEach(
        (name, schema) -> components.put(name, Json31.jsonSchemaAsMap(schema)));
    return resolved.schema == null
        ? ordered("type", "object")
        : Json31.jsonSchemaAsMap(resolved.schema);
  }
}

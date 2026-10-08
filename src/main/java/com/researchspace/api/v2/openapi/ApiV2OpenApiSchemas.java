package com.researchspace.api.v2.openapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json31;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts Java response types to OpenAPI 3.1 component schemas. */
final class ApiV2OpenApiSchemas {

  private ApiV2OpenApiSchemas() {}

  static Map<String, Object> schemaFor(Type type, Map<String, Object> components) {
    if (type == byte[].class) {
      return map("type", "string", "format", "binary");
    }
    ResolvedSchema resolved =
        ModelConverters.getInstance(true)
            .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
    resolved.referencedSchemas.forEach(
        (name, schema) -> components.put(name, Json31.jsonSchemaAsMap(schema)));
    return resolved.schema == null
        ? map("type", "object")
        : Json31.jsonSchemaAsMap(resolved.schema);
  }

  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }
}

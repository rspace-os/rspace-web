package com.researchspace.api.v2.openapi;

import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.models.Components;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

final class ApiV2OpenApiAnnotationMerger {

  private ApiV2OpenApiAnnotationMerger() {}

  static void merge(
      Map<String, Object> document,
      Map<RequestMappingInfo, HandlerMethod> handlerMethods,
      ApiV2EndpointCatalog endpoints) {
    Map<String, Object> paths = objectMap(document.get("paths"));
    Map<String, Object> components = objectMap(document.get("components"));
    Map<String, Object> schemas = objectMap(components.get("schemas"));
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
      HandlerMethod handler = entry.getValue();
      Operation metadata =
          AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), Operation.class);
      if (metadata == null || metadata.hidden()) {
        continue;
      }
      for (String path : entry.getKey().getPatternValues()) {
        if (!path.startsWith("/api/v2")) {
          continue;
        }
        for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
          Map<String, Object> generated =
              operation(handler, metadata, schemas, entry.getKey(), endpoints);
          objectMap(paths.computeIfAbsent(path, ignored -> new LinkedHashMap<>()))
              .put(method.name().toLowerCase(java.util.Locale.ROOT), generated);
          ensureTags(document, generated);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void ensureTags(Map<String, Object> document, Map<String, Object> operation) {
    List<Map<String, Object>> documentedTags =
        (List<Map<String, Object>>) document.computeIfAbsent("tags", ignored -> new ArrayList<>());
    @SuppressWarnings("unchecked")
    List<String> operationTags = (List<String>) operation.getOrDefault("tags", List.of());
    for (String tag : operationTags) {
      boolean present = documentedTags.stream().anyMatch(value -> tag.equals(value.get("name")));
      if (!present) {
        documentedTags.add(
            map("name", tag, "description", "Operations documented by " + tag + "."));
      }
    }
  }

  private static Map<String, Object> operation(
      HandlerMethod handler,
      Operation metadata,
      Map<String, Object> schemas,
      RequestMappingInfo mapping,
      ApiV2EndpointCatalog endpoints) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operationId", metadata.operationId());
    operation.put("summary", metadata.summary());
    if (!metadata.description().isBlank()) {
      operation.put("description", metadata.description());
    }
    operation.put(
        "tags",
        metadata.tags().length == 0
            ? List.of(handler.getBeanType().getSimpleName())
            : List.of(metadata.tags()));
    if (metadata.deprecated()) {
      operation.put("deprecated", true);
    }
    AnnotationsUtils.getExternalDocumentation(metadata.externalDocs(), true)
        .ifPresent(value -> operation.put("externalDocs", modelMap(value)));
    AnnotationsUtils.getServers(metadata.servers())
        .ifPresent(
            value ->
                operation.put(
                    "servers", Json31.mapper().convertValue(value, java.util.ArrayList.class)));
    operation.putAll(AnnotationsUtils.getExtensions(metadata.extensions()));
    if (metadata.security().length > 0) {
      operation.put(
          "security",
          java.util.Arrays.stream(metadata.security())
              .map(requirement -> Map.of(requirement.name(), List.of(requirement.scopes())))
              .toList());
    } else {
      operation.put("security", security(endpoints, handler));
    }

    List<Map<String, Object>> parameters = parameters(handler, metadata, schemas);
    if (!parameters.isEmpty()) {
      operation.put("parameters", parameters);
    }
    requestBody(handler, metadata, mapping, schemas)
        .ifPresent(body -> operation.put("requestBody", body));

    Type responseType = unwrapResponseEntity(handler.getMethod().getGenericReturnType());
    Map<String, Object> responseSchema = ApiV2OpenApiSchemas.schemaFor(responseType, schemas);
    List<String> mediaTypes =
        mapping.getProducesCondition().getProducibleMediaTypes().isEmpty()
            ? List.of("application/json")
            : mapping.getProducesCondition().getProducibleMediaTypes().stream()
                .map(Object::toString)
                .toList();
    Map<String, Object> responses = new LinkedHashMap<>();
    for (ApiResponse documented : metadata.responses()) {
      String responseCode = documented.responseCode();
      if (!documented.ref().isBlank()) {
        responses.put(responseCode, Map.of("$ref", documented.ref()));
      } else if (isSuccessResponse(responseCode)) {
        Map<String, Object> success = map("description", documented.description());
        if (!responseCode.equals("204")
            && !responseCode.equals("205")
            && responseType != Void.class
            && responseType != void.class) {
          Map<String, Object> content =
              documented.content().length == 0
                  ? inferredContent(mediaTypes, responseSchema)
                  : annotatedContent(documented.content(), responseSchema, schemas);
          success.put("content", content);
        }
        if (documented.headers().length > 0) {
          success.put("headers", headers(documented.headers(), schemas));
        }
        responses.put(responseCode, success);
      } else if (responseCode.startsWith("1")
          || responseCode.startsWith("3")
          || documented.content().length > 0) {
        Map<String, Object> response = map("description", documented.description());
        if (documented.content().length > 0) {
          response.put("content", annotatedContent(documented.content(), responseSchema, schemas));
        }
        if (documented.headers().length > 0) {
          response.put("headers", headers(documented.headers(), schemas));
        }
        responses.put(responseCode, response);
      } else {
        Map<String, Object> error = errorResponse(responseCode);
        if (!documented.description().isBlank() && !error.containsKey("$ref")) {
          error.put("description", documented.description());
        }
        responses.put(responseCode, error);
      }
    }
    operation.put("responses", responses);
    operation.put("x-rspace-operation", "CUSTOM");
    return operation;
  }

  private static List<Map<String, List<String>>> security(
      ApiV2EndpointCatalog endpoints, HandlerMethod handler) {
    if (endpoints.isPublic(handler)) {
      return List.of();
    }
    if (endpoints.authenticationMode(handler) == ApiV2AuthenticationMode.BROWSER_SESSION) {
      return List.of(Map.of("browserSession", List.of()));
    }
    return List.of(Map.of("apiKey", List.of()), Map.of("bearerAuth", List.of()));
  }

  private static List<Map<String, Object>> parameters(
      HandlerMethod handler, Operation metadata, Map<String, Object> schemas) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (io.swagger.v3.oas.annotations.Parameter documented : metadata.parameters()) {
      if (!documented.hidden()) {
        result.add(documentedParameter(documented, Object.class, schemas));
      }
    }
    for (java.lang.reflect.Parameter parameter : handler.getMethod().getParameters()) {
      io.swagger.v3.oas.annotations.Parameter documented =
          AnnotatedElementUtils.findMergedAnnotation(
              parameter, io.swagger.v3.oas.annotations.Parameter.class);
      ParameterLocation location = parameterLocation(parameter, documented);
      if (location == null || (documented != null && documented.hidden())) {
        continue;
      }
      Map<String, Object> value =
          documented == null
              ? map(
                  "name",
                  location.name(),
                  "in",
                  location.in(),
                  "required",
                  location.required(),
                  "schema",
                  ApiV2OpenApiSchemas.schemaFor(parameter.getParameterizedType(), schemas))
              : documentedParameter(documented, parameter.getParameterizedType(), schemas);
      value.putIfAbsent("name", location.name());
      value.putIfAbsent("in", location.in());
      if (location.required()) {
        value.put("required", true);
      }
      replaceParameter(result, value);
    }
    return result;
  }

  private static Map<String, Object> documentedParameter(
      io.swagger.v3.oas.annotations.Parameter documented,
      Type fallbackType,
      Map<String, Object> schemas) {
    if (!documented.ref().isBlank()) {
      return Map.of("$ref", documented.ref());
    }
    Map<String, Object> result = new LinkedHashMap<>();
    putIfText(result, "name", documented.name());
    if (documented.in() != io.swagger.v3.oas.annotations.enums.ParameterIn.DEFAULT) {
      result.put("in", documented.in().toString().toLowerCase(java.util.Locale.ROOT));
    }
    putIfText(result, "description", documented.description());
    if (documented.required()) {
      result.put("required", true);
    }
    if (documented.deprecated()) {
      result.put("deprecated", true);
    }
    if (documented.allowEmptyValue()) {
      result.put("allowEmptyValue", true);
    }
    if (documented.allowReserved()) {
      result.put("allowReserved", true);
    }
    if (documented.style() != io.swagger.v3.oas.annotations.enums.ParameterStyle.DEFAULT) {
      result.put("style", documented.style().toString().toLowerCase(java.util.Locale.ROOT));
    }
    if (documented.explode() != io.swagger.v3.oas.annotations.enums.Explode.DEFAULT) {
      result.put(
          "explode", documented.explode() == io.swagger.v3.oas.annotations.enums.Explode.TRUE);
    }
    if (!documented.example().isBlank()) {
      result.put("example", parseExample(documented.example()));
    }
    if (documented.examples().length > 0) {
      Map<String, Object> examples = new LinkedHashMap<>();
      for (int index = 0; index < documented.examples().length; index++) {
        io.swagger.v3.oas.annotations.media.ExampleObject example = documented.examples()[index];
        String exampleName = example.name().isBlank() ? "example" + (index + 1) : example.name();
        AnnotationsUtils.getExample(example, true)
            .ifPresent(value -> examples.put(exampleName, modelMap(value)));
      }
      result.put("examples", examples);
    }
    if (documented.content().length > 0) {
      result.put(
          "content",
          annotatedContent(
              documented.content(), ApiV2OpenApiSchemas.schemaFor(fallbackType, schemas), schemas));
    } else {
      result.put(
          "schema",
          annotatedSchema(documented.schema(), documented.array(), fallbackType, schemas));
    }
    result.putAll(AnnotationsUtils.getExtensions(documented.extensions()));
    return result;
  }

  private static Map<String, Object> annotatedSchema(
      io.swagger.v3.oas.annotations.media.Schema schema,
      io.swagger.v3.oas.annotations.media.ArraySchema array,
      Type fallbackType,
      Map<String, Object> schemas) {
    Components components = new Components();
    java.util.Optional<? extends io.swagger.v3.oas.models.media.Schema> documented =
        AnnotationsUtils.getSchema(
            schema, array, false, rawClass(fallbackType), components, null, true);
    mergeSchemas(schemas, components);
    return documented
        .map(Json31::jsonSchemaAsMap)
        .orElseGet(() -> ApiV2OpenApiSchemas.schemaFor(fallbackType, schemas));
  }

  private static java.util.Optional<Map<String, Object>> requestBody(
      HandlerMethod handler,
      Operation metadata,
      RequestMappingInfo mapping,
      Map<String, Object> schemas) {
    io.swagger.v3.oas.annotations.parameters.RequestBody operationBody = metadata.requestBody();
    java.lang.reflect.Parameter javaBody = null;
    for (java.lang.reflect.Parameter parameter : handler.getMethod().getParameters()) {
      if (parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)
          || parameter.isAnnotationPresent(
              io.swagger.v3.oas.annotations.parameters.RequestBody.class)) {
        javaBody = parameter;
        break;
      }
    }
    boolean explicitlyDocumented =
        !operationBody.description().isBlank()
            || operationBody.required()
            || operationBody.content().length > 0
            || !operationBody.ref().isBlank();
    if (javaBody == null && !explicitlyDocumented) {
      return java.util.Optional.empty();
    }
    io.swagger.v3.oas.annotations.parameters.RequestBody parameterBody =
        javaBody == null
            ? null
            : javaBody.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class);
    io.swagger.v3.oas.annotations.parameters.RequestBody documented =
        explicitlyDocumented ? operationBody : parameterBody;
    Type type = javaBody == null ? Object.class : javaBody.getParameterizedType();
    Map<String, Object> schema = ApiV2OpenApiSchemas.schemaFor(type, schemas);
    List<String> mediaTypes =
        mapping.getConsumesCondition().getConsumableMediaTypes().isEmpty()
            ? List.of("application/json")
            : mapping.getConsumesCondition().getConsumableMediaTypes().stream()
                .map(Object::toString)
                .toList();
    Map<String, Object> result = new LinkedHashMap<>();
    if (documented != null) {
      putIfText(result, "description", documented.description());
      if (!documented.ref().isBlank()) {
        return java.util.Optional.of(Map.of("$ref", documented.ref()));
      }
    }
    result.put("required", documented != null ? documented.required() : true);
    result.put(
        "content",
        documented != null && documented.content().length > 0
            ? annotatedContent(documented.content(), schema, schemas)
            : inferredContent(mediaTypes, schema));
    return java.util.Optional.of(result);
  }

  private static ParameterLocation parameterLocation(
      java.lang.reflect.Parameter parameter, io.swagger.v3.oas.annotations.Parameter documented) {
    PathVariable path = parameter.getAnnotation(PathVariable.class);
    if (path != null) {
      return new ParameterLocation(
          annotationName(path.name(), path.value(), parameter), "path", true);
    }
    RequestParam query = parameter.getAnnotation(RequestParam.class);
    if (query != null) {
      return new ParameterLocation(
          annotationName(query.name(), query.value(), parameter), "query", query.required());
    }
    RequestHeader header = parameter.getAnnotation(RequestHeader.class);
    if (header != null) {
      return new ParameterLocation(
          annotationName(header.name(), header.value(), parameter), "header", header.required());
    }
    CookieValue cookie = parameter.getAnnotation(CookieValue.class);
    if (cookie != null) {
      return new ParameterLocation(
          annotationName(cookie.name(), cookie.value(), parameter), "cookie", cookie.required());
    }
    if (documented != null
        && documented.in() != io.swagger.v3.oas.annotations.enums.ParameterIn.DEFAULT) {
      return new ParameterLocation(
          documented.name(),
          documented.in().toString().toLowerCase(java.util.Locale.ROOT),
          documented.required());
    }
    return null;
  }

  private static String annotationName(
      String name, String value, java.lang.reflect.Parameter parameter) {
    return !name.isBlank() ? name : !value.isBlank() ? value : parameter.getName();
  }

  private static void replaceParameter(
      List<Map<String, Object>> parameters, Map<String, Object> replacement) {
    parameters.removeIf(
        existing ->
            java.util.Objects.equals(existing.get("name"), replacement.get("name"))
                && java.util.Objects.equals(existing.get("in"), replacement.get("in")));
    parameters.add(replacement);
  }

  private static Class<?> rawClass(Type type) {
    if (type instanceof Class<?> raw) {
      return raw;
    }
    if (type instanceof ParameterizedType parameterized
        && parameterized.getRawType() instanceof Class<?> raw) {
      return raw;
    }
    return Object.class;
  }

  private static void putIfText(Map<String, Object> target, String name, String value) {
    if (value != null && !value.isBlank()) {
      target.put(name, value);
    }
  }

  private static Object parseExample(String value) {
    try {
      return Json31.mapper().readValue(value, Object.class);
    } catch (java.io.IOException ex) {
      return value;
    }
  }

  private static Map<String, Object> headers(
      Header[] documentedHeaders, Map<String, Object> schemas) {
    Components components = new Components();
    Map<String, Object> result =
        modelMap(
            AnnotationsUtils.getHeaders(documentedHeaders, components, null, true)
                .orElseGet(Map::of));
    mergeSchemas(schemas, components);
    return result;
  }

  private static Map<String, Object> annotatedContent(
      io.swagger.v3.oas.annotations.media.Content[] annotations,
      Map<String, Object> fallbackSchema,
      Map<String, Object> schemas) {
    Components components = new Components();
    io.swagger.v3.oas.models.media.Schema<?> fallback =
        Json31.mapper().convertValue(fallbackSchema, io.swagger.v3.oas.models.media.Schema.class);
    Map<String, Object> result =
        modelMap(
            AnnotationsUtils.getContent(
                    annotations, new String[0], new String[0], fallback, components, null, true)
                .orElseGet(io.swagger.v3.oas.models.media.Content::new));
    mergeSchemas(schemas, components);
    return result;
  }

  private static Map<String, Object> inferredContent(
      List<String> mediaTypes, Map<String, Object> schema) {
    Map<String, Object> result = new LinkedHashMap<>();
    mediaTypes.forEach(mediaType -> result.put(mediaType, Map.of("schema", schema)));
    return result;
  }

  private static void mergeSchemas(Map<String, Object> schemas, Components components) {
    if (components.getSchemas() != null) {
      components
          .getSchemas()
          .forEach((name, schema) -> schemas.put(name, Json31.jsonSchemaAsMap(schema)));
    }
  }

  private static Type unwrapResponseEntity(Type type) {
    if (type instanceof ParameterizedType parameterized
        && parameterized.getRawType() == ResponseEntity.class) {
      return parameterized.getActualTypeArguments()[0];
    }
    return type;
  }

  private static boolean isSuccessResponse(String responseCode) {
    return responseCode.matches("2(?:\\d\\d|XX)");
  }

  private static Map<String, Object> errorResponse(String responseCode) {
    String component =
        switch (responseCode) {
          case "400" -> "BadRequest";
          case "401" -> "Unauthenticated";
          case "403" -> "Forbidden";
          case "404" -> "NotFound";
          case "406" -> "NotAcceptable";
          case "415" -> "UnsupportedMediaType";
          case "422" -> "BulkLimit";
          case "429" -> "TooManyRequests";
          case "500" -> "UnexpectedError";
          default -> null;
        };
    return component == null
        ? map(
            "description",
            "Request failed.",
            "content",
            Map.of("application/problem+json", Map.of("schema", ref("ApiV2Problem"))))
        : Map.of("$ref", "#/components/responses/" + component);
  }

  private static Map<String, Object> ref(String component) {
    return Map.of("$ref", "#/components/schemas/" + component);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> modelMap(Object value) {
    return Json31.mapper().convertValue(value, LinkedHashMap.class);
  }

  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }

  private record ParameterLocation(String name, String in, boolean required) {}
}

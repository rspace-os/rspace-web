package com.researchspace.api.v2.openapi;

import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.content;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.mutableObjectMap;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ordered;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.parameterRef;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.ref;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.requestBodyRef;
import static com.researchspace.api.v2.openapi.ApiV2OpenApiMaps.responseRef;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.api.v2.openapi.ApiV2OpenApiText.Key;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

final class ApiV2OpenApiAnnotationMerger {

  private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP =
      new TypeReference<>() {};

  private ApiV2OpenApiAnnotationMerger() {}

  static void merge(
      Map<String, Object> document,
      Map<RequestMappingInfo, HandlerMethod> handlerMethods,
      ApiV2EndpointCatalog endpoints,
      ApiV2OpenApiText text) {
    Map<String, Object> paths = mutableObjectMap(document.get("paths"));
    Map<String, Object> components = mutableObjectMap(document.get("components"));
    Map<String, Object> schemas = mutableObjectMap(components.get("schemas"));
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (entry.getKey().getPatternValues().stream()
          .noneMatch(ApiV2OpenApiAnnotationMerger::isApiV2Path)) {
        continue;
      }
      if (AnnotatedElementUtils.hasAnnotation(handler.getMethod(), Hidden.class)
          || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), Hidden.class)) {
        continue;
      }
      Operation metadata =
          AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), Operation.class);
      if (metadata == null) {
        throw new IllegalStateException(
            "API v2 handler needs @Operation or @Hidden: " + handler.getMethod().toGenericString());
      }
      if (metadata.hidden()) {
        continue;
      }
      for (String path : entry.getKey().getPatternValues()) {
        if (!isApiV2Path(path)) {
          continue;
        }
        for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
          Map<String, Object> generated =
              operation(handler, metadata, schemas, entry.getKey(), endpoints, text);
          mutableObjectMap(paths.computeIfAbsent(path, ignored -> new LinkedHashMap<>()))
              .put(method.name().toLowerCase(Locale.ROOT), generated);
        }
      }
    }
  }

  private static boolean isApiV2Path(String path) {
    return path.equals("/api/v2") || path.startsWith("/api/v2/");
  }

  private static Map<String, Object> operation(
      HandlerMethod handler,
      Operation metadata,
      Map<String, Object> schemas,
      RequestMappingInfo mapping,
      ApiV2EndpointCatalog endpoints,
      ApiV2OpenApiText text) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operationId", metadata.operationId());
    operation.put("summary", metadata.summary());
    if (!metadata.description().isBlank()) {
      operation.put("description", metadata.description());
    }
    if (metadata.deprecated()) {
      operation.put("deprecated", true);
    }
    AnnotationsUtils.getExternalDocumentation(metadata.externalDocs(), true)
        .ifPresent(value -> operation.put("externalDocs", modelMap(value)));
    AnnotationsUtils.getServers(metadata.servers())
        .ifPresent(
            value ->
                operation.put("servers", Json31.mapper().convertValue(value, ArrayList.class)));
    operation.putAll(AnnotationsUtils.getExtensions(metadata.extensions()));
    if (metadata.security().length > 0) {
      operation.put(
          "security",
          Arrays.stream(metadata.security())
              .map(requirement -> Map.of(requirement.name(), List.of(requirement.scopes())))
              .toList());
    } else {
      operation.put("security", security(endpoints, handler));
    }

    List<Map<String, Object>> parameters = parameters(handler, metadata, schemas, text);
    if (!parameters.isEmpty()) {
      operation.put("parameters", parameters);
    }
    requestBody(handler, metadata, mapping, schemas)
        .ifPresent(body -> operation.put("requestBody", body));

    Type responseType = unwrapResponseEntity(handler.getMethod().getGenericReturnType());
    boolean hasBody = responseType != Void.class && responseType != void.class;
    Map<String, Object> responseSchema =
        hasBody ? ApiV2OpenApiSchemas.responseSchemaFor(responseType, schemas) : Map.of();
    List<String> mediaTypes = mediaTypes(mapping.getProducesCondition().getProducibleMediaTypes());
    Map<String, Object> responses = new LinkedHashMap<>();
    for (ApiResponse documented : metadata.responses()) {
      String responseCode = documented.responseCode();
      boolean success = isSuccessResponse(responseCode);
      if (!documented.ref().isBlank()) {
        responses.put(responseCode, responseRef(documented.ref()));
      } else if (success
          || responseCode.startsWith("1")
          || responseCode.startsWith("3")
          || documented.content().length > 0) {
        Map<String, Object> response = ordered("description", documented.description());
        boolean contentAllowed =
            !success || (hasBody && !responseCode.equals("204") && !responseCode.equals("205"));
        if (contentAllowed && documented.content().length > 0) {
          response.put("content", annotatedContent(documented.content(), responseSchema, schemas));
        } else if (contentAllowed && success) {
          response.put("content", inferredContent(mediaTypes, responseSchema));
        }
        if (documented.headers().length > 0) {
          response.put("headers", headers(documented.headers(), schemas));
        }
        responses.put(responseCode, response);
      } else {
        Map<String, Object> error = errorResponse(responseCode, text);
        if (!documented.description().isBlank() && !error.containsKey("$ref")) {
          error.put("description", documented.description());
        }
        responses.put(responseCode, error);
      }
    }
    if (responses.keySet().stream().noneMatch(ApiV2OpenApiAnnotationMerger::isSuccessResponse)) {
      var success = ordered("description", text.get(Key.RESPONSE_SUCCESS));
      if (hasBody) success.put("content", inferredContent(mediaTypes, responseSchema));
      responses.put(hasBody ? "200" : "204", success);
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
    return ApiV2OpenApiGenerator.AUTHENTICATED_SECURITY;
  }

  private static List<Map<String, Object>> parameters(
      HandlerMethod handler,
      Operation metadata,
      Map<String, Object> schemas,
      ApiV2OpenApiText text) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Parameter documented : metadata.parameters()) {
      if (!documented.hidden()) {
        result.add(parameter(documented, Object.class, null, schemas));
      }
    }
    for (var parameter : handler.getMethod().getParameters()) {
      Parameter documented = AnnotatedElementUtils.findMergedAnnotation(parameter, Parameter.class);
      if (ApiV2FieldsetQuery.class.isAssignableFrom(parameter.getType())
          && parameter.isAnnotationPresent(ModelAttribute.class)
          && (documented == null || !documented.hidden())) {
        replaceParameter(result, ApiV2OpenApiQueryParameters.fieldset("fields", text));
        replaceParameter(result, ApiV2OpenApiQueryParameters.fieldset("exclude", text));
        continue;
      }
      ParameterLocation location = parameterLocation(parameter, parameter.getName(), documented);
      if (location != null && (documented == null || !documented.hidden())) {
        replaceParameter(
            result, parameter(documented, parameter.getParameterizedType(), location, schemas));
      }
    }
    return result;
  }

  /**
   * Builds a parameter with swagger-core, then completes its name, location and requiredness from
   * the Spring binding, which takes precedence over a weaker annotation.
   */
  private static Map<String, Object> parameter(
      Parameter documented, Type type, ParameterLocation location, Map<String, Object> schemas) {
    if (documented != null && !documented.ref().isBlank()) {
      return parameterRef(documented.ref());
    }
    Components components = new Components();
    var parameter =
        ParameterProcessor.applyAnnotations(
            new io.swagger.v3.oas.models.parameters.Parameter(),
            type,
            documented == null ? List.of() : List.of(documented),
            components,
            new String[0],
            new String[0],
            null,
            true);
    if (location != null) {
      if (parameter.getName() == null) parameter.setName(location.name());
      if (parameter.getIn() == null) parameter.setIn(location.in());
      parameter.setRequired(location.required() || Boolean.TRUE.equals(parameter.getRequired()));
    }
    Map<String, Object> result = modelMap(parameter);
    if (documented != null && documented.content().length > 0) {
      // Swagger resolves parameter content without components; resolve it again to keep its refs.
      result.put(
          "content",
          annotatedContent(
              documented.content(), ApiV2OpenApiSchemas.schemaFor(type, schemas), schemas));
    }
    mergeSchemas(schemas, components);
    return result;
  }

  private static Optional<Map<String, Object>> requestBody(
      HandlerMethod handler,
      Operation metadata,
      RequestMappingInfo mapping,
      Map<String, Object> schemas) {
    RequestBody operationBody = metadata.requestBody();
    var javaBody =
        Arrays.stream(handler.getMethod().getParameters())
            .filter(
                parameter ->
                    parameter.isAnnotationPresent(
                            org.springframework.web.bind.annotation.RequestBody.class)
                        || parameter.isAnnotationPresent(RequestBody.class))
            .findFirst()
            .orElse(null);
    boolean explicitlyDocumented =
        !operationBody.description().isBlank()
            || operationBody.required()
            || operationBody.content().length > 0
            || !operationBody.ref().isBlank();
    if (javaBody == null && !explicitlyDocumented) {
      return Optional.empty();
    }
    RequestBody parameterBody = javaBody == null ? null : javaBody.getAnnotation(RequestBody.class);
    RequestBody documented = explicitlyDocumented ? operationBody : parameterBody;
    Type type = javaBody == null ? Object.class : javaBody.getParameterizedType();
    Map<String, Object> schema = ApiV2OpenApiSchemas.schemaFor(type, schemas);
    List<String> mediaTypes = mediaTypes(mapping.getConsumesCondition().getConsumableMediaTypes());
    Map<String, Object> result = new LinkedHashMap<>();
    if (documented != null) {
      putIfText(result, "description", documented.description());
      if (!documented.ref().isBlank()) {
        return Optional.of(requestBodyRef(documented.ref()));
      }
    }
    result.put("required", documented != null ? documented.required() : true);
    result.put(
        "content",
        documented != null && documented.content().length > 0
            ? annotatedContent(documented.content(), schema, schemas)
            : inferredContent(mediaTypes, schema));
    return Optional.of(result);
  }

  private static ParameterLocation parameterLocation(
      AnnotatedElement parameter, String parameterName, Parameter documented) {
    PathVariable path = parameter.getAnnotation(PathVariable.class);
    if (path != null) {
      return new ParameterLocation(
          annotationName(path.name(), path.value(), parameterName), "path", true);
    }
    RequestParam query = parameter.getAnnotation(RequestParam.class);
    if (query != null) {
      return new ParameterLocation(
          annotationName(query.name(), query.value(), parameterName), "query", query.required());
    }
    RequestHeader header = parameter.getAnnotation(RequestHeader.class);
    if (header != null) {
      return new ParameterLocation(
          annotationName(header.name(), header.value(), parameterName),
          "header",
          header.required());
    }
    CookieValue cookie = parameter.getAnnotation(CookieValue.class);
    if (cookie != null) {
      return new ParameterLocation(
          annotationName(cookie.name(), cookie.value(), parameterName),
          "cookie",
          cookie.required());
    }
    if (documented != null && documented.in() != ParameterIn.DEFAULT) {
      return new ParameterLocation(
          documented.name(),
          documented.in().toString().toLowerCase(Locale.ROOT),
          documented.required());
    }
    return null;
  }

  private static String annotationName(String name, String value, String parameterName) {
    return !name.isBlank() ? name : !value.isBlank() ? value : parameterName;
  }

  private static void replaceParameter(
      List<Map<String, Object>> parameters, Map<String, Object> replacement) {
    parameters.removeIf(
        existing ->
            Objects.equals(existing.get("name"), replacement.get("name"))
                && Objects.equals(existing.get("in"), replacement.get("in")));
    parameters.add(replacement);
  }

  private static void putIfText(Map<String, Object> target, String name, String value) {
    if (value != null && !value.isBlank()) {
      target.put(name, value);
    }
  }

  private static Map<String, Object> headers(
      Header[] documentedHeaders, Map<String, Object> schemas) {
    Components components = new Components();
    var resolved =
        AnnotationsUtils.getHeaders(documentedHeaders, components, null, true).orElseGet(Map::of);
    Map<String, Object> result = modelMap(resolved);
    resolved.forEach(
        (name, header) -> {
          if (header.getSchema() != null) {
            var schema = header.getSchema();
            if (schema.getTypes() == null && schema.getType() != null)
              schema.setTypes(Set.of(schema.getType()));
            mutableObjectMap(result.get(name)).put("schema", Json31.jsonSchemaAsMap(schema));
          }
        });
    result.replaceAll(
        (name, value) -> {
          var header = mutableObjectMap(value);
          if (header.containsKey("$ref")) return Map.of("$ref", header.get("$ref"));
          if (!(header.get("schema") instanceof Map<?, ?> schema) || schema.isEmpty()) {
            throw new IllegalStateException(
                "OpenAPI response header needs a schema: " + name + " " + header);
          }
          return header;
        });
    mergeSchemas(schemas, components);
    return result;
  }

  private static Map<String, Object> annotatedContent(
      Content[] annotations, Map<String, Object> fallbackSchema, Map<String, Object> schemas) {
    Components components = new Components();
    Schema<?> fallback = Json31.mapper().convertValue(fallbackSchema, Schema.class);
    Map<String, Object> result =
        modelMap(
            AnnotationsUtils.getContent(
                    annotations, new String[0], new String[0], fallback, components, null, true)
                .orElseGet(io.swagger.v3.oas.models.media.Content::new));
    mergeSchemas(schemas, components);
    return result;
  }

  private static List<String> mediaTypes(Set<MediaType> declared) {
    return declared.isEmpty()
        ? List.of(APPLICATION_JSON_VALUE)
        : declared.stream().map(Object::toString).toList();
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

  private static Map<String, Object> errorResponse(String responseCode, ApiV2OpenApiText text) {
    Map<String, Object> standardResponse =
        ApiV2OpenApiResponses.standardResponseReference(responseCode);
    return standardResponse == null
        ? ordered(
            "description",
            text.get(Key.RESPONSE_FAILURE),
            "content",
            content(ApiV2OpenApiResponses.PROBLEM_JSON, ref("ApiV2Problem")))
        : standardResponse;
  }

  private static Map<String, Object> modelMap(Object value) {
    return Json31.mapper().convertValue(value, OBJECT_MAP);
  }

  private record ParameterLocation(String name, String in, boolean required) {}
}

package com.researchspace.api.v2.openapi;

import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public final class ApiV2OpenApiDocumentService {

  private final ApiV2OpenApiGenerator generator;
  private final ApiV2EndpointCatalog endpoints;
  private final Supplier<Stream<RequestMappingHandlerMapping>> handlerMappings;

  public ApiV2OpenApiDocumentService(
      ApiV2OpenApiGenerator generator,
      ApiV2EndpointCatalog endpoints,
      Supplier<Stream<RequestMappingHandlerMapping>> handlerMappings) {
    this.generator = Objects.requireNonNull(generator, "OpenAPI generator");
    this.endpoints = Objects.requireNonNull(endpoints, "Endpoint catalog");
    this.handlerMappings = Objects.requireNonNull(handlerMappings, "Handler mappings");
  }

  public Map<String, Object> generate() {
    Map<String, Object> document = generator.generate();
    try (Stream<RequestMappingHandlerMapping> mappings = handlerMappings.get()) {
      mappings.forEach(
          mapping ->
              ApiV2OpenApiAnnotationMerger.merge(document, mapping.getHandlerMethods(), endpoints));
    }
    ApiV2OpenApiValidator.validate(document);
    return document;
  }
}

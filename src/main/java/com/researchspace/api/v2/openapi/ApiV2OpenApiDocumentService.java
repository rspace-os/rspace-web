package com.researchspace.api.v2.openapi;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public final class ApiV2OpenApiDocumentService {

  private final ApiV2OpenApiGenerator generator;
  private final Supplier<Stream<RequestMappingHandlerMapping>> handlerMappings;

  public ApiV2OpenApiDocumentService(
      ApiV2OpenApiGenerator generator,
      Supplier<Stream<RequestMappingHandlerMapping>> handlerMappings) {
    this.generator = Objects.requireNonNull(generator, "OpenAPI generator");
    this.handlerMappings = Objects.requireNonNull(handlerMappings, "Handler mappings");
  }

  public Map<String, Object> generate() {
    Map<String, Object> document = generator.generate();
    try (Stream<RequestMappingHandlerMapping> mappings = handlerMappings.get()) {
      mappings.forEach(
          mapping -> ApiV2OpenApiAnnotationMerger.merge(document, mapping.getHandlerMethods()));
    }
    ApiV2OpenApiValidator.validate(document);
    return document;
  }
}

package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ApiV2OperationCoverageTest {

  @Test
  void everyV2HandlerIsDocumentedOrExplicitlyHidden() {
    try (StaticWebApplicationContext context = new StaticWebApplicationContext()) {
      RequestMappingHandlerMapping mapping = ApiV2TestHandlerMappings.mapping(context);
      Map<RequestMappingInfo, HandlerMethod> handlers = mapping.getHandlerMethods();
      var v2Handlers =
          handlers.entrySet().stream()
              .filter(
                  entry ->
                      entry.getKey().getPatternValues().stream()
                          .anyMatch(path -> path.equals("/api/v2") || path.startsWith("/api/v2/")))
              .map(Map.Entry::getValue)
              .distinct()
              .toList();
      assertFalse(v2Handlers.isEmpty(), "No API v2 handlers discovered");
      var missing =
          v2Handlers.stream()
              .filter(
                  handler ->
                      !AnnotatedElementUtils.hasAnnotation(handler.getMethod(), Operation.class))
              .filter(
                  handler ->
                      !AnnotatedElementUtils.hasAnnotation(handler.getMethod(), Hidden.class))
              .filter(
                  handler ->
                      !AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), Hidden.class))
              .map(handler -> handler.getMethod().toGenericString())
              .sorted()
              .toList();
      assertTrue(missing.isEmpty(), "API v2 handlers need @Operation or @Hidden: " + missing);
    }
  }
}

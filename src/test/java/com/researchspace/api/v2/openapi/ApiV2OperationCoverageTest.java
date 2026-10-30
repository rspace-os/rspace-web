package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ApiV2OperationCoverageTest {

  @Test
  void everyV2HandlerIsDocumentedOrExplicitlyHidden() {
    try (StaticWebApplicationContext context = new StaticWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      ClassPathScanningCandidateComponentProvider scanner =
          new ClassPathScanningCandidateComponentProvider(false);
      String productionClasses =
          ApiV2OpenApiController.class
              .getProtectionDomain()
              .getCodeSource()
              .getLocation()
              .toExternalForm();
      scanner.addExcludeFilter(
          (reader, factory) ->
              !reader.getResource().getURL().toExternalForm().startsWith(productionClasses));
      scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
      for (String basePackage : new String[] {"com.researchspace", "com.axiope.webapp"}) {
        scanner
            .findCandidateComponents(basePackage)
            .forEach(
                definition -> {
                  definition.setLazyInit(true);
                  context.registerBeanDefinition(definition.getBeanClassName(), definition);
                });
      }
      RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
      mapping.setApplicationContext(context);
      mapping.afterPropertiesSet();
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

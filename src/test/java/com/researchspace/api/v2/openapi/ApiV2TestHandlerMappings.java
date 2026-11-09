package com.researchspace.api.v2.openapi;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Discovers production MVC handlers without instantiating controller dependencies. */
public final class ApiV2TestHandlerMappings {
  private ApiV2TestHandlerMappings() {}

  public static StaticWebApplicationContext newContext() {
    return new StaticWebApplicationContext();
  }

  public static RequestMappingHandlerMapping mapping(StaticWebApplicationContext context) {
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
    return mapping;
  }
}

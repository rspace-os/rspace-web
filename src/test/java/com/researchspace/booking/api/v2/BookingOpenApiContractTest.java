package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class BookingOpenApiContractTest {
  @Test
  void rendersFeatureControllerRoutesInTheServedDocument() throws Exception {
    try (var context = new StaticWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      for (Class<?> type :
          List.of(
              BookingCatalogueController.class,
              BookingConfigurationTargetController.class,
              BookingSettingsController.class)) {
        var bean = new org.springframework.beans.factory.support.RootBeanDefinition(type);
        bean.setLazyInit(true);
        context.registerBeanDefinition(type.getSimpleName(), bean);
      }
      var mapping = new RequestMappingHandlerMapping();
      mapping.setApplicationContext(context);
      mapping.afterPropertiesSet();
      var document =
          new ApiV2OpenApiDocumentService(
                  new ApiV2OpenApiGenerator(new ApiV2ResourceCatalog(List.of()), "API", "2.0.0"),
                  new ApiV2EndpointCatalog(List.of()),
                  () -> Stream.of(mapping))
              .generate();
      var mapper = new ObjectMapper();
      var paths = mapper.valueToTree(document).path("paths");
      assertEquals(6, paths.size());
      assertTrue(paths.has("/api/v2/booking-settings/admin"));
      assertTrue(paths.has("/api/v2/booking-catalogue"));
      assertTrue(paths.has("/api/v2/booking-configuration-targets"));
      var responses = paths.path("/api/v2/booking-settings/admin").path("patch").path("responses");
      assertEquals(
          java.util.Set.of("200", "400", "403", "409"),
          mapper.convertValue(responses, java.util.Map.class).keySet());
      String snapshot = System.getProperty("featureOpenApiSnapshot");
      if (snapshot != null) {
        Files.writeString(
            Path.of(snapshot),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document));
      }
    }
  }
}

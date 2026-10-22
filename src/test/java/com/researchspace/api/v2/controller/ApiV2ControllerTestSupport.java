package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.json.ApiV2JsonMappers;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.maintenance.api.v2.MaintenanceResourceOperations;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.ArrayList;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;

final class ApiV2ControllerTestSupport {

  private ApiV2ControllerTestSupport() {}

  static ApiV2ResourceSpec<ScheduledMaintenance, Long> maintenanceSpec(
      MaintenanceManager maintenanceManager) {
    return new ApiV2ResourceSpec<>(
        ApiV2MaintenanceResource.DESCRIPTION,
        new MaintenanceResourceOperations(maintenanceManager),
        Long::valueOf,
        "errors.api.v2.invalidRequest",
        "errors.api.v2.maintenance.patch");
  }

  static ApiV2ControllerAdvice problemAdvice() {
    return new ApiV2ControllerAdvice(new MessageSourceUtils(new JsonMessageSource()));
  }

  static void registerPreHandlerProblems(MockMvc mvc) {
    // Standalone MVC omits WebConfig's resolver for errors raised before handler selection.
    var composite =
        mvc.getDispatcherServlet()
            .getWebApplicationContext()
            .getBean(HandlerExceptionResolverComposite.class);
    var resolvers = new ArrayList<>(composite.getExceptionResolvers());
    resolvers.add(
        0, new ApiV2PreHandlerProblemResolver(problemAdvice(), bodyConverter().getObjectMapper()));
    composite.setExceptionResolvers(resolvers);
  }

  static MappingJackson2HttpMessageConverter bodyConverter() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter
        .getObjectMapper()
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    ApiV2JsonMappers.register(converter);
    return converter;
  }

  static LocalValidatorFactoryBean jsonValidator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    return validator;
  }
}

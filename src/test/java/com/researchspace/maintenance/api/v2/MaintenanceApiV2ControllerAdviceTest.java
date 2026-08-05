package com.researchspace.maintenance.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.ControllerAdviceBean;

class MaintenanceApiV2ControllerAdviceTest {

  @Test
  void mapsMaintenanceErrorsForTheCollectionController() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.v2.maintenance.window", Locale.getDefault(), "Invalid window detail");
    MaintenanceApiV2ControllerAdvice advice =
        new MaintenanceApiV2ControllerAdvice(new MessageSourceUtils(source));

    ResponseEntity<ApiV2Problem> response = advice.handleMaintenanceOperation();

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("errors.api.v2.maintenance.window", response.getBody().code());
    assertEquals("Invalid window detail", response.getBody().detail());
    assertAppliesBeforeSharedAdvice(advice);
  }

  private static void assertAppliesBeforeSharedAdvice(MaintenanceApiV2ControllerAdvice advice) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("maintenanceApiV2ControllerAdvice", advice);
    ControllerAdviceBean adviceBean =
        new ControllerAdviceBean(
            "maintenanceApiV2ControllerAdvice",
            beanFactory,
            MaintenanceApiV2ControllerAdvice.class.getAnnotation(ControllerAdvice.class));

    assertTrue(adviceBean.isApplicableToBeanType(ApiV2CrudController.class));
    assertEquals(
        Ordered.HIGHEST_PRECEDENCE,
        MaintenanceApiV2ControllerAdvice.class.getAnnotation(Order.class).value());
  }
}

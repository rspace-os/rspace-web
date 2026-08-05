package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.ControllerAdviceBean;

class BookingConfigurationApiV2ControllerAdviceTest {

  private BookingConfigurationApiV2ControllerAdvice advice;

  @BeforeEach
  void setUp() {
    StaticMessageSource source = new StaticMessageSource();
    source.addMessage(
        "errors.api.v2.bookingConfiguration.target.invalid",
        Locale.getDefault(),
        "Invalid target detail");
    source.addMessage(
        "errors.api.v2.bookingConfiguration.target.conflict",
        Locale.getDefault(),
        "Target conflict detail");
    advice = new BookingConfigurationApiV2ControllerAdvice(new MessageSourceUtils(source));
  }

  @Test
  void mapsBookingTargetErrorsToTheirPublicStatuses() {
    assertProblem(
        advice.handleInvalidBookableTarget(),
        HttpStatus.BAD_REQUEST,
        "errors.api.v2.bookingConfiguration.target.invalid",
        "Invalid target detail");
    assertProblem(
        advice.handleBookingConfigurationTargetConflict(),
        HttpStatus.CONFLICT,
        "errors.api.v2.bookingConfiguration.target.conflict",
        "Target conflict detail");
  }

  @Test
  void appliesToTheCollectionControllerBeforeSharedAdvice() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("bookingConfigurationApiV2ControllerAdvice", advice);
    ControllerAdviceBean adviceBean =
        new ControllerAdviceBean(
            "bookingConfigurationApiV2ControllerAdvice",
            beanFactory,
            BookingConfigurationApiV2ControllerAdvice.class.getAnnotation(ControllerAdvice.class));

    assertTrue(adviceBean.isApplicableToBeanType(ApiV2CrudController.class));
    assertEquals(
        Ordered.HIGHEST_PRECEDENCE,
        BookingConfigurationApiV2ControllerAdvice.class.getAnnotation(Order.class).value());
  }

  private static void assertProblem(
      ResponseEntity<ApiV2Problem> response, HttpStatus status, String code, String detail) {
    assertEquals(status, response.getStatusCode());
    assertEquals(ApiV2Problem.PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(code, response.getBody().code());
    assertEquals(detail, response.getBody().detail());
  }
}

package com.researchspace.booking.api.v2;

import static com.researchspace.api.v2.controller.ApiV2VersionPreconditionAdvice.VERSION_PRECONDITION_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.booking.api.v2.BookingNotificationSubscriptionController.SubscriptionReplacement;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.ControllerAdviceBean;

class BookingApiV2VersionPreconditionAdviceTest {
  private final BookingApiV2VersionPreconditionAdvice advice =
      new BookingApiV2VersionPreconditionAdvice();

  @Test
  void appliesToBookingControllers() {
    var annotation =
        AnnotatedElementUtils.findMergedAnnotation(advice.getClass(), ControllerAdvice.class);
    var bean =
        new ControllerAdviceBean(
            "advice", new StaticListableBeanFactory(Map.of("advice", advice)), annotation);
    assertTrue(bean.isApplicableToBeanType(BookingNotificationSubscriptionController.class));
  }

  @Test
  void recordsABodyVersionOnARecordBody() {
    var request = new MockHttpServletRequest("PUT", "/api/v2/booking-notification-subscriptions/1");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      advice.afterBodyRead(new SubscriptionReplacement(true, 3L), null, null, null, null);
      assertEquals(true, request.getAttribute(VERSION_PRECONDITION_ATTRIBUTE));
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }
}

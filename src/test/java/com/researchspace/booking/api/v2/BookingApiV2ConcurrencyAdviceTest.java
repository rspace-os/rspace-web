package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.controller.ApiV2ProblemFactory;
import com.researchspace.service.MessageSourceUtils;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.ControllerAdviceBean;

class BookingApiV2ConcurrencyAdviceTest {
  private final MessageSourceUtils messages = mock(MessageSourceUtils.class);
  private final BookingApiV2ControllerAdvice advice =
      new BookingApiV2ControllerAdvice(messages, new ApiV2ProblemFactory(messages));

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
  void commitRaceOnASubscriptionReplacementFailsTheIfMatchPrecondition() {
    when(messages.getMessage("errors.concurrentChange")).thenReturn("Concurrent change");
    for (boolean conditional : List.of(false, true)) {
      var request =
          new MockHttpServletRequest(
              "PUT", "/api/v2/booking-configurations/1/notification-subscription");
      if (conditional) request.addHeader(HttpHeaders.IF_MATCH, "\"v3-110\"");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      try {
        assertEquals(
            conditional ? HttpStatus.PRECONDITION_FAILED : HttpStatus.CONFLICT,
            advice
                .handleUnexpected(
                    new TransactionSystemException(
                        "commit", new SQLException("race", "40001", 1213)))
                .getStatusCode());
      } finally {
        RequestContextHolder.resetRequestAttributes();
      }
    }
  }
}

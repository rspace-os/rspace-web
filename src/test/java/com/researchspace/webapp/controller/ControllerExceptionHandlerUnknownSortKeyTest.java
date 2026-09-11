package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.sort.UnknownSortKeyException;
import com.researchspace.model.sort.UserSort;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

public class ControllerExceptionHandlerUnknownSortKeyTest {

  private ControllerExceptionHandler handler;
  private UnknownSortKeyException exception;

  @BeforeEach
  public void setUp() {
    handler = new ControllerExceptionHandler();
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(eq("errors.invalidOrderByClause"), any(Object[].class)))
        .thenReturn("Invalid order by clause");
    ReflectionTestUtils.setField(handler, "messages", messages);
    exception = new UnknownSortKeyException("name,rand()", List.of(UserSort.LAST_NAME.key()));
  }

  @Test
  public void ajaxRequestGets400WithTheExternalisedMessage() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestUtil.AJAX_REQUEST_HEADER_NAME, RequestUtil.AJAX_REQUEST_TYPE);
    MockHttpServletResponse response = new MockHttpServletResponse();

    ModelAndView mav = handler.handleExceptions(request, response, exception);

    assertEquals(400, response.getStatus());
    assertEquals(ControllerExceptionHandler.AJAX_ERROR_VIEW_NAME, mav.getViewName());
    String message =
        (String) mav.getModel().get(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME);
    assertEquals(
        ControllerExceptionHandler.AJAX_DEFAULT_ERROR_MSG + "\nInvalid order by clause", message);
  }

  /**
   * A page request gets the error view with the same message, but not the 400. error.jsp is an
   * isErrorPage, so Jasper sets 500 for every page rendering it and the handler cannot override
   * that. Only the ajax path, which renders ajaxError.jsp, carries the status to the client.
   */
  @Test
  public void pageRequestGetsTheErrorViewWithoutTheStatus() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    ModelAndView mav = handler.handleExceptions(new MockHttpServletRequest(), response, exception);

    assertEquals(ControllerExceptionHandler.NON_AJAX_ERROR_VIEW_NAME, mav.getViewName());
    assertEquals(
        "Invalid order by clause",
        mav.getModel().get(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME));
  }
}

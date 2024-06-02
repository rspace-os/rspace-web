package com.researchspace.webapp.controller;

import static org.junit.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

public class GlobalExceptionHandlerTest {

  private static final String EXC_MSG =
      "The file was" + " rejected because its size (22345643) exceeded the limit (5000)";
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock private IControllerExceptionHandler exceptionHandler;
  MaxUploadSizeExceededException maxSizeException;

  @Before
  public void setup() {
    maxSizeException = new MaxUploadSizeExceededException(5000, new Exception(EXC_MSG));
  }

  @Test
  public void testHandleMaxUploadException() {
    GlobalExceptionHandler globalHandler = new GlobalExceptionHandler();
    globalHandler.setHandler(exceptionHandler);
    HttpServletRequest req = new MockHttpServletRequest();
    HttpServletResponse resp = new MockHttpServletResponse();

    ModelAndView mav = globalHandler.handleMaxUploadException(maxSizeException, req, resp);
  }

  @Test
  public void testDisplayMaxSizeExceededExceptionMsg() {
    GlobalExceptionHandler globalHandler = new GlobalExceptionHandler();
    String msg = globalHandler.convertBytesToDisplay(EXC_MSG);
    assertTrue("Message was not formatted", msg.contains("(21 MB)"));
  }
}

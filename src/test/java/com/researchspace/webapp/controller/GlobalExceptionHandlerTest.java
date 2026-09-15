package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionHandlerTest {

  private static final String EXC_MSG =
      "The file was" + " rejected because its size (22345643) exceeded the limit (5000)";
  @Mock private IControllerExceptionHandler exceptionHandler;
  MaxUploadSizeExceededException maxSizeException;

  @BeforeEach
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
    assertTrue(msg.contains("(21 MB)"), "Message was not formatted");
  }
}

package com.researchspace.webapp.integrations.jove;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

@Slf4j
@Component
public class JoveExceptionHandler {

  public ResponseEntity<String> handle(
      HttpServletRequest request, HttpServletResponse response, HttpStatusCodeException exception) {
    HttpStatus responseStatus = exception.getStatusCode();
    String userErrorMessage = null;

    if (responseStatus == HttpStatus.INTERNAL_SERVER_ERROR) {
      log.error(makeMessage(exception), exception);
      userErrorMessage = makeMessage(exception);
    } else if (responseStatus == HttpStatus.NOT_FOUND) {
      log.info(makeMessage(exception));
      userErrorMessage = makeMessage(exception);
    } else {
      log.warn(makeMessage(exception));
      log.debug(makeMessage(exception), exception);
      userErrorMessage = makeMessage(exception);
    }
    return new ResponseEntity<>(userErrorMessage, responseStatus);
  }

  private String makeMessage(Exception exception) {
    String message = exception.getMessage();
    Throwable cause = exception.getCause();
    if (cause != null) {
      message = "Exception Msg: " + message + " Cause: " + cause.getMessage();
    }
    return message;
  }
}

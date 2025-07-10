package com.researchspace.webapp.integrations.ascenscia.exception;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;

/** Exception handler for Ascenscia-related exceptions. */
@Slf4j
public class AscensciaExceptionHandler {

  /**
   * Handles Ascenscia-related exceptions and maps them to appropriate HTTP status codes.
   *
   * @param exception The exception to handle
   * @return A ResponseEntity with an appropriate HTTP status code and error message
   */
  public ResponseEntity<String> handle(Exception exception) {
    HttpStatus responseStatus = null;

    // First check if it's our custom exception with a status
    if (exception instanceof AscensciaException) {
      responseStatus = ((AscensciaException) exception).getStatus();
    }

    // If no status from custom exception, check annotation
    if (responseStatus == null) {
      responseStatus = resolveAnnotatedResponseStatus(exception);
    }

    // If no annotation, check if it's a client error
    if (responseStatus == null) {
      responseStatus = determineClientErrorException(exception);
    }

    String userErrorMessage = null;
    if (responseStatus == null) {
      responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    if (responseStatus == HttpStatus.INTERNAL_SERVER_ERROR) {
      log.error(makeMessage(exception), exception);
      userErrorMessage = "There is a problem with your request, please contact support.";
    } else {
      log.warn(makeMessage(exception));
      log.debug(makeMessage(exception), exception);
      userErrorMessage = exception.getMessage();
    }
    return new ResponseEntity<>(userErrorMessage, responseStatus);
  }

  private HttpStatus determineClientErrorException(Exception exception) {
    if (exception instanceof HttpClientErrorException) {
      return ((HttpClientErrorException) exception).getStatusCode();
    } else {
      return null;
    }
  }

  private String makeMessage(Exception exception) {
    String message = exception.getMessage();
    Throwable cause = exception.getCause();
    if (cause != null) {
      message += " " + cause.getMessage();
    }
    return message;
  }

  private HttpStatus resolveAnnotatedResponseStatus(Exception exception) {
    ResponseStatus annotation = findMergedAnnotation(exception.getClass(), ResponseStatus.class);
    if (annotation != null) {
      return annotation.value();
    } else {
      return null;
    }
  }
}

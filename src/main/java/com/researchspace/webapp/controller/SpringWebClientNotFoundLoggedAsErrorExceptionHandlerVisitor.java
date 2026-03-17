package com.researchspace.webapp.controller;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Will log 404 exceptions at Error level instead of Warn, also makes additional exception info
 * available if log level set to debug
 */
@Slf4j
public class SpringWebClientNotFoundLoggedAsErrorExceptionHandlerVisitor
    extends ControllerExceptionHandler.DefaultExceptionHandlerVisitor {

  public void logWebClientExceptions(Exception exception, String errorId) {
    HttpStatus responseStatus = resolveAnnotatedResponseStatus(exception);
    if (responseStatus == null) {
      responseStatus = determineClientErrorException(exception);
    }
    if (responseStatus == null) {
      responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    if (responseStatus == HttpStatus.INTERNAL_SERVER_ERROR
        || responseStatus == HttpStatus.NOT_FOUND) {
      log.debug(makeMessage(exception), exception);
      logErrorFromException(exception, errorId);
    } else {
      logWarningFromException(exception, errorId);
      log.debug(makeMessage(exception), exception);
    }
  }

  private HttpStatus determineClientErrorException(Exception exception) {
    if (exception instanceof HttpClientErrorException) {
      return toHttpStatus(((HttpClientErrorException) exception).getStatusCode());
    } else {
      return null;
    }
  }

  private HttpStatus toHttpStatus(HttpStatusCode statusCode) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
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

  @Override
  public boolean visitorHasHandledSpringWebClientExceptionLogging(
      HttpServletRequest request,
      HttpServletResponse response,
      Exception e,
      String timestamp,
      String errorId) {
    logWebClientExceptions(e, errorId);
    return true;
  }
}

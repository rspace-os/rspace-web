package com.researchspace.webapp.integrations.omero;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import com.researchspace.service.MessageSourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
public class OmeroExceptionHandler {

  private final MessageSourceUtils messages;

  public OmeroExceptionHandler(MessageSourceUtils messages) {
    this.messages = messages;
  }

  public ResponseEntity<String> handle(Exception exception) {
    HttpStatus responseStatus = resolveAnnotatedResponseStatus(exception);
    if (responseStatus == null) {
      responseStatus = determineClientErrorException(exception);
    }
    String userErrorMessage = null;
    if (responseStatus == null) {
      responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    if (responseStatus == HttpStatus.INTERNAL_SERVER_ERROR
        || responseStatus == HttpStatus.NOT_FOUND) {
      log.error(makeMessage(exception), exception);
      userErrorMessage = messages.getMessage("apps.errors.requestSupport");
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

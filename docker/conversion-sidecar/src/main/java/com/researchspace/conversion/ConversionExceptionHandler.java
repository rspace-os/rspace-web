package com.researchspace.conversion;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
final class ConversionExceptionHandler {

  @ExceptionHandler(ConversionException.class)
  ResponseEntity<ProblemDetail> handle(ConversionException exception) {
    return response(exception.status(), exception.error());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> handleOversizedUpload(MaxUploadSizeExceededException exception) {
    return response(HttpStatus.PAYLOAD_TOO_LARGE, ConversionError.INPUT_TOO_LARGE);
  }

  @ExceptionHandler({
    MultipartException.class,
    BindException.class,
    HandlerMethodValidationException.class
  })
  ResponseEntity<ProblemDetail> handleInvalidRequest() {
    return response(HttpStatus.BAD_REQUEST, ConversionError.INPUT_INVALID);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException exception) {
    return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ConversionError.UNSUPPORTED);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> handleUnsupportedMethod() {
    return response(HttpStatus.METHOD_NOT_ALLOWED, ConversionError.INPUT_INVALID);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, ConversionError.FAILED);
  }

  private ResponseEntity<ProblemDetail> response(HttpStatus status, ConversionError error) {
    String code = error.code();
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, code);
    problem.setType(URI.create("urn:rspace:conversion:error:" + code));
    problem.setTitle(code);
    problem.setProperty("code", code);
    problem.setProperty("requestId", UUID.randomUUID().toString());
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(status).header(ConversionError.HEADER, code);
    if (status.value() == 429) {
      response.header(HttpHeaders.RETRY_AFTER, "1");
    }
    return response.body(problem);
  }
}

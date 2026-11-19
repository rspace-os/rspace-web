package com.researchspace.conversion;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger LOG = LoggerFactory.getLogger(ConversionExceptionHandler.class);

  @ExceptionHandler(ConversionException.class)
  ResponseEntity<ProblemDetail> handle(
      ConversionException exception, HttpServletResponse servletResponse)
      throws ConversionException {
    rethrowIfCommitted(exception, servletResponse);
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
  ResponseEntity<ProblemDetail> handleUnexpected(
      Exception exception, HttpServletResponse servletResponse) throws Exception {
    if (servletResponse.isCommitted()) {
      LOG.warn("Conversion response aborted after it was committed", exception);
    }
    rethrowIfCommitted(exception, servletResponse);
    String requestId = UUID.randomUUID().toString();
    LOG.error("Unexpected conversion failure [{}]", requestId, exception);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, ConversionError.FAILED, requestId);
  }

  /**
   * A failure while streaming a converted file must not append an error body to it: rethrowing
   * leaves the exception unresolved, so the container closes the connection and the client sees a
   * body shorter than its {@code Content-Length}. Before commit, drop the converted file's length
   * so the error body is not framed by it.
   */
  private static <E extends Exception> void rethrowIfCommitted(
      E exception, HttpServletResponse servletResponse) throws E {
    if (servletResponse.isCommitted()) {
      throw exception;
    }
    servletResponse.setContentLengthLong(-1);
  }

  private ResponseEntity<ProblemDetail> response(HttpStatus status, ConversionError error) {
    return response(status, error, UUID.randomUUID().toString());
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status, ConversionError error, String requestId) {
    String code = error.code();
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, code);
    problem.setType(URI.create("urn:rspace:conversion:error:" + code));
    problem.setTitle(code);
    problem.setProperty("code", code);
    problem.setProperty("requestId", requestId);
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(status).header(ConversionError.HEADER, code);
    if (status.value() == 429) {
      response.header(HttpHeaders.RETRY_AFTER, "1");
    }
    return response.body(problem);
  }
}

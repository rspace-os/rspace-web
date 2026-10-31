package com.researchspace.api.v2.resource;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

/** Runtime and OpenAPI definition of one collection-operation failure. */
public record ApiV2ErrorMapping(
    Class<? extends RuntimeException> exceptionType,
    HttpStatus status,
    String errorCode,
    String description,
    Function<RuntimeException, Object[]> argumentExtractor,
    Function<RuntimeException, String> errorCodeExtractor,
    Function<RuntimeException, Map<String, Object>> extensionExtractor) {

  public ApiV2ErrorMapping {
    Objects.requireNonNull(exceptionType, "Exception type");
    Objects.requireNonNull(status, "Error status");
    Assert.hasText(errorCode, "Error code must not be blank");
    Assert.hasText(description, "Error description must not be blank");
    Objects.requireNonNull(errorCodeExtractor, "Error code extractor");
    Objects.requireNonNull(argumentExtractor, "Error argument extractor");
    Objects.requireNonNull(extensionExtractor, "Error extension extractor");
    if (!status.isError()) {
      throw new IllegalArgumentException("Resource error mapping requires an error status");
    }
  }

  public static <E extends RuntimeException> ApiV2ErrorMapping of(
      Class<E> exceptionType, HttpStatus status, String errorCode, String description) {
    return of(exceptionType, status, errorCode, description, ignored -> new Object[0]);
  }

  public static <E extends RuntimeException> ApiV2ErrorMapping of(
      Class<E> exceptionType,
      HttpStatus status,
      String errorCode,
      String description,
      Function<E, Object[]> argumentExtractor) {
    return of(
        exceptionType, status, errorCode, description, argumentExtractor, ignored -> Map.of());
  }

  /**
   * Maps a failure whose problem body carries RFC 9457 extension members, provided by a resource
   * operation.
   */
  public static <E extends RuntimeException> ApiV2ErrorMapping of(
      Class<E> exceptionType,
      HttpStatus status,
      String errorCode,
      String description,
      Function<E, Object[]> argumentExtractor,
      Function<E, Map<String, Object>> extensionExtractor) {
    Objects.requireNonNull(argumentExtractor, "Error argument extractor");
    Objects.requireNonNull(extensionExtractor, "Error extension extractor");
    return new ApiV2ErrorMapping(
        exceptionType,
        status,
        errorCode,
        description,
        exception -> argumentExtractor.apply(exceptionType.cast(exception)),
        ignored -> errorCode,
        exception -> extensionExtractor.apply(exceptionType.cast(exception)));
  }

  /** Maps reason-bearing failures while retaining a representative documented error code. */
  public static <E extends RuntimeException> ApiV2ErrorMapping withErrorCode(
      Class<E> type,
      HttpStatus status,
      String documentedCode,
      String description,
      Function<E, String> errorCodeExtractor) {
    return withErrorCode(
        type,
        status,
        documentedCode,
        description,
        errorCodeExtractor,
        ignored -> new Object[0],
        ignored -> Map.of());
  }

  /** Maps dynamic codes, message arguments and RFC 9457 extensions supplied by an operation. */
  public static <E extends RuntimeException> ApiV2ErrorMapping withErrorCode(
      Class<E> type,
      HttpStatus status,
      String documentedCode,
      String description,
      Function<E, String> errorCodeExtractor,
      Function<E, Object[]> argumentExtractor,
      Function<E, Map<String, Object>> extensionExtractor) {
    return new ApiV2ErrorMapping(
        type,
        status,
        documentedCode,
        description,
        exception -> argumentExtractor.apply(type.cast(exception)),
        exception -> errorCodeExtractor.apply(type.cast(exception)),
        exception -> extensionExtractor.apply(type.cast(exception)));
  }

  public ApiV2ResourceException translate(RuntimeException exception) {
    return new ApiV2ResourceException(
        exception,
        status,
        errorCodeExtractor.apply(exception),
        Objects.requireNonNull(argumentExtractor.apply(exception), "Mapped error arguments"),
        Objects.requireNonNull(extensionExtractor.apply(exception), "Mapped error extensions"));
  }

  OpenApiOperationDocumentation.Response documentedResponse() {
    return new OpenApiOperationDocumentation.Response(description, errorCode);
  }
}

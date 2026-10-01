package com.researchspace.api.v2.resource;

import com.researchspace.api.v2.controller.ApiV2Problem;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

/** Runtime and OpenAPI definition of one collection-operation failure. */
public record ApiV2ErrorMapping(
    Class<? extends RuntimeException> exceptionType,
    HttpStatus status,
    String errorCode,
    String description,
    Function<RuntimeException, Object[]> argumentExtractor,
    Function<RuntimeException, ApiV2Problem.Extensions> extensionExtractor) {

  public ApiV2ErrorMapping {
    Objects.requireNonNull(exceptionType, "Exception type");
    Objects.requireNonNull(status, "Error status");
    errorCode = requireText(errorCode, "Error code");
    description = requireText(description, "Error description");
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
        exceptionType,
        status,
        errorCode,
        description,
        argumentExtractor,
        ignored -> ApiV2Problem.Extensions.NONE);
  }

  /**
   * Maps a failure whose problem body carries RFC 9457 extension members, such as the event that
   * blocks a booking interval.
   */
  public static <E extends RuntimeException> ApiV2ErrorMapping of(
      Class<E> exceptionType,
      HttpStatus status,
      String errorCode,
      String description,
      Function<E, Object[]> argumentExtractor,
      Function<E, ApiV2Problem.Extensions> extensionExtractor) {
    Objects.requireNonNull(argumentExtractor, "Error argument extractor");
    Objects.requireNonNull(extensionExtractor, "Error extension extractor");
    return new ApiV2ErrorMapping(
        exceptionType,
        status,
        errorCode,
        description,
        exception -> argumentExtractor.apply(exceptionType.cast(exception)),
        exception -> extensionExtractor.apply(exceptionType.cast(exception)));
  }

  public ApiV2ResourceException translate(RuntimeException exception) {
    return new ApiV2ResourceException(
        exception,
        status,
        errorCode,
        Objects.requireNonNull(argumentExtractor.apply(exception), "Mapped error arguments"),
        Objects.requireNonNull(extensionExtractor.apply(exception), "Mapped error extensions"));
  }

  OpenApiOperationDocumentation.Response documentedResponse() {
    return new OpenApiOperationDocumentation.Response(description, errorCode);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}

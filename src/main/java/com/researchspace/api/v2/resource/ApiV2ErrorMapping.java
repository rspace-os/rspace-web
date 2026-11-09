package com.researchspace.api.v2.resource;

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
    Function<RuntimeException, Object[]> argumentExtractor) {

  public ApiV2ErrorMapping {
    Objects.requireNonNull(exceptionType, "Exception type");
    Objects.requireNonNull(status, "Error status");
    Assert.hasText(errorCode, "Error code must not be blank");
    Assert.hasText(description, "Error description must not be blank");
    Objects.requireNonNull(argumentExtractor, "Error argument extractor");
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
    Objects.requireNonNull(argumentExtractor, "Error argument extractor");
    return new ApiV2ErrorMapping(
        exceptionType,
        status,
        errorCode,
        description,
        exception -> argumentExtractor.apply(exceptionType.cast(exception)));
  }

  public static <E extends RuntimeException> Builder<E> forException(Class<E> type) {
    return new Builder<>(type);
  }

  public static final class Builder<E extends RuntimeException> {
    private final Class<E> type;
    private HttpStatus status;
    private String errorCode;
    private String description;
    private Function<E, Object[]> arguments = ignored -> new Object[0];

    private Builder(Class<E> type) {
      this.type = Objects.requireNonNull(type);
    }

    public Builder<E> status(HttpStatus status) {
      this.status = status;
      return this;
    }

    public Builder<E> documentedError(String code, String description) {
      this.errorCode = code;
      this.description = description;
      return this;
    }

    public Builder<E> arguments(Function<E, Object[]> arguments) {
      this.arguments = Objects.requireNonNull(arguments);
      return this;
    }

    public ApiV2ErrorMapping build() {
      return new ApiV2ErrorMapping(
          type, status, errorCode, description, arguments.compose(type::cast));
    }
  }

  public ApiV2ResourceException translate(RuntimeException exception) {
    return new ApiV2ResourceException(
        exception,
        status,
        errorCode,
        Objects.requireNonNull(argumentExtractor.apply(exception), "Mapped error arguments"));
  }

  OpenApiOperationDocumentation.Response documentedResponse() {
    return new OpenApiOperationDocumentation.Response(description, errorCode);
  }
}

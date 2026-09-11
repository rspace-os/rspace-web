package com.researchspace.service.chemistry;

import com.researchspace.model.field.LocalizedException;
import java.util.function.BiFunction;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ChemistryClientException extends RuntimeException implements LocalizedException {
  private static final Object[] NO_ARGS = {};

  private final String messageKey;

  private final Object[] args;
  private final HttpStatus status;

  public ChemistryClientException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = NO_ARGS;
    this.status = null;
  }

  public ChemistryClientException(String messageKey, Exception cause) {
    super(messageKey, cause);
    this.messageKey = messageKey;
    this.args = NO_ARGS;
    this.status = null;
  }

  public ChemistryClientException(String messageKey, Object[] args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
    this.status = null;
  }

  public ChemistryClientException(String messageKey, HttpStatus status, Throwable cause) {
    super(messageKey, cause);
    this.messageKey = messageKey;
    this.args = NO_ARGS;
    this.status = status;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(messageKey, args);
  }
}

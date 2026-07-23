package com.researchspace.service.chemistry;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ChemistryClientException extends RuntimeException {
  private final String messageKey;
  private final Object[] args;
  private final HttpStatus status;

  public ChemistryClientException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = new Object[0];
    this.status = null;
  }

  public ChemistryClientException(String messageKey, Exception cause) {
    super(messageKey, cause);
    this.messageKey = messageKey;
    this.args = new Object[0];
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
    this.args = new Object[0];
    this.status = status;
  }
}

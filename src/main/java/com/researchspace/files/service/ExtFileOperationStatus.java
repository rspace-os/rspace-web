package com.researchspace.files.service;

import lombok.Value;

@Value
public class ExtFileOperationStatus<T> {
  private int httpCode;
  private String errorMessage;
  private T response;

  public boolean isOK() {
    return httpCode >= 200 && httpCode < 400;
  }

  public boolean isAuthError() {
    return httpCode == 401; // || other codes?
  }

  public boolean isOtherError() {
    return httpCode >= 400 && !isAuthError();
  }
}

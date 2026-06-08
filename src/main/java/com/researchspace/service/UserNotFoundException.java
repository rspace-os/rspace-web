package com.researchspace.service;

public class UserNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 8296339911538687850L;

  public UserNotFoundException(String message) {
    super(message);
  }
}

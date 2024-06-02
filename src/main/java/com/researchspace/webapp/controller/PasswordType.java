package com.researchspace.webapp.controller;

public enum PasswordType {
  LOGIN_PASSWORD("password"),
  VERIFICATION_PASSWORD("verification password");

  private String text;

  PasswordType(String text) {
    this.text = text;
  }

  @Override
  public String toString() {
    return this.text;
  }

  public String capitalise() { // Used in JSP
    return this.text.substring(0, 1).toUpperCase() + this.text.substring(1);
  }
}

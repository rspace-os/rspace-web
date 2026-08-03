package com.researchspace.webapp.controller;

import java.util.Locale;

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
    return this.text.substring(0, 1).toUpperCase(Locale.ROOT) + this.text.substring(1);
  }
}

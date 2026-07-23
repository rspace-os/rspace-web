package com.researchspace.service;

import static java.util.Objects.requireNonNull;

/** A complete rendered email: resolved subject plus HTML and plain-text alternatives. */
public record EmailContent(String subject, String htmlContent, String plainTextContent) {

  public EmailContent {
    requireNonNull(subject, "subject");
    requireNonNull(htmlContent, "htmlContent");
    requireNonNull(plainTextContent, "plainTextContent");
  }
}

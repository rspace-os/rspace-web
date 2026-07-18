package com.researchspace.service;

import static java.util.Objects.requireNonNull;

/**
 * The complete HTML and plain-text alternatives of an email body, together with the resolved
 * subject line. {@code subject} is nullable: it is populated when the content is generated from an
 * i18n subject key, and left null for communications whose subject is supplied dynamically at send
 * time (e.g. {@code Communication#getSubject()}).
 */
public record EmailContent(String subject, String htmlContent, String plainTextContent) {

  public EmailContent {
    requireNonNull(htmlContent, "htmlContent");
    requireNonNull(plainTextContent, "plainTextContent");
  }
}

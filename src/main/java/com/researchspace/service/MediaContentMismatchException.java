package com.researchspace.service;

import java.io.IOException;
import lombok.Getter;

/**
 * Thrown when an uploaded file's content is not the type its filename claims, so the upload is
 * rejected rather than stored under a content type it does not match.
 *
 * <p>Carries a message bundle key rather than a ready-made message, so each caller can render it in
 * the form its own clients expect.
 */
@Getter
public class MediaContentMismatchException extends IOException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final Object[] args;

  public MediaContentMismatchException(String errorCode, Object... args) {
    super(errorCode);
    this.errorCode = errorCode;
    this.args = args;
  }
}

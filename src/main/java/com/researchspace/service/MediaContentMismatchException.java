package com.researchspace.service;

import lombok.Getter;

/**
 * Thrown when an uploaded file's content is not the type its filename claims, so the upload is
 * rejected rather than stored under a content type it does not match.
 *
 * <p>Carries a message bundle key rather than a ready-made message, so each caller can render it in
 * the form its own clients expect.
 *
 * <p>Checked on purpose. A rejection means nothing was written, so callers inside a transaction
 * should be able to report the file and carry on. An unchecked exception could not offer that: by
 * Spring's default rule it marks the surrounding transaction for rollback at the boundary it passes
 * through, which no amount of catching further up can undo. Being checked also makes the compiler,
 * rather than a reviewer, the thing that notices a new caller has to decide what to do.
 */
@Getter
public class MediaContentMismatchException extends Exception {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final Object[] args;

  public MediaContentMismatchException(String errorCode, Object... args) {
    super(errorCode);
    this.errorCode = errorCode;
    this.args = args;
  }
}

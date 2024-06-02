package com.researchspace.service;

/**
 * RSpace exception pointing that the disk space limit is going to be hit, and current operation
 * should be stopped.
 */
public class DiskSpaceLimitException extends RuntimeException {

  private static final long serialVersionUID = 7242584311571579082L;

  public DiskSpaceLimitException(String msg) {
    super(msg);
  }
}

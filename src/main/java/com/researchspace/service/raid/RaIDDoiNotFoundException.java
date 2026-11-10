package com.researchspace.service.raid;

/** Thrown when RAiD refuses a related object link because it cannot resolve it. */
public class RaIDDoiNotFoundException extends RuntimeException {

  public RaIDDoiNotFoundException(String msg, Throwable cause) {
    super(msg, cause);
  }

  private static final long serialVersionUID = 4127730517324907351L;
}

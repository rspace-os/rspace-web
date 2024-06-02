package com.researchspace.service;

/**
 * For Spring-Social managed Connections to 3rd party webservices. Thrown if App is not authorised,
 * .i.e. if there is no entry in UserConnection table.
 */
public class AppNotAuthorisedException extends RuntimeException {

  public AppNotAuthorisedException(String msg) {
    super(msg);
  }

  /** */
  private static final long serialVersionUID = -783496441851458838L;
}

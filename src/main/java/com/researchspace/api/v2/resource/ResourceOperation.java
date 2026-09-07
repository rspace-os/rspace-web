package com.researchspace.api.v2.resource;

public enum ResourceOperation {
  LIST("GET"),
  COUNT("GET"),
  READ("GET"),
  CREATE("POST"),
  BULK_CREATE("POST"),
  UPDATE("PATCH"),
  BULK_UPDATE("PATCH"),
  DELETE("DELETE"),
  BULK_DELETE("DELETE");

  private final String httpMethod;

  ResourceOperation(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  /**
   * The HTTP method this operation is reached by.
   *
   * <p>Needed so a refusal can name the methods a resource does serve. RFC 9110 requires {@code
   * Allow} on a 405, and without this the enum cannot say what to put in it.
   */
  public String httpMethod() {
    return httpMethod;
  }
}

package com.researchspace.netfiles;

import java.io.IOException;

public class NfsException extends IOException {

  private static final long serialVersionUID = -5305143644082350089L;

  public NfsException(String msg, Throwable cause) {
    super(msg, cause);
  }
}

package com.researchspace.netfiles;

public abstract class NfsAbstractClient implements NfsClient {

  private static final long serialVersionUID = -7266247075194021716L;

  protected String username;

  public NfsAbstractClient(String username) {
    this.username = username;
  }

  @Override
  public String getUsername() {
    return username;
  }
}

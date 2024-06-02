package com.researchspace.api.v1.controller;

public enum API_VERSION {
  ONE(1);
  private int version;

  private API_VERSION(int version) {
    this.version = version;
  }

  public int getVersion() {
    return version;
  }
}

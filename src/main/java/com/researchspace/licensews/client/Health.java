package com.researchspace.licensews.client;

import lombok.Data;

/** Simple POJO class more dersializing JSON response from license server. */
@Data
public class Health {
  private String status;
  private String database;
  private String hello;

  public Health() {
    super();
  }
}

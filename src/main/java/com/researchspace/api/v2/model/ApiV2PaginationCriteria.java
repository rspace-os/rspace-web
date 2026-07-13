package com.researchspace.api.v2.model;

import lombok.Data;

@Data
public class ApiV2PaginationCriteria {

  public static final int MAX_LIMIT = 100;

  private int limit = 20;
  private int page = 1;
}

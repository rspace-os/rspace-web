package com.researchspace.integrations.jove.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoveSearchRequest {

  private String queryString;
  private String author;
  private String institution;
  private Long pageNumber;
  private Long pageSize;
}

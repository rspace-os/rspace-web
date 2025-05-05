package com.researchspace.service.chemistry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchResultDTO {
  private String id;
  private String name;
  private String chemId;
  private String displayId;
  private String author;
}

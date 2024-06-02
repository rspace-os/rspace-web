package com.researchspace.integrations.jove.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JoveSearchResult {

  @JsonProperty("countall")
  private Long countAll;

  @JsonProperty("pagesize")
  private Long pageSize;

  @JsonProperty("pagecount")
  private Long pageCount;

  @JsonProperty("articlelist")
  private List<JoveArticle> articleList;
}

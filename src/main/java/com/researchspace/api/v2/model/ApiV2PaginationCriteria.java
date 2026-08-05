package com.researchspace.api.v2.model;

import com.researchspace.model.collection.CollectionQueryLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ApiV2PaginationCriteria {

  public static final int MAX_LIMIT = CollectionQueryLimits.MAX_PAGE_SIZE;

  @Min(value = 1, message = "{errors.api.pagination.limit.min}")
  @Max(value = MAX_LIMIT, message = "{errors.api.pagination.limit.max}")
  private int limit = 20;

  @Min(value = 1, message = "{errors.api.pagination.page.min}")
  private int page = 1;
}

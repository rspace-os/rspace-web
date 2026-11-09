package com.researchspace.api.v2.model;

import com.researchspace.model.collection.CollectionQueryLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ApiV2PaginationCriteria {

  @Min(value = 1, message = "{errors.api.pagination.limit.min}")
  @Max(value = CollectionQueryLimits.MAX_PAGE_SIZE, message = "{errors.api.pagination.limit.max}")
  private int limit = 20;

  @Min(value = 1, message = "{errors.api.pagination.page.min}")
  private int page = 1;
}

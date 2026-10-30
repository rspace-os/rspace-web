package com.researchspace.api.v2.model;

import com.researchspace.model.collection.CollectionQueryLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Query options shared by REST v2 collection endpoints. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiV2CollectionQuery extends ApiV2PaginationCriteria {

  private String where;

  private String sort;

  @io.swagger.v3.oas.annotations.media.Schema(
      description = "Include totalDocs and totalPages. Defaults to false.",
      defaultValue = "false")
  private boolean totals;

  @Min(value = 0, message = "{errors.api.v2.depth.min}")
  @Max(value = CollectionQueryLimits.MAX_RELATIONSHIP_DEPTH, message = "{errors.api.v2.depth.max}")
  private int depth;
}

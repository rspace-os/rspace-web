package com.researchspace.api.v2.model;

import com.researchspace.model.collection.CollectionQueryLimits;
import io.swagger.v3.oas.annotations.media.Schema;
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

  @Schema(
      description = "Include totalDocs and totalPages. Defaults to false.",
      defaultValue = "false")
  private boolean totals;

  @Min(value = 1, message = "{errors.api.v2.count.max.min}")
  @Max(value = 10000, message = "{errors.api.v2.count.max.max}")
  @Schema(
      description =
          "Cap requested totals at max. When exceeded, totalIsLowerBound is true and totalPages is"
              + " omitted.",
      minimum = "1",
      maximum = "10000")
  private Integer max;

  @Min(value = 0, message = "{errors.api.v2.depth.min}")
  @Max(value = CollectionQueryLimits.MAX_RELATIONSHIP_DEPTH, message = "{errors.api.v2.depth.max}")
  private int depth;
}

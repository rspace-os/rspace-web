package com.researchspace.api.v2.model;

import com.researchspace.model.collection.CollectionQueryLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Query options shared by Payload-shaped collection endpoints. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiV2CollectionQuery extends ApiV2PaginationCriteria {

  private String where;

  private String sort;

  @Min(value = 0, message = "{errors.api.v2.depth.min}")
  @Max(value = CollectionQueryLimits.MAX_RELATIONSHIP_DEPTH, message = "{errors.api.v2.depth.max}")
  private int depth;
}

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

  public static final int MAX_DEPTH = CollectionQueryLimits.MAX_RELATIONSHIP_DEPTH;

  /**
   * Maximum length of the {@code where} expression.
   *
   * <p>Enforced in {@code ApiV2ResourceRequestParser}, not by a {@code @Size} constraint here. A
   * constraint would be unreachable: the controller's {@code validateRawQuery} advice runs before
   * argument resolution and measures the still-encoded value, and percent-encoding only ever
   * lengthens a string, so anything a decoded {@code @Size} check would reject has already been
   * rejected. Keeping one enforcement point also keeps the count and bulk endpoints, which take
   * {@code where} as a bare {@code @RequestParam} rather than binding this class, under the same
   * rule.
   */
  public static final int MAX_WHERE_LENGTH = CollectionQueryLimits.MAX_WHERE_LENGTH;

  private String where;

  private String sort;

  @Min(value = 0, message = "{errors.api.v2.depth.min}")
  @Max(value = MAX_DEPTH, message = "{errors.api.v2.depth.max}")
  private int depth;
}

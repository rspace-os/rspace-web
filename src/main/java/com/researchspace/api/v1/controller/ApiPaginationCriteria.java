package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiSortEnum;
import com.researchspace.service.ListFormatUtils;
import java.util.Arrays;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class ApiPaginationCriteria {

  static final String ASC_PARAM = "asc";
  static final String DESC_PARAM = "desc";

  public static final int MAX_PAGE_SIZE = 100;
  public static final Integer DEFAULT_PAGE_SIZE = 20;

  public static final String PAGE_NUMBER_REQ_PARAM = "pageNumber";
  public static final String PAGE_SIZE_REQ_PARAM = "pageSize";
  public static final String ORDERBY_REQ_PARAM = "orderBy";

  @Min(value = 0, message = "Page number must be 0 or greater.")
  Integer pageNumber = 0;

  @Range(
      min = 1,
      max = MAX_PAGE_SIZE,
      message = "Page size must be between 1 and " + MAX_PAGE_SIZE + ".")
  Integer pageSize = DEFAULT_PAGE_SIZE;

  String orderBy;

  abstract String getDefaultOrderBy();

  abstract String getOrderBy();

  public abstract ApiSortEnum getSort();

  public abstract ApiPaginationCriteria previousPage();

  public abstract ApiPaginationCriteria nextPage();

  public abstract ApiPaginationCriteria firstPage();

  public abstract ApiPaginationCriteria lastPage(int lastPageNum);

  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    if (!DEFAULT_PAGE_SIZE.equals(pageSize)) {
      rc.add(PAGE_SIZE_REQ_PARAM, pageSize + "");
    }
    rc.add(PAGE_NUMBER_REQ_PARAM, pageNumber + "");
    addOrderByToMap(rc);
    return rc;
  }

  abstract void addOrderByToMap(LinkedMultiValueMap<String, String> rc);

  /**
   * Carries the invalid {@code orderBy} value and the allowed values, so that the caller (which has
   * access to the localized {@link com.researchspace.service.MessageSourceUtils}) can build the
   * user-facing message. This class is a plain data-binding target constructed by Spring MVC, so it
   * cannot itself have a message source injected.
   */
  static class InvalidSortParameterException extends IllegalArgumentException {
    private final String orderBy;
    private final String[] validParams;

    InvalidSortParameterException(String orderBy, String[] validParams) {
      // Fallback only: if this ever escapes the try/catch in
      // BaseApiController#getPaginationCriteriaForApiSearch (its only intended caller), callers
      // still get a non-null message instead of NPE-prone null from an unset Throwable message.
      super(
          "Problem parsing sort parameter: ["
              + orderBy
              + "]. It must be one of "
              + ListFormatUtils.formatList(Arrays.asList(validParams))
              + ".");
      this.orderBy = orderBy;
      this.validParams = validParams;
    }

    String getOrderBy() {
      return orderBy;
    }

    String[] getValidParams() {
      return validParams;
    }
  }
}

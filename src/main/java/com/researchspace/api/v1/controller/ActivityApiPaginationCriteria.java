package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.StringUtils.join;

import com.researchspace.api.v1.model.ApiSortEnum;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;

/** Request object encapsulating pagination and query parameters for a searchable listing. */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
public class ActivityApiPaginationCriteria extends ApiPaginationCriteria {

  static class EventApiPaginationCriteriaBuilder {
    // defaults when using Builder
    private Integer pageNumber = 0;
    private Integer pageSize = DEFAULT_PAGE_SIZE;
    private String orderBy = DATE_DESC_API_PARAM;
  }

  public ActivityApiPaginationCriteria() {
    this.orderBy = getDefaultOrderBy();
  }

  static final String DATE_PARAM = "date";

  public static final String DATE_DESC_API_PARAM = DATE_PARAM + " " + DESC_PARAM;
  public static final String DATE_ASC_API_PARAM = DATE_PARAM + " " + ASC_PARAM;

  static final String[] ALL_PARAMS = new String[] {DATE_DESC_API_PARAM, DATE_ASC_API_PARAM};

  static final String VALID_SORTORDER_PATTERN =
      "(" + DATE_DESC_API_PARAM + ")|" + "(" + DATE_ASC_API_PARAM + ")";

  public ActivityApiPaginationCriteria(int pageNum, Integer pageSize, String orderBy) {
    super(pageNum, pageSize, orderBy);
  }

  void addOrderByToMap(LinkedMultiValueMap<String, String> rc) {
    if (!DATE_DESC_API_PARAM.equals(orderBy)) {
      rc.add(ORDERBY_REQ_PARAM, orderBy);
    }
  }

  @Override
  String getDefaultOrderBy() {
    return DATE_DESC_API_PARAM;
  }

  @Override
  @Pattern(regexp = VALID_SORTORDER_PATTERN)
  String getOrderBy() {
    return orderBy;
  }

  @Override
  public ApiSortEnum getSort() {
    if (!StringUtils.isEmpty(orderBy)) {
      switch (orderBy) {
        case DATE_ASC_API_PARAM:
          return ApiSortEnum.DATE_ASC;
        case DATE_DESC_API_PARAM:
          return ApiSortEnum.DATE_DESC;
        default:
          throw new IllegalArgumentException(
              "Problem  parsing sort parameter: ["
                  + getOrderBy()
                  + "]. It must be one of "
                  + join(ALL_PARAMS, ",")
                  + ".");
      }
    }
    return null;
  }

  @Override
  public ActivityApiPaginationCriteria previousPage() {
    if (getPageNumber() == 0) {
      throw new IllegalArgumentException(
          "Can't create previous pagination criteria - at first page!");
    }
    return new ActivityApiPaginationCriteria(getPageNumber() - 1, getPageSize(), getOrderBy());
  }

  public ActivityApiPaginationCriteria nextPage() {
    return new ActivityApiPaginationCriteria(getPageNumber() + 1, getPageSize(), getOrderBy());
  }

  public ActivityApiPaginationCriteria firstPage() {
    return new ActivityApiPaginationCriteria(0, getPageSize(), getOrderBy());
  }

  public ActivityApiPaginationCriteria lastPage(int lastPageNum) {
    return new ActivityApiPaginationCriteria(lastPageNum, getPageSize(), getOrderBy());
  }
}

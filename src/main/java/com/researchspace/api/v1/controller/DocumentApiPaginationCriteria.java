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
public class DocumentApiPaginationCriteria extends ApiPaginationCriteria {

  static class DocumentApiPaginationCriteriaBuilder {
    // defaults when using Builder
    private Integer pageNumber = 0;
    private Integer pageSize = DEFAULT_PAGE_SIZE;
    private String orderBy = LAST_MODIFIED_DESC_API_PARAM;
  }

  // core constants
  static final String NAME_PARAM = "name";
  static final String CREATED_PARAM = "created";
  static final String LAST_MODIFIED_PARAM = "lastModified";

  static final String FAVORITE_PARAM = "favorites";
  static final String SHARED_WITH_ME_PARAM = "sharedWithMe";

  // regexes for validation
  static final String NAME_ASC_API_PARAM_REGEXP = NAME_PARAM + "\\s+" + ASC_PARAM;
  static final String NAME_DESC_API_PARAM_REGEXP = NAME_PARAM + "\\s+" + DESC_PARAM;
  static final String CREATED_ASC_API_PARAM_REGEXP = CREATED_PARAM + "\\s+" + ASC_PARAM;
  static final String CREATED_DESC_API_PARAM_REGEXP = CREATED_PARAM + "\\s+" + DESC_PARAM;
  static final String LAST_MODIFIED_ASC_API_PARAM_REGEXP = LAST_MODIFIED_PARAM + "\\s+" + ASC_PARAM;
  static final String LAST_MODIFIED_DESC_API_PARAM_REGEXP =
      LAST_MODIFIED_PARAM + "\\s+" + DESC_PARAM;

  public static final String NAME_DESC_API_PARAM = NAME_PARAM + " " + DESC_PARAM;
  public static final String NAME_ASC_API_PARAM = NAME_PARAM + " " + ASC_PARAM;
  public static final String CREATED_DESC_API_PARAM = CREATED_PARAM + " " + DESC_PARAM;
  public static final String CREATED_ASC_API_PARAM = CREATED_PARAM + " " + ASC_PARAM;
  public static final String LAST_MODIFIED_DESC_API_PARAM = LAST_MODIFIED_PARAM + " " + DESC_PARAM;
  public static final String LAST_MODIFIED_ASC_API_PARAM = LAST_MODIFIED_PARAM + " " + ASC_PARAM;

  static final String[] ALL_PARAMS_REGEXES =
      new String[] {
        NAME_DESC_API_PARAM_REGEXP,
        NAME_ASC_API_PARAM,
        CREATED_DESC_API_PARAM,
        CREATED_ASC_API_PARAM,
        LAST_MODIFIED_DESC_API_PARAM,
        LAST_MODIFIED_ASC_API_PARAM,
      };

  static final String[] ALL_PARAMS =
      new String[] {
        NAME_DESC_API_PARAM,
        NAME_ASC_API_PARAM,
        CREATED_DESC_API_PARAM,
        CREATED_ASC_API_PARAM,
        LAST_MODIFIED_DESC_API_PARAM,
        LAST_MODIFIED_ASC_API_PARAM
      };

  static final String VALID_SORTORDER_PATTERN =
      "("
          + NAME_ASC_API_PARAM_REGEXP
          + ")|"
          + "("
          + NAME_DESC_API_PARAM_REGEXP
          + ")|"
          + "("
          + CREATED_ASC_API_PARAM_REGEXP
          + ")|"
          + "("
          + CREATED_DESC_API_PARAM_REGEXP
          + ")|"
          + "("
          + LAST_MODIFIED_ASC_API_PARAM_REGEXP
          + ")|"
          + "("
          + LAST_MODIFIED_DESC_API_PARAM_REGEXP
          + ")";

  @Override
  public DocumentApiPaginationCriteria previousPage() {
    if (getPageNumber() == 0) {
      throw new IllegalArgumentException(
          "Can't create previous pagination criteria - at first page!");
    }
    return new DocumentApiPaginationCriteria(getPageNumber() - 1, getPageSize(), getOrderBy());
  }

  public DocumentApiPaginationCriteria nextPage() {
    return new DocumentApiPaginationCriteria(getPageNumber() + 1, getPageSize(), getOrderBy());
  }

  public DocumentApiPaginationCriteria firstPage() {
    return new DocumentApiPaginationCriteria(0, getPageSize(), getOrderBy());
  }

  public DocumentApiPaginationCriteria lastPage(int lastPageNum) {
    return new DocumentApiPaginationCriteria(lastPageNum, getPageSize(), getOrderBy());
  }

  public DocumentApiPaginationCriteria() {
    this.orderBy = getDefaultOrderBy();
  }

  @Builder
  public DocumentApiPaginationCriteria(int pageNumber, Integer pageSize, String orderBy) {
    super(pageNumber, pageSize, orderBy);
  }

  void addOrderByToMap(LinkedMultiValueMap<String, String> rc) {
    if (!LAST_MODIFIED_DESC_API_PARAM.equals(orderBy)) {
      rc.add(ORDERBY_REQ_PARAM, orderBy);
    }
  }

  @Override
  String getDefaultOrderBy() {
    return LAST_MODIFIED_DESC_API_PARAM;
  }

  @Pattern(regexp = VALID_SORTORDER_PATTERN)
  String getOrderBy() {
    return orderBy;
  }

  @Override
  public ApiSortEnum getSort() {
    if (!StringUtils.isEmpty(orderBy)) {
      switch (orderBy) {
        case LAST_MODIFIED_ASC_API_PARAM:
          return ApiSortEnum.LAST_MODIFIED_ASC;
        case LAST_MODIFIED_DESC_API_PARAM:
          return ApiSortEnum.LAST_MODIFIED_DESC;
        case CREATED_ASC_API_PARAM:
          return ApiSortEnum.CREATED_ASC;
        case CREATED_DESC_API_PARAM:
          return ApiSortEnum.CREATED_DESC;
        case NAME_ASC_API_PARAM:
          return ApiSortEnum.NAME_ASC;
        case NAME_DESC_API_PARAM:
          return ApiSortEnum.NAME_DESC;
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
}

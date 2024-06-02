package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.StringUtils.join;

import com.researchspace.api.v1.model.ApiSortEnum;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;

@Data
@EqualsAndHashCode(callSuper = true)
public class SysadminUserPaginationCriteria extends ApiPaginationCriteria {

  static final String CREATIONDATE_PARAM = "creationDate";

  public static final String CREATIONDATE_DESC_API_PARAM = CREATIONDATE_PARAM + " " + DESC_PARAM;
  public static final String CREATIONDATE_ASC_API_PARAM = CREATIONDATE_PARAM + " " + ASC_PARAM;

  static final String VALID_SORTORDER_PATTERN =
      "(" + CREATIONDATE_DESC_API_PARAM + ")|" + "(" + CREATIONDATE_ASC_API_PARAM + ")";
  static final String[] ALL_PARAMS =
      new String[] {CREATIONDATE_DESC_API_PARAM, CREATIONDATE_ASC_API_PARAM};

  public SysadminUserPaginationCriteria() {
    this.orderBy = getDefaultOrderBy();
  }

  public SysadminUserPaginationCriteria(int pageNum, Integer pageSize, String orderBy) {
    super(pageNum, pageSize, orderBy);
  }

  @Override
  String getDefaultOrderBy() {
    return CREATIONDATE_ASC_API_PARAM;
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
        case CREATIONDATE_ASC_API_PARAM:
          return ApiSortEnum.CREATION_DATE_ASC;
        case CREATIONDATE_DESC_API_PARAM:
          return ApiSortEnum.CREATION_DATE_DESC;
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
  public ApiPaginationCriteria previousPage() {
    if (getPageNumber() == 0) {
      throw new IllegalArgumentException(
          "Can't create previous pagination criteria - at first page!");
    }
    return new SysadminUserPaginationCriteria(getPageNumber() - 1, getPageSize(), getOrderBy());
  }

  public ApiPaginationCriteria nextPage() {
    return new SysadminUserPaginationCriteria(getPageNumber() + 1, getPageSize(), getOrderBy());
  }

  public ApiPaginationCriteria firstPage() {
    return new SysadminUserPaginationCriteria(0, getPageSize(), getOrderBy());
  }

  public ApiPaginationCriteria lastPage(int lastPageNum) {
    return new SysadminUserPaginationCriteria(lastPageNum, getPageSize(), getOrderBy());
  }

  @Override
  void addOrderByToMap(LinkedMultiValueMap<String, String> rc) {
    if (!getDefaultOrderBy().equals(orderBy)) {
      rc.add(ORDERBY_REQ_PARAM, orderBy);
    }
  }
}

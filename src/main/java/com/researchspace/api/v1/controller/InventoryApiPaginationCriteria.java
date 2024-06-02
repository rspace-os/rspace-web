package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.StringUtils.join;

import com.researchspace.api.v1.model.ApiSortEnum;
import javax.validation.constraints.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;

public class InventoryApiPaginationCriteria extends ApiPaginationCriteria {

  static final String NAME_PARAM = "name";
  static final String TYPE_PARAM = "type";
  static final String GLOBAL_ID_PARAM = "globalId";
  static final String CREATION_DATE_PARAM = "creationDate";
  static final String MODIFICATION_DATE_PARAM = "modificationDate";

  public static final String NAME_ASC_API_PARAM = NAME_PARAM + " " + ASC_PARAM;
  public static final String NAME_DESC_API_PARAM = NAME_PARAM + " " + DESC_PARAM;
  public static final String TYPE_ASC_API_PARAM = TYPE_PARAM + " " + ASC_PARAM;
  public static final String TYPE_DESC_API_PARAM = TYPE_PARAM + " " + DESC_PARAM;
  public static final String GLOBAL_ID_ASC_API_PARAM = GLOBAL_ID_PARAM + " " + ASC_PARAM;
  public static final String GLOBAL_ID_DESC_API_PARAM = GLOBAL_ID_PARAM + " " + DESC_PARAM;
  public static final String CREATION_DATE_ASC_API_PARAM = CREATION_DATE_PARAM + " " + ASC_PARAM;
  public static final String CREATION_DATE_DESC_API_PARAM = CREATION_DATE_PARAM + " " + DESC_PARAM;
  public static final String MODIFICATION_DATE_ASC_API_PARAM =
      MODIFICATION_DATE_PARAM + " " + ASC_PARAM;
  public static final String MODIFICATION_DATE_DESC_API_PARAM =
      MODIFICATION_DATE_PARAM + " " + DESC_PARAM;

  static final String VALID_SORTORDER_PATTERN =
      "("
          + NAME_ASC_API_PARAM
          + ")|"
          + "("
          + NAME_DESC_API_PARAM
          + ")|"
          + "("
          + TYPE_ASC_API_PARAM
          + ")|"
          + "("
          + TYPE_DESC_API_PARAM
          + ")|"
          + "("
          + GLOBAL_ID_ASC_API_PARAM
          + ")|"
          + "("
          + GLOBAL_ID_DESC_API_PARAM
          + ")|"
          + "("
          + CREATION_DATE_ASC_API_PARAM
          + ")|"
          + "("
          + CREATION_DATE_DESC_API_PARAM
          + ")|"
          + "("
          + MODIFICATION_DATE_ASC_API_PARAM
          + ")|"
          + "("
          + MODIFICATION_DATE_DESC_API_PARAM
          + ")";

  static final String[] ALL_PARAMS =
      new String[] {
        NAME_DESC_API_PARAM, NAME_ASC_API_PARAM,
        TYPE_DESC_API_PARAM, TYPE_ASC_API_PARAM,
        GLOBAL_ID_ASC_API_PARAM, GLOBAL_ID_DESC_API_PARAM,
        CREATION_DATE_ASC_API_PARAM, CREATION_DATE_DESC_API_PARAM,
        MODIFICATION_DATE_ASC_API_PARAM, MODIFICATION_DATE_DESC_API_PARAM
      };

  public InventoryApiPaginationCriteria() {
    this.orderBy = getDefaultOrderBy();
  }

  public InventoryApiPaginationCriteria(int pageNum, Integer pageSize, String orderBy) {
    super(pageNum, pageSize, orderBy);
  }

  @Override
  String getDefaultOrderBy() {
    return NAME_ASC_API_PARAM;
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
        case NAME_ASC_API_PARAM:
          return ApiSortEnum.NAME_ASC;
        case NAME_DESC_API_PARAM:
          return ApiSortEnum.NAME_DESC;
        case TYPE_ASC_API_PARAM:
          return ApiSortEnum.TYPE_ASC;
        case TYPE_DESC_API_PARAM:
          return ApiSortEnum.TYPE_DESC;
        case GLOBAL_ID_ASC_API_PARAM:
          return ApiSortEnum.GLOBAL_ID_ASC;
        case GLOBAL_ID_DESC_API_PARAM:
          return ApiSortEnum.GLOBAL_ID_DESC;
        case CREATION_DATE_ASC_API_PARAM:
          return ApiSortEnum.CREATION_DATE_ASC;
        case CREATION_DATE_DESC_API_PARAM:
          return ApiSortEnum.CREATION_DATE_DESC;
        case MODIFICATION_DATE_ASC_API_PARAM:
          return ApiSortEnum.MODIFICATION_DATE_ASC;
        case MODIFICATION_DATE_DESC_API_PARAM:
          return ApiSortEnum.MODIFICATION_DATE_DESC;
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
  public InventoryApiPaginationCriteria previousPage() {
    if (getPageNumber() == 0) {
      throw new IllegalArgumentException(
          "Can't create previous pagination criteria - at first page!");
    }
    return new InventoryApiPaginationCriteria(getPageNumber() - 1, getPageSize(), getOrderBy());
  }

  @Override
  public InventoryApiPaginationCriteria nextPage() {
    return new InventoryApiPaginationCriteria(getPageNumber() + 1, getPageSize(), getOrderBy());
  }

  @Override
  public InventoryApiPaginationCriteria firstPage() {
    return new InventoryApiPaginationCriteria(0, getPageSize(), getOrderBy());
  }

  @Override
  public InventoryApiPaginationCriteria lastPage(int lastPageNum) {
    return new InventoryApiPaginationCriteria(lastPageNum, getPageSize(), getOrderBy());
  }

  @Override
  void addOrderByToMap(LinkedMultiValueMap<String, String> rc) {
    if (!NAME_DESC_API_PARAM.equals(orderBy)) {
      rc.add(ORDERBY_REQ_PARAM, orderBy);
    }
  }
}

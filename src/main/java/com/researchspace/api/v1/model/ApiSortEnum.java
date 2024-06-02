package com.researchspace.api.v1.model;

import com.axiope.search.SearchUtils;
import com.researchspace.core.util.SortOrder;
import lombok.Getter;

@Getter
public enum ApiSortEnum {
  LAST_MODIFIED_ASC(SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED, SortOrder.ASC),
  LAST_MODIFIED_DESC(SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED, SortOrder.DESC),

  CREATED_ASC(SearchUtils.BASE_RECORD_ORDER_BY_CREATED, SortOrder.ASC),
  CREATED_DESC(SearchUtils.BASE_RECORD_ORDER_BY_CREATED, SortOrder.DESC),

  NAME_ASC(SearchUtils.ORDER_BY_NAME, SortOrder.ASC),
  NAME_DESC(SearchUtils.ORDER_BY_NAME, SortOrder.DESC),

  USERNAME_ASC("username", SortOrder.ASC),
  USERNAME_DESC("username", SortOrder.DESC),

  CREATION_DATE_ASC(SearchUtils.ORDER_BY_CREATION_DATE, SortOrder.ASC),
  CREATION_DATE_DESC(SearchUtils.ORDER_BY_CREATION_DATE, SortOrder.DESC),

  DATE_ASC("date", SortOrder.ASC),
  DATE_DESC("date", SortOrder.DESC),

  TYPE_ASC(SearchUtils.ORDER_BY_TYPE, SortOrder.ASC),
  TYPE_DESC(SearchUtils.ORDER_BY_TYPE, SortOrder.DESC),

  GLOBAL_ID_ASC(SearchUtils.ORDER_BY_GLOBAL_ID, SortOrder.ASC),
  GLOBAL_ID_DESC(SearchUtils.ORDER_BY_GLOBAL_ID, SortOrder.DESC),

  MODIFICATION_DATE_ASC(SearchUtils.ORDER_BY_MODIFICATION_DATE, SortOrder.ASC),
  MODIFICATION_DATE_DESC(SearchUtils.ORDER_BY_MODIFICATION_DATE, SortOrder.DESC);

  private String orderBy;
  private SortOrder sortOrder;

  private ApiSortEnum(String orderBy, SortOrder sortOrder) {
    this.orderBy = orderBy;
    this.sortOrder = sortOrder;
  }
}

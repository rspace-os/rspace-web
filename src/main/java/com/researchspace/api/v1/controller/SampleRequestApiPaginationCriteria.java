package com.researchspace.api.v1.controller;

import jakarta.validation.constraints.Pattern;

/** Sample requests sort by creation date only, newest first by default. */
public class SampleRequestApiPaginationCriteria extends InventoryApiPaginationCriteria {

  @Override
  String getDefaultOrderBy() {
    return CREATION_DATE_DESC_API_PARAM;
  }

  @Override
  @Pattern(regexp = "(" + CREATION_DATE_ASC_API_PARAM + ")|(" + CREATION_DATE_DESC_API_PARAM + ")")
  String getOrderBy() {
    return orderBy;
  }
}

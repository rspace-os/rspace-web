package com.researchspace.core.util;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * POJO to hold pagination criteria. If not configured otherwise, will return 10 results from page1
 * of a listing, in descending order
 *
 * @param <T> type parameter for the object being sorted on; is used for dynamically accessing
 *     fields * of the class to order by
 */
@EqualsAndHashCode(of = {"orderBy", "pageNumber", "resultsPerPage", "sortOrder"})
@ToString
public class BasicPaginationCriteria<T> implements Serializable, IPagination<T> {

  private static final long serialVersionUID = 1L;

  private FilterCriteria searchCriteria;
  private Class<T> clazz;
  private String orderBy;
  private SortOrder sortOrder = SortOrder.DESC;
  private Long pageNumber = 0L;
  private Integer resultsPerPage = DEFAULT_RESULTS_PERPAGE;

  /**
   * @param clazz the class of the listed objects
   */
  public BasicPaginationCriteria(Class<T> clazz) {
    this.clazz = clazz;
  }

  public BasicPaginationCriteria() {}

  /**
   * Creates default {@link BasicPaginationCriteria} with 10 records per page, set to page 0, sort
   * order DESC
   *
   * @param clazz
   * @return A PaginationCriteria<T> object.
   */
  public static <T> IPagination<T> createDefaultForClass(Class<T> clazz) {
    return createForClass(clazz, null, SortOrder.DESC.toString(), 0L, DEFAULT_RESULTS_PERPAGE);
  }

  /**
   * Convenient factory method to create a {@link BasicPaginationCriteria} object.
   *
   * @param clazz The class of the listed object that will be paginated
   * @param orderBy
   * @param sortOrder
   * @param pageNumber
   * @param resultsPerPage
   * @return
   */
  public static <T> IPagination<T> createForClass(
      Class<T> clazz, String orderBy, String sortOrder, Long pageNumber, Integer resultsPerPage) {
    IPagination<T> rc = new BasicPaginationCriteria<>(clazz);
    rc.setOrderBy(orderBy);
    rc.setPageNumber(pageNumber);
    rc.setResultsPerPage(resultsPerPage);
    rc.setSortOrder(SortOrder.valueOf(sortOrder));
    return rc;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getClazz()
   */
  @Override
  public Class<T> getClazz() {
    return clazz;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setClazz(java.lang.Class)
   */
  @Override
  public IPagination<T> setClazz(Class<T> clazz) {
    this.clazz = clazz;
    setOrderBy(orderBy); // validates orderby field against class
    return this;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getSearchCriteria()
   */
  @Override
  public FilterCriteria getSearchCriteria() {
    return searchCriteria;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setSearchCriteria(com.axiope.util.
   * FilterCriteria)
   */
  @Override
  public void setSearchCriteria(FilterCriteria searchCriteria) {
    this.searchCriteria = searchCriteria;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#toURLQueryString(int)
   */
  @Override
  public String toURLQueryString(int pageNumber) {
    StringBuffer queryStrBuff = new StringBuffer();
    queryStrBuff
        .append("pageNumber=" + pageNumber)
        .append("&resultsPerPage=" + getResultsPerPage())
        .append("&sortOrder=" + getSortOrder())
        .append(!StringUtils.isBlank(getOrderBy()) ? "&orderBy=" + getOrderBy() : "");
    return queryStrBuff.toString();
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getPageNumber()
   */
  @Override
  public Long getPageNumber() {
    return pageNumber;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setPageNumber(java.lang.Long)
   */
  @Override
  public void setPageNumber(Long pageNumber) {
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Page number cannot be -ve, was [" + pageNumber + "]");
    }
    this.pageNumber = pageNumber;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getResultsPerPage()
   */
  @Override
  public Integer getResultsPerPage() {
    return resultsPerPage;
  }

  /**
   * Gets the default number of results to be displayed per page
   *
   * @return
   */
  public static int getDefaultResultsPerPage() {
    return DEFAULT_RESULTS_PERPAGE;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setResultsPerPage(java.lang.Integer)
   */
  @Override
  public void setResultsPerPage(Integer resultsPerPage) {
    if (resultsPerPage != null && resultsPerPage < 0) {
      throw new IllegalArgumentException(
          "Results per page cannot be -ve, was [" + resultsPerPage + "]");
    }
    this.resultsPerPage = resultsPerPage;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setGetAllResults()
   */
  @Override
  public IPagination<T> setGetAllResults() {
    setResultsPerPage(Integer.MAX_VALUE);
    return this;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getOrderBy()
   */
  @Override
  public String getOrderBy() {
    return orderBy;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setOrderBy(java.lang.String)
   */
  @Override
  public void setOrderBy(String orderByField) {
    if (StringUtils.isBlank(orderByField)) {
      return;
    }
    this.orderBy = orderByField;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getSortOrder()
   */
  @Override
  public SortOrder getSortOrder() {
    return sortOrder;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setSortOrder(com.axiope.util.SortOrder)
   */
  @Override
  public void setSortOrder(SortOrder sortOrder) {
    Validate.isTrue(sortOrder != null, "sort order cannot be null!");
    this.sortOrder = sortOrder;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#setOrderByIfNull(java.lang.String)
   */
  @Override
  public boolean setOrderByIfNull(String orderBy) {
    if (this.orderBy == null) {
      setOrderBy(orderBy);
      return true;
    }
    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.axiope.util.IPagination#getFirstResultIndex()
   */
  @Override
  public int getFirstResultIndex() {
    return (int) (getPageNumber() * getResultsPerPage());
  }
}

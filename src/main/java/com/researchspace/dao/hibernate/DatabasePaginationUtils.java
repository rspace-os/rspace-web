package com.researchspace.dao.hibernate;

import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import org.hibernate.Criteria;
import org.hibernate.criterion.Order;

public class DatabasePaginationUtils {

  /**
   * Convenience method to set pagination criteria into a Hibernate Criteria query. Does not set
   * 'orderBy' as this will be use-dependent depending on the properties to order by.
   *
   * @param pgCrit
   * @param query
   */
  public static void addPaginationCriteriaToHibernateCriteria(
      PaginationCriteria<?> pgCrit, Criteria query) {
    if (pgCrit == null) {
      return; // do nothing
    }
    query.setMaxResults(pgCrit.getResultsPerPage());
    query.setFirstResult(pgCrit.getFirstResultIndex());
    addOrderToHibernateCriteria(pgCrit, query);
  }

  /**
   * Convenience method to set order criteria into a Hibernate Criteria query.
   *
   * @param pgCrit
   * @param query
   */
  public static void addOrderToHibernateCriteria(PaginationCriteria<?> pgCrit, Criteria query) {
    if (pgCrit.getOrderBy() != null && pgCrit.isOrderBySafe(pgCrit.getOrderBy())) {
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        query.addOrder(Order.asc(pgCrit.getOrderBy()));
      } else {
        query.addOrder(Order.desc(pgCrit.getOrderBy()));
      }
    }
  }
}

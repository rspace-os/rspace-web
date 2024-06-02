package com.axiope.search;

import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import java.util.Collections;
import java.util.List;

public class SearchUtils {

  /** constant to use in orderBy field for ordering BaseRecords by creation date */
  public static final String BASE_RECORD_ORDER_BY_CREATED = "creationDateMillis";

  /** constant to use in orderBy field for ordering BaseRecords by modification date */
  public static final String BASE_RECORD_ORDER_BY_LAST_MODIFIED = "modificationDateMillis";

  /** constant to use in orderBy field for ordering BaseRecords/InventoryRecords by name */
  public static final String ORDER_BY_NAME = "name";

  /** constant to use in orderBy field for ordering InventoryRecords by type */
  public static final String ORDER_BY_TYPE = "type";

  /** constant to use in orderBy field for ordering InventoryRecords by globalId */
  public static final String ORDER_BY_GLOBAL_ID = "globalId";

  /** constant to use in orderBy field for ordering InventoryRecords by creationDate */
  public static final String ORDER_BY_CREATION_DATE = "creationDate";

  /** constant to use in orderBy field for ordering InventoryRecords by modificationDate */
  public static final String ORDER_BY_MODIFICATION_DATE = "modificationDate";

  private SearchUtils() {
    // private constructor to avoid instantiation of utility class
  }

  /** Utility method to sort a specific list using the pagination criteria values. */
  public static <T extends BaseRecord> List<T> sortList(
      List<T> list, PaginationCriteria<BaseRecord> paginationCriteria) {
    if (paginationCriteria != null) {
      String orderBy = paginationCriteria.getOrderBy();
      SortOrder sortOrder = paginationCriteria.getSortOrder();
      if (orderBy != null && sortOrder != null) {
        return sortByOrderByField(list, orderBy, sortOrder);
      }
    }
    return list;
  }

  private static <T extends BaseRecord> List<T> sortByOrderByField(
      List<T> list, String orderBy, SortOrder sortOrder) {
    // Compare BaseRecord.name
    if (orderBy.equalsIgnoreCase(ORDER_BY_NAME)) {
      list.sort(BaseRecord.NAME_COMPARATOR.thenComparing(BaseRecord.MODIFICATION_DATE_COMPARATOR));
      // Compare BaseRecord.modificationDate
    } else if (orderBy.equalsIgnoreCase(BASE_RECORD_ORDER_BY_LAST_MODIFIED)) {
      list.sort(BaseRecord.MODIFICATION_DATE_COMPARATOR);
      // Compare BaseRecord.creationDate
    } else if (orderBy.equalsIgnoreCase(BASE_RECORD_ORDER_BY_CREATED)) {
      list.sort(BaseRecord.CREATION_DATE_COMPARATOR);
    }

    if (sortOrder == SortOrder.DESC) {
      Collections.reverse(list);
    }
    return list;
  }

  public static <T extends InventoryRecord> List<T> sortInventoryList(
      List<T> list, PaginationCriteria<InventoryRecord> paginationCriteria) {

    if (paginationCriteria != null) {
      String orderBy = paginationCriteria.getOrderBy();
      SortOrder sortOrder = paginationCriteria.getSortOrder();
      if (orderBy != null && sortOrder != null) {
        if (orderBy.equalsIgnoreCase(ORDER_BY_NAME)) {
          list.sort(InventoryRecord.NAME_COMPARATOR);
        } else if (orderBy.equalsIgnoreCase(ORDER_BY_TYPE)) {
          list.sort(InventoryRecord.TYPE_COMPARATOR.thenComparing(InventoryRecord.NAME_COMPARATOR));
        } else if (orderBy.equalsIgnoreCase(ORDER_BY_GLOBAL_ID)) {
          list.sort(
              InventoryRecord.GLOBAL_ID_PREFIX_COMPARATOR.thenComparing(
                  InventoryRecord.ID_COMPARATOR));
        } else if (orderBy.equalsIgnoreCase(ORDER_BY_CREATION_DATE)) {
          list.sort(InventoryRecord.CREATION_DATE_COMPARATOR);
        } else if (orderBy.equalsIgnoreCase(ORDER_BY_MODIFICATION_DATE)) {
          list.sort(InventoryRecord.MODIFICATION_DATE_COMPARATOR);
        }
        if (sortOrder == SortOrder.DESC) {
          Collections.reverse(list);
        }
      }
    }
    return list;
  }

  public static <T> List<T> repaginateResults(List<T> list, int pageSize, int startPage) {
    int totalHits = list.size();
    int start = startPage * pageSize;
    if (start >= totalHits) {
      // supplied pagination criteria were wrong / out of bounds – reset and return first page
      start = 0;
    }
    int end = Math.min(totalHits, start + pageSize);

    return list.subList(start, end);
  }
}

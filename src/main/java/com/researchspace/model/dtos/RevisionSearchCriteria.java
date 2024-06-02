package com.researchspace.model.dtos;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;

public class RevisionSearchCriteria extends FilterCriteria {

  @UISearchTerm private String modifiedBy;

  @UISearchTerm private String dateRange;

  @UISearchTerm private String[] selectedFields;

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((dateRange == null) ? 0 : dateRange.hashCode());
    result = prime * result + ((modifiedBy == null) ? 0 : modifiedBy.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    RevisionSearchCriteria other = (RevisionSearchCriteria) obj;
    if (dateRange == null) {
      if (other.dateRange != null) {
        return false;
      }
    } else if (!dateRange.equals(other.dateRange)) {
      return false;
    }
    if (modifiedBy == null) {
      if (other.modifiedBy != null) {
        return false;
      }
    } else if (!modifiedBy.equals(other.modifiedBy)) {
      return false;
    }
    return true;
  }

  public String[] getSelectedFields() {
    return selectedFields;
  }

  public void setSelectedFields(String[] selectedFields) {
    this.selectedFields = selectedFields;
  }

  public String getDateRange() {
    return dateRange;
  }

  /**
   * A date range in syntax 'yy-mm-dd,yy-mm-dd' with either being optional
   *
   * @param dateRange
   */
  public void setDateRange(String dateRange) {
    this.dateRange = dateRange;
  }

  public RevisionSearchCriteria(String modifiedBy, String dateRange) {
    super();
    this.modifiedBy = modifiedBy;
    this.dateRange = dateRange;
  }

  public RevisionSearchCriteria() {}

  public String getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(String modifiedBy) {
    this.modifiedBy = modifiedBy;
  }
}

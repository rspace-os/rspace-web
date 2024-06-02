package com.researchspace.model.dtos;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;

public class SharedRecordSearchCriteria extends FilterCriteria {

  private static final long serialVersionUID = 1L;

  @UISearchTerm private String allFields;

  public String getAllFields() {
    return allFields;
  }

  public void setAllFields(String allFields) {
    this.allFields = allFields;
  }

  @Override
  public String toString() {
    return "SharedRecordSearchCriteria [allFields=" + allFields + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((allFields == null) ? 0 : allFields.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SharedRecordSearchCriteria other = (SharedRecordSearchCriteria) obj;
    if (allFields == null) {
      if (other.allFields != null) return false;
    } else if (!allFields.equals(other.allFields)) return false;
    return true;
  }
}

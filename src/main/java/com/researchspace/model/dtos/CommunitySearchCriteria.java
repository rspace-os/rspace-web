package com.researchspace.model.dtos;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;
import org.apache.commons.lang.StringUtils;

public class CommunitySearchCriteria extends FilterCriteria {

  /** */
  private static final long serialVersionUID = 2498792219722443926L;

  @UISearchTerm private String displayName;

  public String getDisplayName() {
    return displayName;
  }

  /**
   * Sets search term, truncates to
   *
   * @param displayName
   */
  public void setDisplayName(String displayName) {
    this.displayName = StringUtils.abbreviate(displayName, MAX_SEARCH_LENGTH);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    @SuppressWarnings("rawtypes")
    CommunitySearchCriteria other = (CommunitySearchCriteria) obj;
    if (displayName == null) {
      if (other.displayName != null) return false;
    } else if (!displayName.equals(other.displayName)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "CommunitySearchCriteria [displayName=" + displayName + "]";
  }
}

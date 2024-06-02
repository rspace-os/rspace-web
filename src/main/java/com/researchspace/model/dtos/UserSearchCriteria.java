package com.researchspace.model.dtos;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;

@Data
@EqualsAndHashCode(
    callSuper = false,
    of = {"allFields"})
public class UserSearchCriteria extends FilterCriteria {

  private static final long serialVersionUID = 1L;

  /**
   * The search term that will be searched across all user data( name, email, username)
   *
   * @return
   */
  @UISearchTerm private String allFields;

  /** The list of tags that results should be limited to */
  @UISearchTerm private String[] tags;

  @UISearchTerm private boolean onlyEnabled;

  private boolean onlyPublicProfiles;

  private boolean withoutBackdoorSysadmins;

  @UISearchTerm private boolean tempAccountsOnly;

  @UISearchTerm private LocalDate creationDateEarlierThan;

  @UISearchTerm private LocalDate lastLoginEarlierThan;

  /**
   * Sets search term from UI, truncated to MAX_SEARCH_LENGTH characters
   *
   * @param allFields
   */
  public void setAllFields(String allFields) {
    this.allFields = StringUtils.abbreviate(allFields, MAX_SEARCH_LENGTH);
  }

  @Override
  public String toString() {
    return "UserSearchCriteria [allFields=" + allFields + "]";
  }
}

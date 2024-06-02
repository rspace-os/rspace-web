package com.researchspace.admin.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;

/** Encapsualtes logic for Group listings/ orderings */
public interface SysadminGroupManager {

  /**
   * Gets group usage/info
   *
   * @param sysadmin
   * @param pgCrit
   * @return an {@link ISearchResults}
   */
  ISearchResults<GroupUsageInfo> getGroupUsageInfo(User sysadmin, PaginationCriteria<Group> pgCrit);
}

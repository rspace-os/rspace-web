package com.researchspace.admin.service.impl;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import org.apache.shiro.authz.AuthorizationException;

/** Common functions for subclasses */
public class AbstractSysadminMgr {

  protected void checkArgs(User sysadmin, PaginationCriteria<?> pgCrit) {
    // in current codebase pgCrit is never null at this point, so let's make it explicit
    if (pgCrit == null) {
      throw new IllegalArgumentException("criteria are null");
    }

    if (!sysadmin.hasAdminRole()) {
      throw new AuthorizationException("Only an admin can access this feature");
    }
  }
}

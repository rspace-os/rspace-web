package com.researchspace.service.impl;

import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import org.springframework.beans.factory.annotation.Autowired;

/** Shared beans and methods common to IUserSignupPolicy implementations */
public abstract class AbstractUserSignupPolicy {

  @Autowired protected UserManager userMgr;

  public void setUserManager(UserManager userMgr) {
    this.userMgr = userMgr;
  }

  @Autowired protected IPropertyHolder properties;

  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }
}

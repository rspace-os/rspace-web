package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.SysAdminCreateUser;

/** Handler for user account creation from Sysadmin form. */
public interface SysadminUserCreationHandler {

  AjaxReturnObject<User> createUser(SysAdminCreateUser postedCreateUserForm, User subject);
}

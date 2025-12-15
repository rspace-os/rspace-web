package com.researchspace.service;

import com.researchspace.model.Group;
import com.researchspace.model.User;

public interface SystemPropertyPermissionManager {

  /**
   * Returns whether this property is allowed to be used by this user. This method takes into
   * account system-wide settings and community settings.
   *
   * @param user user for which to check permissions (in case of null, just checks system wide
   *     permissions)
   * @param systemPropertyName
   * @return
   */
  boolean isPropertyAllowed(User user, String systemPropertyName);

  boolean isPropertyAllowed(User user, SystemPropertyName systemPropertyName);

  /**
   * Returns whether this property is allowed to be used by this lab group. This method takes into
   * account system-wide settings and community settings.
   *
   * @return
   */
  boolean isPropertyAllowed(Group group, String systemPropertyName);
}

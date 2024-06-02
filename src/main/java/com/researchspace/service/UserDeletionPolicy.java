package com.researchspace.service;

import java.util.Date;
import lombok.Data;

/** Configures how deletion of a user should work */
@Data
public class UserDeletionPolicy {

  public enum UserTypeRestriction {
    /** Restrict to temporary users only */
    TEMP_USER,

    /**
     * Will delete an initialized user but not one that has other associations - messages, group
     * membership etc
     */
    INIT_USER,

    /** Will force user delete and removal of associated information */
    NO_RESTRICTION
  }

  public UserDeletionPolicy(UserTypeRestriction restriction) {
    this.restriction = restriction;
  }

  private UserTypeRestriction restriction;

  /**
   * preference that if the user cannot be deleted, then the user account will be disabled.
   *
   * @param disableAccountIfCannotBeDeleted the disableAccountIfCannotBeDeleted to set
   */
  private boolean disableAccountIfCannotBeDeleted;

  /**
   * Only delete the user if all members of the group are inactive. Default setting is <code>false
   * </code>
   */
  private boolean strictPreserveDataForGroup = false;

  /**
   * If <code>strictPreserveDataForGroup</code> is <code>true</code>, a user will only be deleted if
   * the lastLogin date for all group members is earlier than this date. If <code>
   * strictPreserveDataForGroup</code> is <code>false</code>, this setting has no effect.
   */
  private Date lastLoginCutOffForGroup;

  /**
   * @return the forceDelete
   */
  public boolean isForceDelete() {
    return UserTypeRestriction.NO_RESTRICTION.equals(restriction);
  }

  /**
   * @return the forceDelete
   */
  public boolean isTempUserOnlyDelete() {
    return UserTypeRestriction.TEMP_USER.equals(restriction);
  }
}

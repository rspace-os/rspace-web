package com.researchspace.admin.service;

import com.researchspace.model.User;
import java.util.Date;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO object summarising various DB queries for return to System table display. Equality is based
 * on that of the userInfo object.
 */
@EqualsAndHashCode(of = {"userInfo"})
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserUsageInfo {

  @Getter @Setter private UserPublicInfoForUsageInfo userInfo;

  @Getter @Setter private Long fileUsage;

  @Getter @Setter private Long recordCount;

  private Date lastLogin;
  private Date creationDate;

  @Getter @Setter private String signupSource;

  /** Default constructor for frameworks */
  public UserUsageInfo() {}

  public UserUsageInfo(User user) {
    this.userInfo = new UserPublicInfoForUsageInfo(user);
  }

  public Date getCreationDate() {
    return creationDate != null ? new Date(creationDate.getTime()) : null;
  }

  public void setCreationDate(Date creationDate) {
    if (creationDate != null) {
      this.creationDate = new Date(creationDate.getTime());
    }
  }

  /**
   * Can be <code>null</code> if the user with the user name of this object's userInfo has never
   * logged in.
   *
   * @return
   */
  public Date getLastLogin() {
    return lastLogin != null ? new Date(lastLogin.getTime()) : null;
  }

  public void setLastLogin(Date date) {
    if (date != null) {
      this.lastLogin = new Date(date.getTime());
    }
  }
}

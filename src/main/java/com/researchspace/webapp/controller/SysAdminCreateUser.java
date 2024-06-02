package com.researchspace.webapp.controller;

import javax.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

/**
 * Data object to encapsulate request parameters from Sysadmin Create User form and perform basic
 * validation.
 */
@Data
public class SysAdminCreateUser {

  @NotBlank private String firstName;

  @NotBlank private String lastName;

  @NotBlank private String username;

  @NotBlank private String role;

  @NotBlank private String email;

  private boolean ldapAuthChoice;

  @Size(min = 1)
  private String affiliation;

  @Size(min = 1)
  private String passwordConfirmation;

  @Size(min = 1)
  private String password;

  private Long communityId;

  @Size(min = 1)
  private String newLabGroupName;

  private Long labGroupId;

  /** If creating a PI user, should a group be created? Default is <code>true</code> */
  private boolean createGroupForPiUser = true;

  /** Is the request to create backdoor sso user (signupSource.SSO_BACKDOOR) */
  private boolean ssoBackdoorAccount = false;

  private static final int defaultCommunity = -10;

  public boolean isCommunitySet() {
    return communityId != null && communityId != defaultCommunity;
  }
}

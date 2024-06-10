package com.researchspace.admin.service;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dto.UserPublicInfo;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserPublicInfoForUsageInfo {

  private Long id;
  private String username;
  private String usernameAlias;
  private String firstName;
  private String lastName;
  private String fullName;
  private String fullNameSurnameFirst;
  private String email;
  private Date previousLastLogin;
  private boolean accountLocked;
  private boolean enabled;
  private boolean temporary;
  private String role;
  private List<String> groupNames;
  private List<String> tags;

  public UserPublicInfoForUsageInfo(User user) {
    UserPublicInfo publicInfo = user.toPublicInfo();
    this.id = publicInfo.getId();
    this.username = publicInfo.getUsername();
    this.firstName = publicInfo.getFirstName();
    this.lastName = publicInfo.getLastName();
    this.fullName = publicInfo.getFullName();
    this.fullNameSurnameFirst = publicInfo.getFullNameSurnameFirst();
    this.email = publicInfo.getEmail();
    this.previousLastLogin = publicInfo.getPreviousLastLogin();
    this.accountLocked = publicInfo.isAccountLocked();
    this.enabled = publicInfo.isEnabled();
    this.temporary = publicInfo.isTemporary();
    this.role = publicInfo.getRole();

    this.usernameAlias = user.getUsernameAlias();
    this.tags = user.getTagsList();
    this.groupNames =
        user.getGroups().stream().map(Group::getDisplayName).collect(Collectors.toList());
  }
}

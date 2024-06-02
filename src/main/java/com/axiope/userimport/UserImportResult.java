package com.axiope.userimport;

import com.researchspace.model.User;
import com.researchspace.model.dto.CommunityPublicInfo;
import com.researchspace.model.dto.GroupPublicInfo;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.field.ErrorList;
import java.util.Collections;
import java.util.List;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.Validate;

/** Holds the result of parsing a batch of users and groups */
@NoArgsConstructor
public class UserImportResult {

  private List<UserRegistrationInfo> parsedUsers;
  private List<GroupPublicInfo> parsedGroups;
  private List<CommunityPublicInfo> parsedCommunities;

  private ErrorList errors;

  public UserImportResult(
      List<UserRegistrationInfo> parsedUsers,
      List<GroupPublicInfo> parsedGroups,
      List<CommunityPublicInfo> parsedCommunities,
      ErrorList errors) {
    Validate.notNull(parsedUsers, "parsed users can't be null");
    Validate.notNull(errors, "error list can't be null");

    this.parsedUsers = parsedUsers;
    this.errors = errors;
    if (parsedGroups != null) {
      this.parsedGroups = Collections.<GroupPublicInfo>unmodifiableList(parsedGroups);
    }
    if (parsedCommunities != null) {
      this.parsedCommunities = Collections.<CommunityPublicInfo>unmodifiableList(parsedCommunities);
    }
  }

  public List<UserRegistrationInfo> getParsedUsers() {
    return parsedUsers;
  }

  public List<GroupPublicInfo> getParsedGroups() {
    return parsedGroups;
  }

  public List<CommunityPublicInfo> getParsedCommunities() {
    return parsedCommunities;
  }

  public ErrorList getErrors() {
    return errors;
  }

  public boolean hasErrors() {
    return errors != null && errors.hasErrorMessages();
  }

  public User getUserFromUsername(String username) {
    for (UserRegistrationInfo u : parsedUsers) {
      if (u.getUsername().equals(username)) {
        return u.toUser();
      }
    }
    return null;
  }

  public User[] getUsersFromMemberString(List<String> members) {

    User[] allUsers = new User[members.size()];
    for (int i = 0; i < members.size(); i++) {
      User u = getUserFromUsername(members.get(i));
      allUsers[i] = u;
    }
    return allUsers;
  }
}

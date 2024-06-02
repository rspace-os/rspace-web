package com.researchspace.archive.model;

import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.UserProfile;

/** POJO to host test fixture data */
public class ArchiveUsersTestData {
  User user;
  User admin;
  Group group;
  Community community;
  UserProfile profile;
  UserPreference preferences;
  ArchiveUsers archiveInfo;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public User getAdmin() {
    return admin;
  }

  public void setAdmin(User admin) {
    this.admin = admin;
  }

  public Group getGroup() {
    return group;
  }

  public void setGroup(Group group) {
    this.group = group;
  }

  public Community getCommunity() {
    return community;
  }

  public void setCommunity(Community community) {
    this.community = community;
  }

  public UserProfile getProfile() {
    return profile;
  }

  public void setProfile(UserProfile profile) {
    this.profile = profile;
  }

  public UserPreference getPreferences() {
    return preferences;
  }

  public void setPreferences(UserPreference preferences) {
    this.preferences = preferences;
  }

  public void setArchiveUsers(ArchiveUsers userGroupInfo) {
    this.archiveInfo = userGroupInfo;
  }

  public ArchiveUsers getArchiveInfo() {
    return archiveInfo;
  }
}

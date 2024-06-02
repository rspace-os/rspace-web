package com.researchspace.archive.model;

import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.UserPreference;
import com.researchspace.model.UserProfile;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Container for XML storage of user/ group information. After unmarshalling the XML representation
 * of this object, associations between objects will be made, but all these objects will be
 * transient ( wrt to the database).
 *
 * <p>Therefore, saving of these objects direct to the database may require additional code.
 */
@XmlRootElement()
// this order is important to avoid null data during unmarshalling of userGroups
@XmlType(
    propOrder = {
      "schemaVersion",
      "users",
      "groups",
      "userGroups",
      "communities",
      "userPreferences",
      "profiles"
    })
public class ArchiveUsers {

  public ArchiveUsers() {
    super();
  }

  private Integer schemaVersion = 1;

  /**
   * The version of the XML schema for this object. This is to avoid needing to change the namespace
   * when schema versions change.
   *
   * @return
   */
  @XmlElement(required = true)
  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(Integer schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  private Set<Group> groups = new TreeSet<Group>();

  /** Defines a list of group properties */
  @XmlElementWrapper(name = "listOfGroups")
  @XmlElement(name = "group", required = true)
  public Set<Group> getGroups() {
    return groups;
  }

  public void setGroups(Set<Group> groups) {
    this.groups = groups;
  }

  /** Defines a list of users */
  private Set<User> users = new TreeSet<User>();

  @XmlElementWrapper(name = "listOfUsers")
  @XmlElement(name = "user", required = true)
  public Set<User> getUsers() {
    return users;
  }

  public void setUsers(Set<User> users) {
    this.users = users;
  }

  /** Defines a set of userGroups */
  private Set<UserGroup> userGroups = new HashSet<UserGroup>();

  @XmlElementWrapper(name = "listOfUserGroups")
  @XmlElement(name = "userGroup", required = true)
  public Set<UserGroup> getUserGroups() {
    return userGroups;
  }

  public void setUserGroups(Set<UserGroup> groups) {
    this.userGroups = groups;
  }

  /** Defines a set of userGroups */
  private Set<UserPreference> userPrefs = new HashSet<UserPreference>();

  @XmlElementWrapper(name = "listOfUserPreferences")
  @XmlElement(name = "userPreference", required = true)
  public Set<UserPreference> getUserPreferences() {
    return userPrefs;
  }

  public void setUserPreferences(Set<UserPreference> prefs) {
    this.userPrefs = prefs;
  }

  /** Defines a set of userGroups */
  private Set<Community> communities = new HashSet<Community>();

  @XmlElementWrapper(name = "listOfCommunities")
  @XmlElement(name = "community", required = true)
  public Set<Community> getCommunities() {
    return communities;
  }

  public void setCommunities(Set<Community> communities) {
    this.communities = communities;
  }

  private Set<UserProfile> userProfiles = new TreeSet<UserProfile>();

  @XmlElementWrapper(name = "userProfiles")
  @XmlElement(name = "userProfile", required = true)
  public Set<UserProfile> getProfiles() {
    return userProfiles;
  }

  public void setProfiles(Set<UserProfile> userProfiles) {
    this.userProfiles = userProfiles;
  }

  /**
   * MAgically called by JAXB framework to re-establish bi-directional links between users and
   * groups
   *
   * @param user
   * @param parent
   */
  void afterUnmarshal(Unmarshaller u, Object parent) {
    for (UserGroup ug : userGroups) {
      ug.getGroup().addMember(ug.getUser(), ug.getRoleInGroup());
    }
    // no establish link back to user.
    for (UserPreference up : userPrefs) {
      up.getUser().setPreference(up);
    }
  }
}

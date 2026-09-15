package com.researchspace.model;

import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.record.Folder;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Set;
import org.apache.shiro.authz.Permission;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An entity class that acts as a bridging entity between User and Group, adding properties that are
 * specific to a User-Group relationship.
 *
 * <p>All properties should be accessed from public methods in User or Group, which set the
 * relations properly. This class is an entity solely to make Hibernate mapping simpler.
 */
@Entity
@XmlType
@XmlAccessorType(XmlAccessType.NONE)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class UserGroup implements Permissable, Serializable {

  private static final long serialVersionUID = -2651417819901275622L;

  private static final Logger log = LoggerFactory.getLogger(UserGroup.class);

  private User user;
  private Group group;
  private Long id;
  private RoleInGroup roleInGroup = RoleInGroup.DEFAULT;
  private boolean includePermissions = true;
  private boolean adminViewDocsEnabled;
  private boolean piCanEditWork;
  private boolean autoshareEnabled;
  private Folder autoShareFolder;

  private final PermissionHandler permHandler;

  /**
   * @param user
   * @param group
   * @param roleInGroup whether this user belongs directly to this group, or whether it is
   *     transitive
   */
  public UserGroup(User user, Group group, RoleInGroup roleInGroup) {
    this();
    this.user = user;
    this.group = group;
    this.roleInGroup = roleInGroup;
  }

  /*
   * For hibernate
   */
  public UserGroup() {
    permHandler = new PermissionHandler();
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  @XmlElement
  @XmlIDREF
  public User getUser() {
    return user;
  }

  void setUser(User user) {
    this.user = user;
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id")
  @XmlElement
  @XmlIDREF
  public Group getGroup() {
    return group;
  }

  void setGroup(Group group) {
    this.group = group;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "user_group_gen")
  @TableGenerator(
      name = "user_group_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  // or get an exception if using auto
  public Long getId() {
    return id;
  }

  void setId(Long id) {
    this.id = id;
  }

  @XmlElement
  public RoleInGroup getRoleInGroup() {
    return roleInGroup;
  }

  /*
   * hibernate only
   */
  void setRoleInGroup(RoleInGroup roleInGroup) {
    this.roleInGroup = roleInGroup;
  }

  public void setRoleInGroup(RoleInGroup roleInGroup, Set<ConstraintBasedPermission> rolePerms) {
    this.roleInGroup = roleInGroup;
    // remove existing permissions
    permHandler.clearAll();
    for (ConstraintBasedPermission cbp : rolePerms) {
      addPermission(cbp);
    }
  }

  boolean isIncludePermissions() {
    return includePermissions;
  }

  /** Whether to include this group in a user's set of permissions */
  void setIncludePermissions(boolean includePermissions) {
    this.includePermissions = includePermissions;
  }

  /**
   * Boolean value that is only relevant for members with {@link RoleInGroup#RS_LAB_ADMIN} role.
   * <br>
   * If this is true then lab admins will be able to view all docs created by group members except
   * the PIs documents.
   *
   * @return <code>true</code> if is enabled, false otherwise
   */
  public boolean isAdminViewDocsEnabled() {
    return adminViewDocsEnabled;
  }

  /** Sets whether an admin should be able to see group member's work. */
  public void setAdminViewDocsEnabled(boolean adminViewDocsEnabled) {
    this.adminViewDocsEnabled = adminViewDocsEnabled;
  }

  /**
   * Boolean value that is only relevant for members with {@link RoleInGroup#PI} role. <br>
   * If this is true, then this PI user will be able to edit group member's work instead of only
   * read.
   *
   * @return <code>true</code> if is enabled, false otherwise
   */
  public boolean isPiCanEditWork() {
    return piCanEditWork;
  }

  /** Sets whether a PI can edit group member's work instead of just read. */
  public void setPiCanEditWork(boolean piCanEditWork) {
    this.piCanEditWork = piCanEditWork;
  }

  /** Boolean flag for whether user has enabled autosharing */
  public boolean isAutoshareEnabled() {
    return autoshareEnabled;
  }

  public void setAutoshareEnabled(boolean autoshareEnabled) {
    this.autoshareEnabled = autoshareEnabled;
  }

  /**
   * A User in a group, if auto-share is enabled, will specify a folder that will be added to the
   * group shared folder. This folder is lazyloaded
   *
   * @return A Folder, or <code>null</code> if not set set (isAutoshareEnabled() == false).
   */
  @OneToOne(fetch = FetchType.LAZY)
  public Folder getAutoShareFolder() {
    return autoShareFolder;
  }

  public void setAutoShareFolder(Folder autoShareFolder) {
    this.autoShareFolder = autoShareFolder;
  }

  @Convert(converter = PermissionStringConverter.class)
  @Column(columnDefinition = "MEDIUMTEXT")
  Set<String> getPermissionStrings() {
    return permHandler.getPermissionStrings();
  }

  @Transient
  public Set<Permission> getPermissions() {
    return permHandler.getPermissions();
  }

  void setPermissionStrings(Set<String> permissionStrings) {
    log.trace("Calling with " + permissionStrings);
    if (permissionStrings != null) {
      for (String s : permissionStrings) {
        log.trace("permission string is " + s);
      }
    }
    log.trace("perm handler is " + permHandler);
    permHandler.setPermissionStrings(permissionStrings);
  }

  /**
   * Public API to add a {@link ConstraintBasedPermission} to this user-group.
   *
   * @param p A {@link Permission} object
   */
  public void addPermission(ConstraintBasedPermission p) {
    permHandler.addPermission(p);
  }

  /**
   * Public API to remove a {@link Permission} from this group.
   *
   * @param p A {@link Permission} object
   */
  public void removePermission(Permission p) {
    permHandler.removePermission(p);
  }

  @Transient
  @XmlID
  @XmlAttribute(name = "id")
  public String getXMLId() {
    return getGroup().getUniqueName() + "-" + getUser().getUsername();
  }

  @Transient
  public boolean isPIRole() {
    return RoleInGroup.PI.equals(roleInGroup);
  }

  @Transient
  public boolean isAdminRole() {
    return RoleInGroup.RS_LAB_ADMIN.equals(roleInGroup);
  }

  @Transient
  public boolean isGroupOwnerRole() {
    return RoleInGroup.GROUP_OWNER.equals(roleInGroup);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((group == null) ? 0 : group.hashCode());
    result = prime * result + ((user == null) ? 0 : user.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    UserGroup other = (UserGroup) obj;
    if (group == null) {
      if (other.group != null) {
        return false;
      }
    } else if (!group.equals(other.group)) {
      return false;
    }
    if (user == null) {
      if (other.user != null) {
        return false;
      }
    } else if (!user.equals(other.user)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UserGroup [user="
        + user
        + ", group="
        + group
        + ", id="
        + id
        + ", includePermissions="
        + includePermissions
        + ", roleInGroup="
        + roleInGroup
        + "]";
  }
}

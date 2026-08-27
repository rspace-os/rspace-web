package com.researchspace.model;

import com.researchspace.model.permissions.ConstraintBasedPermission;
import java.util.Set;
import org.apache.shiro.authz.Permission;

/** Defines operations for entities that use permissions. */
public interface Permissable {

  Set<Permission> getPermissions();

  /**
   * Public API to add a {@link Permission} to this group.
   *
   * @param p A {@link Permission} object
   */
  void addPermission(ConstraintBasedPermission p);

  /**
   * Public API to remove a {@link Permission} to htis group.
   *
   * @param p A {@link Permission} object
   */
  void removePermission(Permission p);
}

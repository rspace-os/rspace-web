package com.researchspace.model;

import java.util.Set;

import org.apache.shiro.authz.Permission;

import com.researchspace.model.permissions.ConstraintBasedPermission;

/**
 * Defines operations for entities that use permissions.
 *
 */
public interface Permissable {

	public Set<Permission> getPermissions();

	/**
	 * Public API to add a {@link Permission} to this group.
	 * 
	 * @param p
	 *            A {@link Permission} object
	 */
	public void addPermission(ConstraintBasedPermission p);

	/**
	 * Public API to remove a {@link Permission} to htis group.
	 * 
	 * @param p
	 *            A {@link Permission} object
	 */
	public void removePermission(Permission p);

}

package com.researchspace.model.record;

import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;

/**
 * Marker interface for an entity class that can adapt to a Shiro Permission
 * class.
 */
public interface PermissionsAdaptable {

	/**
	 * Adapts an Entity class to permissions object that can be used in
	 * Permissions evaluations
	 * 
	 * @return
	 */
	AbstractEntityPermissionAdapter getPermissionsAdapter();

}

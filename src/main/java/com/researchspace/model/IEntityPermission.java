package com.researchspace.model;

import java.util.Set;

import org.apache.shiro.authz.Permission;

import com.researchspace.model.permissions.CommunityConstraint;
import com.researchspace.model.permissions.GroupConstraint;
import com.researchspace.model.permissions.LocationConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.PropertyConstraint;

/**
 * Adapts an RS entity object to the Shiro Permissions interface.
 */
public interface IEntityPermission extends Permission {

	Long getId();

	PermissionType getAction();

	void setAction(PermissionType action);

	PermissionDomain getDomain();

	void setDomain(PermissionDomain domain);

	LocationConstraint getLocationConstraint();

	PropertyConstraint getPropertyConstraintForProperty(String proeprtyName);

	Set<GroupConstraint> getGroupConstraints();

	Set<CommunityConstraint> getCommunityConstraints();

	boolean hasProperty(String propertyName);

}

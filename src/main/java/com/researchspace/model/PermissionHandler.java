package com.researchspace.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.authz.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;

/*
 * Provides permission handling functionality, delegated from entity classes
 */
class PermissionHandler implements Serializable {

	private static final long serialVersionUID = 7130258407692100276L;

	private static final Logger log = LoggerFactory.getLogger(PermissionHandler.class);
	private static final ConstraintPermissionResolver permissionResolver = new ConstraintPermissionResolver();
	private Set<String> permissionStrings = new HashSet<>();
	private Set<Permission> permissions = new HashSet<>();

	Set<String> getPermissionStrings() {
		return permissionStrings;
	}

	public Set<Permission> getPermissions() {
		return Collections.unmodifiableSet(permissions);
	}

	void setPermissionStrings(Set<String> permissionStrings) {
		this.permissionStrings = permissionStrings;
		if (permissionStrings == null) {
			log.warn("set was null!");
			return;
		}
		List<Permission> tempPermissions = new ArrayList<>();
		for (String permStr : permissionStrings) {
			// RSPAC-2706, known issue, parsing will just fail with error-level exception, so let's log on info and move on 
			if ("RECORD:READ:property_owner=".equals(permStr)) {
				log.info("Found problematic 'RECORD:READ:property_owner=' permission string, skipping");
				continue;  
			}
			try {
				tempPermissions.add(ConstraintPermissionResolver.populatePermssion(new ConstraintBasedPermission(), permStr));
			} catch (Exception e) {
				log.error("Problem adding permission '{}': {}", permStr, e.getMessage());
			}
		}
		// this will flatten many individual sb reads into a single 1.
		permissionResolver.flattenRecordReadWritePermissions(tempPermissions);
		permissions = new HashSet<>(tempPermissions);
	}

	/**
	 * Public API to add a {@link Permission} to this handler.
	 * 
	 * @param p A {@link Permission} object
	 */
	public void addPermission(ConstraintBasedPermission p) {
		boolean added = permissions.add(p);
		if (added) {
			permissionStrings.add(((ConstraintBasedPermission) p).getString());
		}
	}

	/**
	 * Public API to remove a {@link Permission} to this handler.
	 * 
	 * @param p A {@link Permission} object
	 */
	public void removePermission(Permission p) {
		permissions.remove(p);
		if (p instanceof ConstraintBasedPermission) {
			permissionStrings.remove(((ConstraintBasedPermission) p).getString());
		}
	}

	public void clearAll() {
		permissions.clear();
		permissionStrings.clear();
	}

}

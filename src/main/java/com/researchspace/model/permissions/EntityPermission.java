package com.researchspace.model.permissions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.shiro.authz.Permission;

import com.researchspace.model.IEntityPermission;

/**
 * Represents a set of permission information for a Resource, e.g., a document.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class EntityPermission implements IEntityPermission {

	@Getter
	@Setter
	Long id;

	@Getter
	@Setter
	private PermissionDomain domain;

	@Getter
	@Setter
	private PermissionType action;

	@Getter
	@Setter
	private Set<CommunityConstraint> communityConstraints = new HashSet<>();

	@Getter
	@Setter
	private LocationConstraint locationConstraint;

	@Setter
	Map<String, PropertyConstraint> propertyConstraints = new HashMap<>();

	@Getter
	@Setter
	private Set<GroupConstraint> groupConstraints = new HashSet<>();

	public EntityPermission(PermissionDomain domain, PermissionType action) {
		this.domain = domain;
		this.action = action;
	}

	public void addPropertyConstraint(PropertyConstraint dc) {
		propertyConstraints.put(dc.getName(), dc);
	}

	@Override
	public boolean hasProperty(String propertyName) {
		return propertyConstraints.get(propertyName) != null;
	}

	@Override
	public PropertyConstraint getPropertyConstraintForProperty(String propertyName) {
		return propertyConstraints.get(propertyName);
	}

	@Override
	public boolean implies(Permission permission) {
		throw new UnsupportedOperationException();
	}

}

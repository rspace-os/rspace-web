package com.researchspace.model.permissions;

import com.researchspace.model.IEntityPermission;
import com.researchspace.model.User;
import com.researchspace.model.utils.Utils;
import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import org.apache.shiro.authz.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Permission implementation in RS for representing a user's permissions.
 */
@Embeddable
public class ConstraintBasedPermission implements Serializable, Permission, Comparable<ConstraintBasedPermission> {

	static final Logger log = LoggerFactory.getLogger(ConstraintBasedPermission.class);
	
	
	private static final long serialVersionUID = -7860471174894242383L;
	private boolean enabled = true;

	private PermissionDomain domain;
	private EnumSet<PermissionType> actions = EnumSet.noneOf(PermissionType.class);

	private String permissionString;
	private User user;

	private CommunityConstraint communityConstraint;
	private GroupConstraint groupConstraint;
	private Set<LocationConstraint> locationConstraints = new HashSet<>();
	private IdConstraint idConstraint;

	public ConstraintBasedPermission(PermissionDomain domain, Set<PermissionType> actions) {
		this.domain = domain;
		if (actions != null) {
      this.actions.addAll(actions);
		}
	}

	public ConstraintBasedPermission(PermissionDomain domain, PermissionType... actions) {
		this.domain = domain;
    this.actions.addAll(Arrays.asList(actions));
	}
	
	public ConstraintBasedPermission(PermissionDomain domain, PermissionType action) {
		this.domain = domain;
		this.actions.add(action);
	}

	public ConstraintBasedPermission() {

	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public GroupConstraint getGroupConstraint() {
		return groupConstraint;
	}

	public void setGroupConstraint(GroupConstraint groupConstraint) {
		this.groupConstraint = groupConstraint;
	}

	@Transient
	public Map<String, PropertyConstraint> getPropertyConstraints() {
		return propertyConstraints;
	}

	@Transient
	public PermissionDomain getDomain() {
		return domain;
	}

	void setDomain(PermissionDomain domain) {
		this.domain = domain;
	}

	@Transient
	public EnumSet<PermissionType> getActions() {
		return actions;
	}

	void setActions(EnumSet<PermissionType> actions) {
		this.actions = actions;
	}

	@Transient
	Set<LocationConstraint> getLocationConstraints() {
		return locationConstraints;
	}

	public void addLocationConstraint(LocationConstraint lc) {
		locationConstraints.add(lc);
	}

	void setLocationConstraints(Set<LocationConstraint> locationConstraints) {
		this.locationConstraints = locationConstraints;
	}

	@Transient
	public IdConstraint getIdConstraint() {
		return idConstraint;
	}

	void setPropertyConstraints(Map<String, PropertyConstraint> propertyConstraints) {
		this.propertyConstraints = propertyConstraints;
		for (PropertyConstraint pc : propertyConstraints.values()) {
			pc.setOwner(this);
		}
	}

	public void setIdConstraint(IdConstraint idConstraint) {
		this.idConstraint = idConstraint;
	}

	private Map<String, PropertyConstraint> propertyConstraints = new HashMap<>();

	public void addPropertyConstraint(PropertyConstraint dc) {
		propertyConstraints.put(dc.getName(), dc);
	}

	public void addPermissionType(PermissionType pt) {
		actions.add(pt);
	}

	public boolean removePermissionType(PermissionType pt) {
		return actions.remove(pt);
	}

	/*
	 * sets user to null (so the permission is clean for next use) and returns
	 * false
	 */
	private boolean exitFalse() {
		setUser(null);
		return false;
	}

	@Override
	public boolean implies(Permission permission) {

		log.trace("Comparing this: {} with argument {}", permission.toString(), getString());
		//
		if (!isEnabled()) {
			return exitFalse();
		}
		if (!(permission instanceof IEntityPermission || permission instanceof ConstraintBasedPermission)) {
			return exitFalse();
		}
		IEntityPermission entityPermission;

		// is this code ever called ? Might be from JSP shiro tags?
		if (permission instanceof ConstraintBasedPermission) {
			entityPermission = ((ConstraintBasedPermission) permission).getAsEntityPermission();
		} else {
			entityPermission = (IEntityPermission) permission;
		}
		// return false if domain doesn't match
		if (!entityPermission.getDomain().equals(domain) && !domain.equals(PermissionDomain.ALL)) {
			return exitFalse();
		}

		if (!matchActions(entityPermission.getAction())) {
			return false;
		}
		// only check match properties if both this and test permission declare
		// property constraints
		for (Map.Entry<String, PropertyConstraint> prop : propertyConstraints.entrySet()) {
			if (entityPermission.hasProperty(prop.getKey())) {
				if (!prop.getValue()
						.satisfies(entityPermission.getPropertyConstraintForProperty(prop.getKey()))) {
					return exitFalse();
				}
			}
		}

		if (getGroupConstraint() != null && entityPermission.getGroupConstraints() != null) {
			boolean matchGroup = false;
			for (GroupConstraint other : entityPermission.getGroupConstraints()) {
				if (getGroupConstraint().satisfies(other)) {
					matchGroup = true;
					break;
				}
			}
			if (!matchGroup) {
				return exitFalse();
			}
		}

		if (getCommunityConstraint() != null && entityPermission.getCommunityConstraints() != null) {
			boolean matchCommunity = false;
			for (CommunityConstraint other : entityPermission.getCommunityConstraints()) {
				if (getCommunityConstraint().satisfies(other)) {
					matchCommunity = true;
					break;
				}
			}
			if (!matchCommunity) {
				return exitFalse();
			}
		}

		if (idConstraint != null && !idConstraint.satisfies(entityPermission.getId())) {
			return exitFalse();
		}
		if (!locationConstraints.isEmpty()) {
			boolean ok = false;
			for (LocationConstraint lc : locationConstraints) {
				// if no info, can't grant authorisation
				if (entityPermission.getLocationConstraint() == null) {
					break;
				}
				if (lc.satisfies(entityPermission.getLocationConstraint())) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				return exitFalse();
			}
		}
		setUser(null); // should be wiped after each
		return true;
	}

	boolean matchActions(PermissionType action) {
		if (!actions.contains(action)) {
			// write implies read permission
			if (PermissionType.READ.equals(action)) {
				if (!actions.contains(PermissionType.WRITE)) {
					return exitFalse();
				}
			} else {
				return exitFalse();
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return getString();
	}

	/**
	 * Gets the String representation that is persisted to DB.
	 * 
	 */
	public String getString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getDomain()).append(ConstraintPermissionResolver.PART_DELIMITER);

		for (PermissionType pt : getActions()) {
			sb.append(pt).append(ConstraintPermissionResolver.LIST_SEPARATOR);

		}
		Utils.replaceTRailingSeparator(sb, ConstraintPermissionResolver.LIST_SEPARATOR);
		sb.append(ConstraintPermissionResolver.PART_DELIMITER);

		boolean needsAnd = false;
		IdConstraint idc = getIdConstraint();
		if (idc != null && !idc.getId().isEmpty()) {
			needsAnd = true;
			sb.append(idc);

		}

		if (!propertyConstraints.isEmpty()) {
			if (needsAnd) {
				sb.append(ConstraintPermissionResolver.CONSTRAINT_SEPARATOR);
				needsAnd = false;
			}
			for (PropertyConstraint pc : propertyConstraints.values()) {
				if (needsAnd) {
					sb.append(ConstraintPermissionResolver.CONSTRAINT_SEPARATOR);
					needsAnd = false;
				}
				sb.append(pc.getString());
				needsAnd = true;
			}
		}
		if (groupConstraint != null) {
			if (needsAnd) {
				sb.append(ConstraintPermissionResolver.CONSTRAINT_SEPARATOR);
				needsAnd = false;
			}
			sb.append(groupConstraint.getString());
			needsAnd = true;
		}

		if (communityConstraint != null) {
			if (needsAnd) {
				sb.append(ConstraintPermissionResolver.CONSTRAINT_SEPARATOR);
				needsAnd = false;
			}
			sb.append(communityConstraint.getString());
			needsAnd = true;
		}

		if (!locationConstraints.isEmpty()) {
			if (needsAnd) {
				sb.append(ConstraintPermissionResolver.CONSTRAINT_SEPARATOR);
				needsAnd = false;
			}
			for (LocationConstraint lc : locationConstraints) {
				if (needsAnd) {
					sb.append(ConstraintPermissionResolver.CONSTRAINT_SEPARATOR);
					needsAnd = false;
				}
				sb.append(lc.getString());
				needsAnd = true;
			}
		}
		return sb.toString();
	}

	/**
	 * Converts a constraint-based permission to an entity permission. This is
	 * so that permissions can be parsed from strings into EntityPermissions.
	 *
   */
	@Transient
	public IEntityPermission getAsEntityPermission() {
		EntityPermission ep = new EntityPermission(getDomain(), null);
		if (!getActions().isEmpty()) {
			ep.setAction(getActions().iterator().next());
		}
		ep.setId(getIdConstraint() != null && !getIdConstraint().getId().isEmpty() ? getIdConstraint().getId().iterator().next() : null);
		for (PropertyConstraint pc : getPropertyConstraints().values()) {
			ep.addPropertyConstraint(pc);
		}
		Set<GroupConstraint> grpConstraints = new HashSet<>();
		if (getGroupConstraint() != null) {
			grpConstraints.add(getGroupConstraint());
			ep.setGroupConstraints(grpConstraints);
		}

		return ep;

	}

	/*
	 * For hibernate
	 */
	String getPermissionString() {
		return permissionString;
	}

	/*
	 * For hibernate
	 */
	void setPermissionString(String permission) {
		this.permissionString = permission;
	}

	public void setUser(User user) {
		this.user = user;
	}

	@Transient
	User getUser() {
		return user;
	}

	public void setCommunityConstraint(CommunityConstraint communityConstraint) {
		this.communityConstraint = communityConstraint;
	}

	public CommunityConstraint getCommunityConstraint() {
		return communityConstraint;
	}

	/**
	 * Default comparator orders by permission domain, then actions.
	 */
	@Override
	public int compareTo(ConstraintBasedPermission arg0) {
		if (arg0 == null) {
			return -1;
		}
		if (arg0.equals(this)) {
			return 0;
		}
		int rc = this.getDomain().compareTo(arg0.getDomain());
		if (rc == 0) {
			rc = this.getString().split(ConstraintPermissionResolver.PART_DELIMITER)[1]
					.compareTo(arg0.getString().split(ConstraintPermissionResolver.PART_DELIMITER)[1]);
		}
		return rc;
	}

	@Override
	public int hashCode() {
	//	hashcodeCount++;
		final int prime = 31;
		int result = 1;
		result = prime * result + ((permissionString == null) ? 0 : permissionString.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
	//	equalsCount++;
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ConstraintBasedPermission other = (ConstraintBasedPermission) obj;
    return getString().equals(other.getString());
  }

}

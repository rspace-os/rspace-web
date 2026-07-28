package com.researchspace.model.permissions;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;

/**
 * Represents a single access permission with an ACL This a value-object -
 * immutable once created so does not need copy method.
 * <p>
 * Equality is based on the permission string and the user or group's unique
 * name.
 */
public final class ACLElement implements Serializable {

	final static Pattern rigTypes = Pattern.compile("\\[.+\\]");

	/**
	 * 
	 */
	private static final long serialVersionUID = -5533389233858554610L;

	public final String getUserOrGrpUniqueName() {
		return userOrGrpUniqueName;
	}

	final String getPermString() {
		return permString;
	}

	@Override
	public String toString() {
		return "ACLElement [userOrGrpUniqueName=" + userOrGrpUniqueName + ", permString=" + permString + "]";
	}

	/**
	 * Gets a String representation of this object for persistence in format:
	 * <br/>
	 * subject=permission
	 * 
	 * @return
	 */
	public String getAsString() {
		return userOrGrpUniqueName + "=" + permString;
	}

	/**
	 * Generates a group permission restricted by RoleInGroup.
	 * <p>
	 * This will appear in the ACL string as :
	 * <p/>
	 * 'group[PI,DEFAULT_USER]=RECORD:CREATE', for example
	 * </p>
	 * and relies on the absence of '[' or ']' characters from the group unique
	 * name.
	 * 
	 * @param grp
	 * @param cbp
	 * @param roles
	 *            An optional list of 0, 1 or more Roles that this permission
	 *            applies to.
	 * @return A new {@link ACLElement}
	 */
	public static ACLElement createRoleRestrictedGroupACL(Group grp, ConstraintBasedPermission cbp,
			RoleInGroup... roles) {

		String name = grp == null ? "" : grp.getUniqueName();
		if (roles != null && roles.length > 0) {
			String rolesStr = StringUtils.join(roles, ",");
			name = name + "[" + rolesStr + "]";
		}
		ACLElement el = new ACLElement(name, cbp);
		return el;
	}

	/**
	 * Standard constructor to create an ACL element
	 * 
	 * @param userOrGrpUniqueName
	 * @param perm
	 */
	public ACLElement(String userOrGrpUniqueName, ConstraintBasedPermission perm) {
		if (StringUtils.isBlank(userOrGrpUniqueName) || perm == null) {
			throw new IllegalArgumentException(" invalid arguments :[" + userOrGrpUniqueName + "],[" + perm + "]");
		}
		this.userOrGrpUniqueName = userOrGrpUniqueName;
		this.permString = perm.getString();
	}

	private final String userOrGrpUniqueName;
	// a single permission
	private final String permString;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;

		result = prime * result + ((permString == null) ? 0 : permString.hashCode());
		result = prime * result + ((userOrGrpUniqueName == null) ? 0 : userOrGrpUniqueName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ACLElement other = (ACLElement) obj;

		if (permString == null) {
			if (other.permString != null)
				return false;
		} else if (!permString.equals(other.permString))
			return false;
		if (userOrGrpUniqueName == null) {
			if (other.userOrGrpUniqueName != null)
				return false;
		} else if (!userOrGrpUniqueName.equals(other.userOrGrpUniqueName))
			return false;
		return true;
	}

	Set<RoleInGroup> getRoles() {
		Set<RoleInGroup> rg = new HashSet<>(3);
		Matcher m = rigTypes.matcher(userOrGrpUniqueName);
		if (m.find()) {
			String roles = userOrGrpUniqueName.substring(m.start(), m.end());
			if (roles.contains(RoleInGroup.DEFAULT.name())) {
				rg.add(RoleInGroup.DEFAULT);
			}
			if (roles.contains(RoleInGroup.RS_LAB_ADMIN.name())) {
				rg.add(RoleInGroup.RS_LAB_ADMIN);
			}
			if (roles.contains(RoleInGroup.PI.name())) {
				rg.add(RoleInGroup.PI);
			}
		}
		return rg;
	}

}

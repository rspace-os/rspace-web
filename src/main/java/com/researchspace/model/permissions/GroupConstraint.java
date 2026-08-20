package com.researchspace.model.permissions;

import java.io.Serializable;

public class GroupConstraint implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
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
		GroupConstraint other = (GroupConstraint) obj;
		if (groupName == null) {
			if (other.groupName != null)
				return false;
		} else if (!groupName.equals(other.groupName))
			return false;
		return true;
	}

	private String groupName;

	public boolean satisfies(GroupConstraint other) {
		return groupName.equals(other.getGroupName());
	}

	public String getGroupName() {
		return groupName;
	}

	void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	/**
	 * 
	 * @param groupName
	 *            the unique name of the group ( not the display name)
	 */
	public GroupConstraint(String groupName) {
		super();
		this.groupName = groupName;
	}

	public Object getString() {
		return ConstraintPermissionResolver.GROUP_PARAM_PREFIX + "=" + groupName;
	}

}

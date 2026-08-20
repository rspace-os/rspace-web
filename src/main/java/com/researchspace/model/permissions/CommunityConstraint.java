package com.researchspace.model.permissions;

import java.io.Serializable;

public class CommunityConstraint implements Serializable {

	private Long communityId;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public CommunityConstraint(long id) {
		this.communityId = id;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((communityId == null) ? 0 : communityId.hashCode());
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
		CommunityConstraint other = (CommunityConstraint) obj;
		if (communityId == null) {
			if (other.communityId != null)
				return false;
		} else if (!communityId.equals(other.communityId))
			return false;
		return true;
	}

	public boolean satisfies(CommunityConstraint other) {
		return communityId.equals(other.getCommunityId());
	}

	public Long getCommunityId() {
		return communityId;
	}

	void setCommunityId(Long communityId) {
		this.communityId = communityId;
	}

	public Object getString() {
		return ConstraintPermissionResolver.COMMUNITY_PARAM_PREFIX + "=" + communityId;
	}

}

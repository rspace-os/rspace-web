package com.researchspace.model.permissions;

import java.util.HashSet;
import java.util.Set;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;

public class GroupPermissionsAdapter extends AbstractEntityPermissionAdapter {
	private Group grp;

	public GroupPermissionsAdapter(Group group) {
		this.grp = group;
		setDomain(PermissionDomain.GROUP);
	}

	@Override
	public Long getId() {
		return grp.getId();
	}

	@Override
	public Set<GroupConstraint> getGroupConstraints() {
		return TransformerUtils.toSet(new GroupConstraint(grp.getUniqueName()));
	}

	@Override
	protected  Object getEntity() {
		return grp;
	}

	public Set<CommunityConstraint> getCommunityConstraints() {
		Set<CommunityConstraint> communities = new HashSet<>();
		if (grp.getCommunityId() != null) {
			communities.add(new CommunityConstraint(grp.getCommunityId()));
		}
		return communities;
	}

	@Override
	protected PropertyConstraint handleSpecialProperties(String propertyName) {
		return null;
	}

}

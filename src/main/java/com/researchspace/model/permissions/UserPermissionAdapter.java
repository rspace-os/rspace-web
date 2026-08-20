package com.researchspace.model.permissions;

import java.util.HashSet;
import java.util.Set;

import com.researchspace.model.Group;
import com.researchspace.model.User;

public class UserPermissionAdapter extends AbstractEntityPermissionAdapter {

	private User subject;

	public UserPermissionAdapter(User subject) {
		super();
		this.subject = subject;
		setDomain(PermissionDomain.GROUP);
	}

	@Override
	public Long getId() {
		return subject.getId();
	}

	@Override
	public Set<GroupConstraint> getGroupConstraints() {
		Set<GroupConstraint> grps = new HashSet<>();
		for (Group ug : subject.getGroups()) {
			grps.add(new GroupConstraint(ug.getUniqueName()));
		}
		return grps;
	}

	@Override
	protected Object getEntity() {
		return subject;
	}

	protected @Override
	PropertyConstraint handleSpecialProperties(String propertyName) {
		//
		return null;
	}

}

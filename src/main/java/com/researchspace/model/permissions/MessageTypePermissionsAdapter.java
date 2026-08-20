package com.researchspace.model.permissions;


import java.util.Set;

import com.researchspace.model.comms.MessageType;

public class MessageTypePermissionsAdapter extends AbstractEntityPermissionAdapter {

	private MessageType messageType;

	public MessageTypePermissionsAdapter(MessageType messageType) {
		this.messageType = messageType;
		setDomain(PermissionDomain.COMMS);
	}

	@Override
	public Long getId() {
		return null;
	}

	public boolean hasProperty(String propertyName) {
		return "name".equals(propertyName);
	}

	@Override
	public Set<GroupConstraint> getGroupConstraints() {
		return null;
	}

	@Override
	protected Object getEntity() {
		return messageType;
	}

	@Override
	protected PropertyConstraint handleSpecialProperties(String propertyName) {
		if ("name".equals(propertyName)) {
			return new PropertyConstraint("name", messageType.name().replaceAll("_", ""));
		} else {
			return null;
		}
	}
}

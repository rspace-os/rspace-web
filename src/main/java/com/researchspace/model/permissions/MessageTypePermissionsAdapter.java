package com.researchspace.model.permissions;

import com.researchspace.model.comms.MessageType;
import java.util.Set;

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

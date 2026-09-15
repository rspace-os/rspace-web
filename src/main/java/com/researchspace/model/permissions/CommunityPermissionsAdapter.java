package com.researchspace.model.permissions;

import com.researchspace.model.Community;
import java.util.Set;

/** Adapts a {@link Community} object to the permissions system. */
public class CommunityPermissionsAdapter extends AbstractEntityPermissionAdapter {

  private Community community;

  @Override
  public Long getId() {
    return community.getId();
  }

  @Override
  public Set<GroupConstraint> getGroupConstraints() {
    return null;
  }

  @Override
  protected Object getEntity() {
    return community;
  }

  public CommunityPermissionsAdapter(Community community) {
    this.community = community;
    setDomain(PermissionDomain.COMMUNITY);
  }

  @Override
  protected PropertyConstraint handleSpecialProperties(String propertyName) {
    return null;
  }
}

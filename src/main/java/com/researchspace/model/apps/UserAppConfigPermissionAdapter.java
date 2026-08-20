package com.researchspace.model.apps;

import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.GroupConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PropertyConstraint;
import java.util.Set;

public class UserAppConfigPermissionAdapter extends AbstractEntityPermissionAdapter {

  private UserAppConfig appConfig;

  public UserAppConfigPermissionAdapter(UserAppConfig appConfig) {
    super();
    this.appConfig = appConfig;
    setDomain(PermissionDomain.APP);
  }

  @Override
  public Long getId() {
    return this.appConfig.getId();
  }

  @Override
  public Set<GroupConstraint> getGroupConstraints() {
    return null;
  }

  @Override
  protected Object getEntity() {
    return appConfig;
  }

  @Override
  protected PropertyConstraint handleSpecialProperties(String propertyName) {
    if ("user".equals(propertyName)) {
      return new PropertyConstraint("user", appConfig.getUser().getUsername());
    } else {
      return null;
    }
  }
}

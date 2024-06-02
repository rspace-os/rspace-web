package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.IdConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.PermissionsAdaptable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpSession;
import org.apache.shiro.authz.Permission;

public class PermissionsUtilsStub implements IPermissionUtils {

  @Override
  public void refreshCache() {}

  @Override
  public ConstraintBasedPermission findBy(
      Set<Permission> permissions, PermissionDomain domain, IdConstraint idConstraint) {
    return null;
  }

  @Override
  public void notifyUserOrGroupToRefreshCache(AbstractUserOrGroupImpl grp) {}

  @Override
  public boolean refreshCacheIfNotified() {
    return false;
  }

  @Override
  public PermissionType createFromString(String type) {
    return null;
  }

  @Override
  public <T extends PermissionsAdaptable> ISearchResults<T> filter(
      ISearchResults<T> toFilter, PermissionType permissionTypes, User authUser) {
    return toFilter;
  }

  @Override
  public <U extends Collection<T>, T extends PermissionsAdaptable> U filter(
      U toFilter, PermissionType permissionType, User authUser) {
    return toFilter;
  }

  @Override
  public <T extends PermissionsAdaptable> boolean isPermitted(
      T toTest, PermissionType permissionType, User authUser) {
    return false;
  }

  @Override
  public boolean isPermitted(String permission) {
    return false;
  }

  @Override
  public boolean isUserInRole(User user, Role... roles) {
    return false;
  }

  @Override
  public boolean isPermittedViaMediaLinksToRecords(
      IFieldLinkableElement fieldLinkableElement, PermissionType permType, User authUser) {
    return false;
  }

  @Override
  public void assertIsPermitted(
      PermissionsAdaptable toTest, PermissionType permType, User subject, String actionMsg) {}

  @Override
  public void assertIsPermitted(String permission, String unauthorisedMsg) {}

  @Override
  public void doRunAs(HttpSession session, User adminUser, User targetUser) {}

  @Override
  public boolean isRecordAccessPermitted(
      User user, BaseRecord recordToCheck, PermissionType permType) {
    return false;
  }

  @Override
  public void assertRecordAccessPermitted(
      BaseRecord toTest, PermissionType permType, User subject, String actionMsg) {}

  @Override
  public ConstraintBasedPermission findBy(
      Set<Permission> permissions,
      PermissionDomain domain,
      IdConstraint idConstraint,
      List<PermissionType> orderedActions) {
    return null;
  }
}

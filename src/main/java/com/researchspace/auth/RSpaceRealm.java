package com.researchspace.auth;

import static org.apache.commons.collections.CollectionUtils.isEmpty;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;

/** Base Class with a common method to get authorisation info */
@Slf4j
public class RSpaceRealm extends AuthorizingRealm {

  protected @Autowired UserManager userMgr;
  private @Autowired GroupManager groupMgr;

  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {

    Collection principalCollection = principals.fromRealm(getName());
    if (isEmpty(principalCollection)) {
      log.debug("Principal collection is null for realm {}", getName());
      return null;
    }
    String username = (String) principalCollection.iterator().next();
    log.info("Reloading authorisation info for {} from realm {}", username, getName());
    User user = userMgr.getUserByUsername(username, true);

    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
    for (Role role : user.getRoles()) {
      info.addRole(role.getName());
      info.addObjectPermissions(role.getPermissions());
    }
    log.debug(
        "Loading user {} has loaded {} usergroups: {}",
        username,
        user.getUserGroups().size(),
        StringUtils.join(
            user.getUserGroups().stream()
                .map(ug -> ug.getGroup().getUniqueName())
                .collect(Collectors.toList()),
            ","));
    info.addObjectPermissions(user.getAllPermissions(false, true));
    List<UserGroup> allUserGroups = groupMgr.findByUserId(user.getId());
    log.debug("ExplicitlyLoaded {} usergroups for {}", allUserGroups.size(), username);
    if (user.getUserGroups().size() != allUserGroups.size()) {
      log.warn(
          "Mismatch between eagerly laoded usergroups  ({})and explicit user groups ({})",
          user.getUserGroups().size(),
          allUserGroups.size());
      log.info("Adding in extra permissions");
      for (UserGroup ug : allUserGroups) {
        info.addObjectPermissions(ug.getPermissions());
        info.addObjectPermissions(ug.getGroup().getPermissions());
      }
    }

    log.debug(
        "Loaded {} permissions: {}",
        info.getObjectPermissions().size(),
        StringUtils.join(info.getObjectPermissions(), ","));
    return info;
  }

  public void clearCache(PrincipalCollection principals) {
    clearCachedAuthorizationInfo(principals);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    return null;
  }
}

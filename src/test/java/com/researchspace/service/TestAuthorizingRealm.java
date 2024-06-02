package com.researchspace.service;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.util.HashMap;
import java.util.Map;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

/** Dummy tests security realm that avoids using the database for authentication. */
public class TestAuthorizingRealm extends AuthorizingRealm {

  public TestAuthorizingRealm() {
    setName("TestRealm");
  }

  public void setUsers(Map<String, User> users) {
    this.users = users;
  }

  private Map<String, User> users = new HashMap<String, User>();

  private Map<String, SimpleAuthorizationInfo> user2info =
      new HashMap<String, SimpleAuthorizationInfo>();

  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    String username = (String) principals.fromRealm(getName()).iterator().next();
    if (user2info.get(username) == null) {
      User user = users.get(username);
      SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
      for (Role role : user.getRoles()) {
        info.addRole(role.getName());
        info.addObjectPermissions(role.getPermissions());
      }
      info.addObjectPermissions(user.getAllPermissions(false, true));
      user2info.put(username, info);
      return info;
    } else {
      return user2info.get(username);
    }
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    UsernamePasswordToken tok = (UsernamePasswordToken) token;
    return new SimpleAuthenticationInfo(
        tok.getUsername(), new String(tok.getPassword()), getName());
  }
}

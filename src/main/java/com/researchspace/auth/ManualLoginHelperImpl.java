package com.researchspace.auth;

import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.beans.factory.annotation.Autowired;

public class ManualLoginHelperImpl extends BaseLoginHelperImpl {

  private @Autowired IPropertyHolder properties;

  void doLogin(User toLogin, String originalPwd) {
    AuthenticationToken token;
    if (properties.isStandalone()) {
      token = new UsernamePasswordToken(toLogin.getUsername(), originalPwd);
    } else {
      token = new SSOAuthenticationToken(toLogin.getUsername());
    }
    login(token);
  }

  private void login(AuthenticationToken token) {
    SecurityUtils.getSubject().login(token);
  }
}

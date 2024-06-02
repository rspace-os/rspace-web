package com.researchspace.auth;

import com.researchspace.model.User;
import org.apache.shiro.SecurityUtils;

/**
 * Simple pass-through login which uses ExternalOAuthPassThruRealm to login since authentication
 * already performed by OAuth provider
 */
public class PostOAuthLoginHelperImpl extends BaseLoginHelperImpl {

  void doLogin(User toLogin, String originalPwd) {
    SecurityUtils.getSubject()
        .login(new EmailAuthenticationToken(toLogin.getEmail(), toLogin.getUsername()));
  }
}

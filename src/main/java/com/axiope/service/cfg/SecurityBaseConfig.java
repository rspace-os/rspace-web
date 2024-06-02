package com.axiope.service.cfg;

import com.researchspace.auth.AccountEnabledAuthorizer;
import com.researchspace.auth.AccountReviewRequiredAuthorizer;
import com.researchspace.auth.ApiRealm;
import com.researchspace.auth.ExternalAuthPassThruRealm;
import com.researchspace.auth.GlobalInitSysadminRealm;
import com.researchspace.auth.IPWhitelistLoginAuthorizer;
import com.researchspace.auth.LdapRealm;
import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.MaintenanceLoginAuthorizer;
import com.researchspace.auth.ManualLoginPermittedAuthorizer;
import com.researchspace.auth.OAuthRealm;
import com.researchspace.auth.SSOPassThruRealm;
import com.researchspace.auth.ShiroRealm;
import com.researchspace.auth.SlackRealm;
import com.researchspace.auth.wopi.WopiRealm;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.service.SessionControl;
import java.util.ArrayList;
import java.util.List;
import org.apache.shiro.authz.permission.PermissionResolver;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Base class for Shiro beans */
@Configuration
public abstract class SecurityBaseConfig {

  @Autowired protected ApplicationContext context;
  @Autowired protected DeploymentPropertyConfig deploymentPropertyConfig;

  @Bean
  public PermissionResolver permissionResolver() {
    return new ConstraintPermissionResolver();
  }

  @Bean(name = "myRealm")
  public SessionControl standardRealm() {
    ShiroRealm realm = new ShiroRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean
  public Realm externalOAuthRealm() {
    ExternalAuthPassThruRealm realm = new ExternalAuthPassThruRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean
  public OAuthRealm internalOAuthRealm() {
    OAuthRealm realm = new OAuthRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean
  public Realm apiRealm() {
    ApiRealm realm = new ApiRealm();
    realm.setPermissionResolver(permissionResolver());
    realm.setAuthenticationCacheName("API.authenticationCache");
    return realm;
  }

  @Bean
  public Realm ssoRealm() {
    SSOPassThruRealm realm = new SSOPassThruRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean
  public Realm slackRealm() {
    SlackRealm realm = new SlackRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean
  public Realm wopiRealm() {
    WopiRealm realm = new WopiRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean
  public Realm ldapRealm() {
    LdapRealm realm = new LdapRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  @Bean()
  public Realm globalInitSysadminRealm() {
    GlobalInitSysadminRealm realm = new GlobalInitSysadminRealm();
    realm.setPermissionResolver(permissionResolver());
    return realm;
  }

  public abstract SecurityManager securityManager();

  @Bean
  public List<LoginAuthorizer> loginAuthorizers() {
    List<LoginAuthorizer> authorizers = new ArrayList<>();
    authorizers.add(accountEnabledAuthorizer());
    authorizers.add(accountReviewRequiredAuthorizer());
    authorizers.add(ipWhitelistAuthorizer());
    // this should be last
    authorizers.add(manualLoginPermittedAuthorizer());
    return authorizers;
  }

  @Bean
  public LoginAuthorizer manualLoginPermittedAuthorizer() {
    return new ManualLoginPermittedAuthorizer();
  }

  @Bean
  public LoginAuthorizer ipWhitelistAuthorizer() {
    return new IPWhitelistLoginAuthorizer();
  }

  @Bean
  public LoginAuthorizer accountReviewRequiredAuthorizer() {
    return new AccountReviewRequiredAuthorizer();
  }

  @Bean
  public LoginAuthorizer accountEnabledAuthorizer() {
    return new AccountEnabledAuthorizer();
  }

  @Bean
  public MaintenanceLoginAuthorizer maintenanceLoginAuthorizer() {
    return new MaintenanceLoginAuthorizer();
  }
}

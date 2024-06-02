package com.axiope.service.cfg;

import static org.apache.commons.lang3.ArrayUtils.contains;

import com.researchspace.auth.FirstSuccessOrExceptionAuthStrategy;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Configures a web security manager for run/prod profiles */
@Configuration
@Profile({"run", "prod"})
public class SecurityRunProdConfig extends SecurityBaseConfig {

  @Bean
  public SecurityManager securityManager() {
    DefaultWebSecurityManager rc = new DefaultWebSecurityManager();
    Collection<Realm> realms = new ArrayList<>();

    if (deploymentPropertyConfig.isStandalone()
        || deploymentPropertyConfig.isSsoAdminLoginEnabled()) {
      realms.add(standardRealm());
    }
    if (!deploymentPropertyConfig.isStandalone()) {
      realms.add(ssoRealm());
    }
    if (deploymentPropertyConfig.isMsOfficeEnabled()
        || deploymentPropertyConfig.isCollaboraEnabled()) {
      realms.add(wopiRealm());
    }
    realms.add(apiRealm());
    realms.add(slackRealm());

    if (deploymentPropertyConfig.isCloud()) {
      realms.add(externalOAuthRealm());
    }
    if (deploymentPropertyConfig.isLdapAuthenticationEnabled()) {
      realms.add(ldapRealm());
    }
    rc.setRealms(realms);

    ModularRealmAuthenticator authenticator = (ModularRealmAuthenticator) rc.getAuthenticator();
    authenticator.setAuthenticationStrategy(new FirstSuccessOrExceptionAuthStrategy());

    /* configuring ehcache in java config is still problematic in test
     * environment, loading from XML config for now */
    // security-realm-config test since upgrading to Spring 5 has issues loading multiple instances
    // of the cache
    // since the cache is not needed in test environment( but in tests, we want to test this
    // production configuration)
    // we have this conditional
    if (!contains(context.getEnvironment().getActiveProfiles(), "securitytest")) {
      rc.setCacheManager(shiroEhCacheMgr());
    }

    return rc;
  }

  @Bean(name = "ehcacheManager")
  public EhCacheManager shiroEhCacheMgr() {
    EhCacheManager mgr = new EhCacheManager();
    mgr.setCacheManagerConfigFile("classpath:ehcache-shiro.xml");
    return mgr;
  }
}

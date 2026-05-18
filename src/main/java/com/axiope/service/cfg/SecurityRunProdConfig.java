package com.axiope.service.cfg;

import static org.apache.commons.lang3.ArrayUtils.contains;

import com.researchspace.auth.FirstSuccessOrExceptionAuthStrategy;
import com.researchspace.auth.JCacheShiroCacheManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
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
    realms.add(globalInitSysadminRealm());

    if (deploymentPropertyConfig.isCloud()) {
      realms.add(externalOAuthRealm());
    }
    if (deploymentPropertyConfig.isLdapAuthenticationEnabled()) {
      realms.add(ldapRealm());
    }
    rc.setRealms(realms);

    ModularRealmAuthenticator authenticator = (ModularRealmAuthenticator) rc.getAuthenticator();
    authenticator.setAuthenticationStrategy(new FirstSuccessOrExceptionAuthStrategy());

    // The security-realm-config tests load this production configuration; avoid extra cache
    // manager wiring during those tests.
    if (!contains(context.getEnvironment().getActiveProfiles(), "securitytest")) {
      rc.setCacheManager(shiroCacheManager());
    }

    return rc;
  }

  @Bean(name = "ehcacheManager")
  public JCacheShiroCacheManager shiroCacheManager() {
    CachingProvider provider = Caching.getCachingProvider();
    javax.cache.CacheManager jCacheManager =
        provider.getCacheManager(
            provider.getDefaultURI(), getClass().getClassLoader(), provider.getDefaultProperties());

    Map<String, Duration> ttls = new HashMap<>();
    ttls.put("com.researchspace.model.Role", null);
    ttls.put("com.researchspace.model.Role.permissions", null);
    ttls.put("com.researchspace.model.User", Duration.ofSeconds(3600));
    ttls.put("com.researchspace.model.User.roles", Duration.ofSeconds(3600));
    ttls.put("com.researchspace.model.UserApiKey", Duration.ofSeconds(3600));
    ttls.put("API.authenticationCache", Duration.ofSeconds(3600));

    return new JCacheShiroCacheManager(jCacheManager, ttls, Duration.ofSeconds(121));
  }
}

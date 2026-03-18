package com.researchspace.testutils;

import com.axiope.service.cfg.SecurityBaseConfig;
import com.researchspace.auth.ApiRealm;
import com.researchspace.auth.ApiRealmTestSpy;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Test configuration of Apache Shiro */
@Configuration
@Profile({"dev", "prod-test"})
public class SecurityTestConfig extends SecurityBaseConfig {

  /**
   * Sets up standard security manager with all realms set up for testing.
   *
   * @return
   */
  @Bean(name = "securityManagerTest")
  @Override
  public SecurityManager securityManager() {
    DefaultSecurityManager rc = new DefaultSecurityManager();
    Collection<Realm> realms = new ArrayList<>();
    realms.add(standardRealm());
    realms.add(ssoRealm());
    realms.add(apiRealm());
    realms.add(externalOAuthRealm());
    rc.setRealms(realms);
    rc.setCacheManager(shiroCacheManager());
    return rc;
  }

  /** Creates A test spy which reveals some of the internals of the realm. */
  @Override
  @Bean
  public Realm apiRealm() {
    ApiRealm realm = new ApiRealmTestSpy();
    realm.setPermissionResolver(permissionResolver());
    realm.setAuthenticationCacheName("API.authenticationCache");
    realm.setCacheManager(shiroCacheManager());
    return realm;
  }

  @Bean
  public LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
    return new LifecycleBeanPostProcessor();
  }

  @Bean
  public MemoryConstrainedCacheManager shiroCacheManager() {
    return new MemoryConstrainedCacheManager();
  }
}

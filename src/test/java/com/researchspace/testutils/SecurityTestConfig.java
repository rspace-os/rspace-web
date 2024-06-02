package com.researchspace.testutils;

import com.axiope.service.cfg.SecurityBaseConfig;
import com.researchspace.auth.ApiRealm;
import com.researchspace.auth.ApiRealmTestSpy;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

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
    // configuring ehcache in java config is still problematic in test environment, loading from XML
    // config for now.
    rc.setCacheManager(shiroEhCacheManager());
    return rc;
  }

  /** Creates A test spy which reveals some of the internals of the realm. */
  @Override
  @Bean
  public Realm apiRealm() {
    ApiRealm realm = new ApiRealmTestSpy();
    realm.setPermissionResolver(permissionResolver());
    realm.setAuthenticationCacheName("API.authenticationCache");
    realm.setCacheManager(shiroEhCacheManager());
    return realm;
  }

  @Bean
  public EhCacheManagerFactoryBean ehCacheManagerFactoryBean() {
    EhCacheManagerFactoryBean rc = new EhCacheManagerFactoryBean();
    rc.setShared(true);
    rc.setConfigLocation(new ClassPathResource("ehcache-shiro.xml"));
    return rc;
  }

  @Bean
  public LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
    return new LifecycleBeanPostProcessor();
  }

  @Bean
  public EhCacheManager shiroEhCacheManager() {
    EhCacheManager mgr = new EhCacheManager();
    mgr.setCacheManager(ehCacheManagerFactoryBean().getObject());
    return mgr;
  }
  //	 <bean id="ehcache-shiro"
  //		class="org.springframework.cache.ehcache.EhCacheManagerFactoryBean"
  //		p:shared="true" p:config-location="classpath:ehcache-spring.xml" />
  //   <!--  we're setting an actual reusable ehcache object here. If we just set in the ehcache
  // config file
  //       location  then Shiro will create a new cache manager each time a spring context is
  // loaded, and
  //        this is not allowed since ehcache 2.5,. -->
  //	<bean id="ehcacheManager" class="org.apache.shiro.cache.ehcache.EhCacheManager">
  //		<property name="cacheManager" ref="ehcache-shiro" />
  //	</bean>

}

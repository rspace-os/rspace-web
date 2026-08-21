package com.researchspace.webapp.filter;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * A {@link ShiroFilterFactoryBean} that resolves the securityManager bean on first use instead of
 * having it injected at construction.
 *
 * <p>Under Shiro 2, ShiroFilterFactoryBean was a BeanPostProcessor and initialized before the
 * context refresh completed. Direct securityManager injection would then initialize the realm
 * service graph before annotation-driven advice was available. See the AOP notes in
 * applicationContext-service.xml. Shiro 3 moved this processing to a separate
 * ShiroFilterFactoryBeanPostProcessor, which this application does not use. The companion advisor
 * still initializes early, so the filter remains lazy to prevent the early dependency graph from
 * returning.
 *
 * <p>The servlet container calls {@link #getSecurityManager()} from {@code createInstance()} after
 * the refresh. The method returns the real securityManager, which preserves behavior such as casts
 * to DefaultSecurityManager.
 */
public class LazySecurityManagerShiroFilterFactoryBean extends ShiroFilterFactoryBean
    implements BeanFactoryAware {

  private BeanFactory beanFactory;
  private String securityManagerBeanName = "securityManager";

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  public void setSecurityManagerBeanName(String securityManagerBeanName) {
    this.securityManagerBeanName = securityManagerBeanName;
  }

  @Override
  public SecurityManager getSecurityManager() {
    SecurityManager securityManager = super.getSecurityManager();
    if (securityManager == null) {
      securityManager = beanFactory.getBean(securityManagerBeanName, SecurityManager.class);
      setSecurityManager(securityManager);
    }
    return securityManager;
  }
}

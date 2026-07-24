package com.researchspace.webapp.filter;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * A {@link ShiroFilterFactoryBean} that resolves the securityManager bean on first use instead of
 * having it injected at construction.
 *
 * <p>ShiroFilterFactoryBean is a BeanPostProcessor, so it is created before context refresh
 * completes. A direct securityManager property reference would instantiate the realms' entire
 * service-bean dependency graph inside that early window, where annotation-driven advice
 * (@Transactional, Shiro's authorization annotations) is silently skipped; see the AOP notes in
 * applicationContext-service.xml. {@link #getSecurityManager()} is first called from {@code
 * createInstance()} when the servlet container initialises the filter, which is safely after
 * refresh, and hands the real securityManager instance to the filter so downstream code (e.g. casts
 * to DefaultSecurityManager) behaves exactly as with direct injection.
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

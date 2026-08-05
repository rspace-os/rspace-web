package com.researchspace.auth;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * An {@link AuthorizationAttributeSourceAdvisor} that resolves the securityManager bean on first
 * use instead of having it injected at construction.
 *
 * <p>Advisor beans are created during early advisor retrieval, before context refresh completes. A
 * direct securityManager property reference would instantiate the realms' entire service-bean
 * dependency graph inside that early window, where annotation-driven advice (@Transactional,
 * Shiro's authorization annotations) is silently skipped; see the AOP notes in
 * applicationContext-service.xml.
 */
public class LazySecurityManagerAuthorizationAttributeSourceAdvisor
    extends AuthorizationAttributeSourceAdvisor implements BeanFactoryAware {

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

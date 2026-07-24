package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.auth.LazySecurityManagerAuthorizationAttributeSourceAdvisor;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class LazySecurityManagerShiroFilterFactoryBeanTest {

  private DefaultListableBeanFactory beanFactory;
  private DefaultWebSecurityManager securityManager;

  @BeforeEach
  public void setUp() {
    beanFactory = new DefaultListableBeanFactory();
    securityManager = new DefaultWebSecurityManager();
    beanFactory.registerSingleton("securityManager", securityManager);
  }

  @Test
  public void filterFactoryBeanResolvesSecurityManagerFromBeanFactoryOnFirstUse() {
    LazySecurityManagerShiroFilterFactoryBean factoryBean =
        new LazySecurityManagerShiroFilterFactoryBean();
    factoryBean.setBeanFactory(beanFactory);

    assertSame(securityManager, factoryBean.getSecurityManager());
    assertSame(securityManager, factoryBean.getSecurityManager());
  }

  @Test
  public void advisorResolvesSecurityManagerFromBeanFactoryOnFirstUse() {
    LazySecurityManagerAuthorizationAttributeSourceAdvisor advisor =
        new LazySecurityManagerAuthorizationAttributeSourceAdvisor();
    advisor.setBeanFactory(beanFactory);

    assertSame(securityManager, advisor.getSecurityManager());
    assertSame(securityManager, advisor.getSecurityManager());
  }

  @Test
  public void constructionDoesNotResolveTheSecurityManager() {
    DefaultListableBeanFactory emptyFactory = new DefaultListableBeanFactory();

    LazySecurityManagerShiroFilterFactoryBean factoryBean =
        new LazySecurityManagerShiroFilterFactoryBean();
    factoryBean.setBeanFactory(emptyFactory);
    LazySecurityManagerAuthorizationAttributeSourceAdvisor advisor =
        new LazySecurityManagerAuthorizationAttributeSourceAdvisor();
    advisor.setBeanFactory(emptyFactory);

    // no bean lookup has happened yet: an empty factory only fails when the getter is called
    assertNull(emptyFactory.getSingleton("securityManager"));
  }
}

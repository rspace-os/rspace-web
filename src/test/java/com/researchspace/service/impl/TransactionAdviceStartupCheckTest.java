package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.SimpleTraceInterceptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

public class TransactionAdviceStartupCheckTest {

  @Transactional
  static class ClassAnnotatedService {
    public void doSomething() {}
  }

  static class MethodAnnotatedService {
    @Transactional
    public void doSomething() {}
  }

  @Transactional
  interface ClassAnnotatedApi {
    void doSomething();
  }

  static class ClassAnnotatedApiImpl implements ClassAnnotatedApi {
    @Override
    public void doSomething() {}
  }

  interface MethodAnnotatedApi {
    @Transactional
    void doSomething();
  }

  static class MethodAnnotatedApiImpl implements MethodAnnotatedApi {
    @Override
    public void doSomething() {}
  }

  static class PlainService {
    public void doSomething() {}
  }

  private DefaultListableBeanFactory beanFactory;
  private TransactionAdviceStartupCheck check;

  @BeforeEach
  public void setUp() {
    beanFactory = new DefaultListableBeanFactory();
    check = new TransactionAdviceStartupCheck();
  }

  private Object proxied(Object target, boolean withTransactionAdvice) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
        withTransactionAdvice ? new TransactionInterceptor() : new SimpleTraceInterceptor());
    return proxyFactory.getProxy();
  }

  @Test
  public void unproxiedAnnotatedBeanIsFlagged() {
    beanFactory.registerSingleton("classAnnotated", new ClassAnnotatedService());
    beanFactory.registerSingleton("methodAnnotated", new MethodAnnotatedService());

    List<String> offenders = check.findBeansMissingTransactionAdvice(beanFactory);

    assertEquals(2, offenders.size());
    assertTrue(offenders.stream().anyMatch(o -> o.startsWith("classAnnotated ")));
    assertTrue(offenders.stream().anyMatch(o -> o.startsWith("methodAnnotated ")));
  }

  @Test
  public void proxiedAnnotatedBeanWithoutTransactionAdviceIsFlagged() {
    beanFactory.registerSingleton("proxiedNoTx", proxied(new ClassAnnotatedService(), false));

    List<String> offenders = check.findBeansMissingTransactionAdvice(beanFactory);

    assertEquals(1, offenders.size());
    assertTrue(offenders.get(0).startsWith("proxiedNoTx "));
  }

  @Test
  public void annotationDeclaredOnlyOnInterfaceIsStillDetected() {
    beanFactory.registerSingleton("interfaceClassAnnotated", new ClassAnnotatedApiImpl());
    beanFactory.registerSingleton("interfaceMethodAnnotated", new MethodAnnotatedApiImpl());

    List<String> offenders = check.findBeansMissingTransactionAdvice(beanFactory);

    assertEquals(2, offenders.size());
    assertTrue(offenders.stream().anyMatch(o -> o.startsWith("interfaceClassAnnotated ")));
    assertTrue(offenders.stream().anyMatch(o -> o.startsWith("interfaceMethodAnnotated ")));
  }

  @Test
  public void annotatedBeanWithTransactionAdvicePasses() {
    beanFactory.registerSingleton("proxiedWithTx", proxied(new ClassAnnotatedService(), true));
    beanFactory.registerSingleton(
        "methodAnnotatedWithTx", proxied(new MethodAnnotatedService(), true));

    assertTrue(check.findBeansMissingTransactionAdvice(beanFactory).isEmpty());
  }

  @Test
  public void unannotatedBeansAreIgnored() {
    beanFactory.registerSingleton("plain", new PlainService());
    beanFactory.registerSingleton("plainProxied", proxied(new PlainService(), false));

    assertTrue(check.findBeansMissingTransactionAdvice(beanFactory).isEmpty());
  }

  @Test
  public void beansOutsideApplicationPackagesAreIgnored() {
    beanFactory.registerSingleton("someInfrastructureBean", new StringBuilder());

    assertTrue(check.findBeansMissingTransactionAdvice(beanFactory).isEmpty());
  }
}

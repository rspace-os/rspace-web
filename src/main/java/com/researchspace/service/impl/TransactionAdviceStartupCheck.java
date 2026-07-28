package com.researchspace.service.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Guards against beans that declare transactional behaviour with {@code @Transactional} but were
 * proxied without transaction advice.
 *
 * <p>Annotation-driven advice is unreliable for any bean instantiated before context refresh
 * completes (e.g. pulled in transitively by a BeanPostProcessor such as Shiro's
 * ShiroFilterFactoryBean): while the {@code <tx:annotation-driven>} advisor is itself mid-creation,
 * Spring's advisor retrieval skips it, so such beans are permanently proxied without transaction
 * advice and fail only at runtime ("Could not obtain transaction-synchronized Session"). See the
 * AOP notes in applicationContext-service.xml.
 *
 * <p>On refresh this listener first initialises the real {@code securityManager} (the Shiro beans
 * in security.xml deliberately resolve it lazily so its large service-bean dependency graph is
 * created only now, when all advisors are available), then inspects every already-instantiated
 * singleton and fails startup if any {@code @Transactional} bean lacks a {@link
 * TransactionInterceptor} in its advice chain. In contexts without the real securityManager
 * (tests), offenders are logged as errors instead of failing, since only the running application
 * wires security.xml.
 */
@Component
public class TransactionAdviceStartupCheck
    implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

  private static final Logger log = LoggerFactory.getLogger(TransactionAdviceStartupCheck.class);

  private static final String SECURITY_MANAGER_BEAN_NAME = "securityManager";

  private static final List<Class<? extends Annotation>> TRANSACTIONAL_ANNOTATIONS =
      List.of(Transactional.class, jakarta.transaction.Transactional.class);

  private ApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.context = applicationContext;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    if (event.getApplicationContext() != context) {
      return;
    }
    boolean realSecurityManagerPresent = context.containsLocalBean(SECURITY_MANAGER_BEAN_NAME);
    if (realSecurityManagerPresent) {
      context.getBean(SECURITY_MANAGER_BEAN_NAME);
    }
    ConfigurableListableBeanFactory beanFactory =
        ((ConfigurableApplicationContext) context).getBeanFactory();
    List<String> offenders = findBeansMissingTransactionAdvice(beanFactory);
    if (offenders.isEmpty()) {
      log.info("All early-instantiated @Transactional beans carry transaction advice.");
      return;
    }
    String message =
        "The following beans declare @Transactional behaviour but are proxied without"
            + " transaction advice: "
            + offenders
            + ". This happens to beans instantiated before context refresh completes (e.g."
            + " via a BeanPostProcessor dependency graph), where annotation-driven advisors"
            + " are silently skipped. Either break the early-instantiation chain or cover the"
            + " bean with an explicit XML advisor; see the AOP notes in"
            + " applicationContext-service.xml.";
    if (realSecurityManagerPresent) {
      throw new IllegalStateException(message);
    }
    log.error(message);
  }

  /**
   * Returns a description of every already-instantiated singleton in an application package that
   * declares {@code @Transactional} behaviour without a {@link TransactionInterceptor} in its
   * advice chain.
   */
  List<String> findBeansMissingTransactionAdvice(ConfigurableListableBeanFactory beanFactory) {
    List<String> offenders = new ArrayList<>();
    for (String name : beanFactory.getSingletonNames()) {
      Object bean = beanFactory.getSingleton(name);
      if (bean == null) {
        continue;
      }
      Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
      String packageName = targetClass.getPackageName();
      if (!packageName.startsWith("com.researchspace") && !packageName.startsWith("com.axiope")) {
        continue;
      }
      if (declaresTransactionalBehaviour(targetClass) && !hasTransactionAdvice(bean)) {
        offenders.add(name + " (" + targetClass.getName() + ")");
      }
    }
    return offenders;
  }

  private static boolean declaresTransactionalBehaviour(Class<?> targetClass) {
    for (Class<? extends Annotation> annotation : TRANSACTIONAL_ANNOTATIONS) {
      if (AnnotatedElementUtils.hasAnnotation(targetClass, annotation)) {
        return true;
      }
      for (Method method : targetClass.getMethods()) {
        if (AnnotatedElementUtils.hasAnnotation(method, annotation)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasTransactionAdvice(Object bean) {
    return bean instanceof Advised advised
        && Arrays.stream(advised.getAdvisors())
            .anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor);
  }
}

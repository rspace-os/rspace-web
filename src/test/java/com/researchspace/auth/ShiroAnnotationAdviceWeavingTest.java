package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Proves that the advisor pair declared in WEB-INF/security.xml (an advisor auto-proxy creator plus
 * an {@link AuthorizationAttributeSourceAdvisor}) weaves Shiro's authorization-annotation advice,
 * and that the woven advice denies an under-privileged subject.
 *
 * <p>security.xml is a webapp-only context, so service beans are never Shiro-proxied in Spring
 * tests, and the AuthorizationException tests in FormManagerTest pass via explicit permission
 * checks inside method bodies. Methods guarded only by an annotation (for example
 * FormManagerImpl.create) rely entirely on this weaving, and the classic failure mode of swapping
 * the advisor class is that advice silently stops being applied.
 *
 * <p>To guard the production wiring rather than a copy of it, the two bean classes are read from
 * security.xml itself: deleting either declaration, or swapping it for a class outside its
 * contract, fails this test.
 */
public class ShiroAnnotationAdviceWeavingTest {

  private static final Path SECURITY_XML =
      Path.of("src", "main", "webapp", "WEB-INF", "security.xml");
  private static final String PERMISSION = "FORM:CREATE";

  public static class GuardedService {
    @RequiresPermissions(PERMISSION)
    public String createForm() {
      return "created";
    }
  }

  /** Authorization-only realm: subjects are built directly, no login step. */
  static class StubRealm extends AuthorizingRealm {
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
      SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
      if ("privileged".equals(principals.getPrimaryPrincipal())) {
        info.addStringPermission(PERMISSION);
      }
      return info;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
      return null;
    }
  }

  private AnnotationConfigApplicationContext context;

  @BeforeEach
  public void setUp() throws Exception {
    List<Class<?>> declared = beanClassesDeclaredInSecurityXml();
    Class<?> autoProxyCreator =
        exactlyOneAssignableTo(declared, AbstractAdvisorAutoProxyCreator.class);
    Class<?> advisor = exactlyOneAssignableTo(declared, AuthorizationAttributeSourceAdvisor.class);

    context = new AnnotationConfigApplicationContext();
    context.registerBean(autoProxyCreator);
    context.registerBean(advisor);
    context.registerBean(
        "securityManager",
        SecurityManager.class,
        () -> new DefaultSecurityManager(new StubRealm()));
    context.registerBean(GuardedService.class);
    context.refresh();
  }

  @AfterEach
  public void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void annotatedBeanIsProxiedByTheAdvisor() {
    assertTrue(
        AopUtils.isAopProxy(context.getBean(GuardedService.class)),
        "the @RequiresPermissions bean was not proxied: annotation advice is not being woven");
  }

  @Test
  public void underPrivilegedSubjectIsDenied() {
    GuardedService service = context.getBean(GuardedService.class);
    runAs("restricted", () -> assertThrows(UnauthorizedException.class, service::createForm));
  }

  @Test
  public void privilegedSubjectPassesTheGuard() {
    GuardedService service = context.getBean(GuardedService.class);
    runAs("privileged", () -> assertEquals("created", service.createForm()));
  }

  private void runAs(String principal, Runnable assertion) {
    Subject subject =
        new Subject.Builder(context.getBean(SecurityManager.class))
            .principals(new SimplePrincipalCollection(principal, "stubRealm"))
            .authenticated(true)
            .buildSubject();
    subject.execute(assertion);
  }

  /**
   * Every loadable bean class declared in security.xml. Classes that cannot be loaded are skipped
   * rather than failing: only the two classes under contract matter here, and if one of THOSE is
   * missing or renamed, {@link #exactlyOneAssignableTo} fails with the full declared list.
   */
  private static List<Class<?>> beanClassesDeclaredInSecurityXml() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document doc = factory.newDocumentBuilder().parse(SECURITY_XML.toFile());
    NodeList beans = doc.getElementsByTagNameNS("*", "bean");
    List<Class<?>> classes = new ArrayList<>();
    for (int i = 0; i < beans.getLength(); i++) {
      String className = ((Element) beans.item(i)).getAttribute("class");
      if (className.isEmpty()) {
        continue;
      }
      try {
        classes.add(Class.forName(className));
      } catch (ClassNotFoundException e) {
        // not on the test classpath; cannot be one of the contract classes below
      }
    }
    return classes;
  }

  private static Class<?> exactlyOneAssignableTo(List<Class<?>> declared, Class<?> contract) {
    List<Class<?>> matches = declared.stream().filter(contract::isAssignableFrom).toList();
    assertEquals(
        1,
        matches.size(),
        () ->
            "expected security.xml to declare exactly one bean assignable to "
                + contract.getName()
                + " but found "
                + matches
                + " among declared classes "
                + declared);
    return matches.get(0);
  }
}

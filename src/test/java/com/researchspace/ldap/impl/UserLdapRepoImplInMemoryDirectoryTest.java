package com.researchspace.ldap.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.axiope.service.cfg.LdapConfig;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Runs the LDAP search and bind paths against a real (in-memory) directory, with the Spring LDAP
 * beans built by {@link LdapConfig}, so Spring LDAP upgrades are checked without the external
 * server that {@code UserLdapRepoRealConnectionTest} needs.
 */
@ExtendWith(MockitoExtension.class)
class UserLdapRepoImplInMemoryDirectoryTest {

  private static final String BASE_DN = "dc=example,dc=com";
  private static final String USER_DN = "uid=jbloggs,ou=people," + BASE_DN;
  private static final String PASSWORD = "correct-horse";
  private static final String SERVICE_DN = "cn=rspace-service," + BASE_DN;
  private static final String SERVICE_PASSWORD = "service-secret";

  private static InMemoryDirectoryServer directory;

  @Mock private IPropertyHolder properties;

  @BeforeAll
  static void startDirectory() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
    // Active Directory style 'distinguishedName' attribute is not in the default schema
    config.setSchema(null);
    config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("test", 0));
    // UnboundID refuses a bind with a DN and an empty password, but some directories accept it
    // as an unauthenticated bind (RFC 4513); behave like those so the empty-password test fails
    // if Spring LDAP ever lets such a bind through again
    config.addInMemoryOperationInterceptor(
        new InMemoryOperationInterceptor() {
          @Override
          public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest request) {
            if (request.getRequest().getPassword().getValueLength() == 0) {
              request.setRequest(new SimpleBindRequest());
            }
          }
        });
    directory = new InMemoryDirectoryServer(config);
    directory.add("dn: " + BASE_DN, "objectClass: domain", "dc: example");
    directory.add("dn: ou=people," + BASE_DN, "objectClass: organizationalUnit", "ou: people");
    directory.add(
        "dn: " + USER_DN,
        "objectClass: inetOrgPerson",
        "uid: jbloggs",
        "cn: Joe Bloggs",
        "givenName: Joe",
        "sn: Bloggs",
        "mail: jbloggs@example.com",
        "distinguishedName: " + USER_DN,
        "userPassword: " + PASSWORD);
    directory.add(
        "dn: " + SERVICE_DN,
        "objectClass: person",
        "cn: rspace-service",
        "sn: service",
        "userPassword: " + SERVICE_PASSWORD);
    directory.startListening();
  }

  @AfterAll
  static void stopDirectory() {
    directory.shutDown(true);
  }

  @Test
  void searchMapsDirectoryAttributesToUser() {
    when(properties.getLdapEnabled()).thenReturn("true");

    try (AnnotationConfigApplicationContext ctx = ldapContext(true)) {
      User found = repo(ctx).findUserByUsername("jbloggs");

      assertNotNull(found);
      assertEquals("jbloggs", found.getUsername());
      assertEquals(USER_DN, found.getToken());
      assertEquals("Joe", found.getFirstName());
      assertEquals("Bloggs", found.getLastName());
      assertEquals("jbloggs@example.com", found.getEmail());
      assertNull(repo(ctx).findUserByUsername("nobody"));
    }
  }

  @Test
  void authenticateBindsWithUserPassword() {
    when(properties.getLdapEnabled()).thenReturn("true");
    when(properties.isLdapAuthenticationEnabled()).thenReturn(true);

    try (AnnotationConfigApplicationContext ctx = ldapContext(true)) {
      UserLdapRepoImpl repo = repo(ctx);

      User authenticated = repo.authenticate("jbloggs", PASSWORD);
      assertNotNull(authenticated);
      assertEquals("jbloggs", authenticated.getUsername());
      assertNull(repo.authenticate("jbloggs", "wrong-password"));
    }
  }

  @Test
  void contextSourceRefusesBindWithEmptyPassword() {
    try (AnnotationConfigApplicationContext ctx = ldapContext(true)) {
      LdapContextSource contextSource = ctx.getBean(LdapContextSource.class);

      assertThrows(AuthenticationException.class, () -> contextSource.getContext(USER_DN, ""));
    }
  }

  @Test
  void searchBindsAsServiceAccountWhenAnonymousBindOff() {
    when(properties.getLdapEnabled()).thenReturn("true");

    try (AnnotationConfigApplicationContext ctx = serviceAccountContext(SERVICE_PASSWORD)) {
      User found = repo(ctx).findUserByUsername("jbloggs");

      assertNotNull(found);
      assertEquals(USER_DN, found.getToken());
    }
  }

  @Test
  void searchFailsWhenServiceAccountPasswordEmpty() {
    when(properties.getLdapEnabled()).thenReturn("true");

    try (AnnotationConfigApplicationContext ctx = serviceAccountContext("")) {
      UserLdapRepoImpl repo = repo(ctx);

      assertThrows(AuthenticationException.class, () -> repo.findUserByUsername("jbloggs"));
    }
  }

  @Test
  void contextStartsWhenLdapDisabled() {
    try (AnnotationConfigApplicationContext ctx = ldapContext(false)) {
      assertNotNull(ctx.getBean(LdapTemplate.class));
    }
  }

  private AnnotationConfigApplicationContext ldapContext(boolean enabled) {
    return ldapContext(enabled, true, "anonymous", "anonymous");
  }

  private AnnotationConfigApplicationContext serviceAccountContext(String bindPassword) {
    return ldapContext(true, false, SERVICE_DN, bindPassword);
  }

  private AnnotationConfigApplicationContext ldapContext(
      boolean enabled, boolean anonymousBind, String bindDn, String bindPassword) {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    Map<String, Object> props =
        Map.of(
            "ldap.enabled",
            String.valueOf(enabled),
            "ldap.url",
            "ldap://localhost:" + directory.getListenPort(),
            "ldap.baseSuffix",
            BASE_DN,
            "ldap.ignorePartialResultException",
            "false",
            "ldap.bindQuery.dn",
            bindDn,
            "ldap.bindQuery.password",
            bindPassword,
            "ldap.anonymousBind",
            String.valueOf(anonymousBind),
            "ldap.userSearchQuery.objectSidField",
            "");
    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ldapTest", props));
    ctx.register(PropertySourcesPlaceholderConfigurer.class, LdapConfig.class);
    ctx.refresh();
    return ctx;
  }

  private UserLdapRepoImpl repo(AnnotationConfigApplicationContext ctx) {
    UserLdapRepoImpl repo = new UserLdapRepoImpl();
    ReflectionTestUtils.setField(repo, "ldapContext", ctx.getBean(LdapContextSource.class));
    ReflectionTestUtils.setField(repo, "ldapTemplate", ctx.getBean(LdapTemplate.class));
    ReflectionTestUtils.setField(repo, "properties", properties);
    ReflectionTestUtils.setField(repo, "ldapSearchQueryUidField", "uid");
    ReflectionTestUtils.setField(repo, "ldapSearchQueryDnField", "distinguishedName");
    ReflectionTestUtils.setField(repo, "fallbackDnCalculationEnabled", "false");
    return repo;
  }
}

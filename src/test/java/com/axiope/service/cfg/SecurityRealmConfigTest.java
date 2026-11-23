package com.axiope.service.cfg;

import static com.researchspace.auth.ApiRealm.API_REALM_NAME;
import static com.researchspace.auth.ExternalAuthPassThruRealm.EXT_OAUTH_REAM_NAME;
import static com.researchspace.auth.SSOPassThruRealm.SSO_REALM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.researchspace.auth.AnonymousGuestRealm;
import com.researchspace.auth.GlobalInitSysadminRealm;
import com.researchspace.auth.LdapRealm;
import com.researchspace.auth.ShiroRealm;
import com.researchspace.auth.SlackRealm;
import com.researchspace.auth.UsernamePasswordCredentialsMatcher;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.auth.wopi.WopiRealm;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.SSOTestContext;
import com.researchspace.testutils.WithSpringContext;
import jakarta.servlet.http.Cookie;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.subject.WebSubject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/** Tests that realms are configured properly for various deployment scenarios */
public class SecurityRealmConfigTest {

  // mocking some dependencies mean we don't have to load all the classes in prod configuration,
  // just dependencies for the beans defined in this configuration
  @Configuration()
  @Profile(
      "securitytest") // needs to  define its own profile so that it doesn't pollute configuration
  // of other tests
  static class MockDependenciesConfig {
    @Bean
    UserManager userManager() {
      return mock(UserManager.class);
    }

    @Bean
    WhiteListIPChecker whiteListIPChecker() {
      return mock(WhiteListIPChecker.class);
    }

    @Bean
    GroupManager grpManager() {
      return mock(GroupManager.class);
    }

    @Bean
    IPropertyHolder propertyHolder() {
      return mock(IPropertyHolder.class);
    }

    @Bean
    MaintenanceManager maintenanceMgr() {
      return mock(MaintenanceManager.class);
    }

    @Bean
    UserLdapRepo userLdapRepo() {
      return mock(UserLdapRepo.class);
    }

    @Bean
    UsernamePasswordCredentialsMatcher credentialsMatcher() {
      return mock(UsernamePasswordCredentialsMatcher.class);
    }
  }

  @ActiveProfiles({"prod", "securitytest"})
  @ContextConfiguration(
      classes = {
        MockDependenciesConfig.class,
        SecurityRunProdConfig.class,
        DeploymentPropertyConfig.class,
      })
  @WithSpringContext
  public static class ProdSecurityTestBase {
    @Autowired RealmSecurityManager realmMgr;

    void assertAPIRealm() {
      assertThat(realmMgr.getRealms().stream()).anyMatch(r -> r.getName().equals(API_REALM_NAME));
    }

    void assertStandardRealm() {
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(ShiroRealm.DEFAULT_USER_PASSWD_REALM));
    }

    void assertSSORealm() {
      assertThat(realmMgr.getRealms().stream()).anyMatch(r -> r.getName().equals(SSO_REALM_NAME));
    }

    void assertSlackRealm() {
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(SlackRealm.SLACK_REALM_NAME));
    }

    void assertWopiRealm() {
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(WopiRealm.WOPI_REALM_NAME));
    }

    void assertLdapRealm() {
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(LdapRealm.LDAP_REALM_NAME));
    }

    void assertGlobalInitRealm() {
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(GlobalInitSysadminRealm.REALM_NAME));
    }

    void assertAnonymousGuestRealm() {
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(AnonymousGuestRealm.REALM_NAME));
    }
  }

  @Nested
  @TestPropertySource(properties = {"deployment.standalone=true"})
  public class StandaloneEnterpriseProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(5);
      assertStandardRealm();
      assertAPIRealm();
      assertSlackRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }

    // RSDEV-1409: malformed base64, and well-formed base64 that fails decryption
    @ParameterizedTest
    @ValueSource(
        strings = {
          "!not-base64!",
          "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQQ=="
        })
    public void invalidRememberMeCookieIsIgnoredWithoutWarnings(String cookieValue) {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(new Cookie("rememberMe", cookieValue));
      MockHttpServletResponse response = new MockHttpServletResponse();

      StringAppenderForTestLogging shiroWarnings = new StringAppenderForTestLogging();
      String shiroLoggerName = "org.apache.shiro";
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      org.apache.logging.log4j.core.config.Configuration logConfig = ctx.getConfiguration();
      LoggerConfig existingShiroLogger = logConfig.getLoggers().get(shiroLoggerName);
      shiroWarnings.start();
      if (existingShiroLogger != null) {
        existingShiroLogger.addAppender(shiroWarnings, Level.WARN, null);
      } else {
        LoggerConfig shiroLogger = new LoggerConfig(shiroLoggerName, Level.WARN, true);
        shiroLogger.addAppender(shiroWarnings, Level.WARN, null);
        logConfig.addLogger(shiroLoggerName, shiroLogger);
      }
      ctx.updateLoggers();

      Subject subject;
      try {
        subject = new WebSubject.Builder(realmMgr, request, response).buildWebSubject();
      } finally {
        if (existingShiroLogger != null) {
          existingShiroLogger.removeAppender(shiroWarnings.getName());
        } else {
          logConfig.removeLogger(shiroLoggerName);
        }
        ctx.updateLoggers();
        shiroWarnings.stop();
      }

      assertNull(subject.getPrincipals());
      assertFalse(subject.isRemembered());
      assertNull(response.getCookie("rememberMe"));
      assertThat(response.getHeaders("Set-Cookie")).noneMatch(h -> h.contains("rememberMe"));
      assertThat(shiroWarnings.logContents).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"deployment.standalone=true", "ldap.authentication.enabled=true"})
  public class StandaloneLdapEnterpriseProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(6);
      assertStandardRealm();
      assertLdapRealm();
      assertAPIRealm();
      assertSlackRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }
  }

  @CommunityTestContext
  @Nested
  public class CommunityProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(6);
      assertAPIRealm();
      assertThat(realmMgr.getRealms().stream())
          .anyMatch(r -> r.getName().equals(EXT_OAUTH_REAM_NAME));
      assertSlackRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }
  }

  @Nested
  @SSOTestContext
  public class SSOProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(5);
      assertAPIRealm();
      assertSSORealm();
      assertSlackRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }
  }

  @Nested
  @SSOTestContext
  @TestPropertySource(properties = {"deployment.sso.adminLogin.enabled=true"})
  public class SSOAdminLoginProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(6);
      assertAPIRealm();
      assertSSORealm();
      assertStandardRealm();
      assertSlackRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }
  }

  @Nested
  @TestPropertySource(properties = {"deployment.standalone=true", "collabora.wopi.enabled=true"})
  public class CollaboraProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(6);
      assertAPIRealm();
      assertSlackRealm();
      assertWopiRealm();
      assertStandardRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }
  }

  @Nested
  @TestPropertySource(properties = {"deployment.standalone=true", "msoffice.wopi.enabled=true"})
  public class MsOfficeProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertThat(realmMgr.getRealms()).hasSize(6);
      assertAPIRealm();
      assertSlackRealm();
      assertWopiRealm();
      assertStandardRealm();
      assertGlobalInitRealm();
      assertAnonymousGuestRealm();
    }
  }
}

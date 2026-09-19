package com.axiope.service.cfg;

import static com.researchspace.auth.ApiRealm.API_REALM_NAME;
import static com.researchspace.auth.ExternalAuthPassThruRealm.EXT_OAUTH_REAM_NAME;
import static com.researchspace.auth.SSOPassThruRealm.SSO_REALM_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.GlobalInitSysadminRealm;
import com.researchspace.auth.LdapRealm;
import com.researchspace.auth.ShiroRealm;
import com.researchspace.auth.SlackRealm;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.auth.wopi.WopiRealm;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.maintenance.service.WhiteListedIPAddressManager;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.GroupManager;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.SSOTestContext;
import com.researchspace.testutils.WithSpringContext;
import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
    @Mock UserManager mgr;
    @Mock MemoryConstrainedCacheManager ehCacheManager;
    @Mock IPropertyHolder propertyHolder;
    @Mock WhiteListedIPAddressManager ipMgr;
    @Mock MaintenanceManager maintenanceMgr;
    @Mock GroupManager grpMgr;
    @Mock UserLdapRepo userLdapRepo;
    @Mock WhiteListIPChecker ipWhitelist;

    @Mock OAuthAppManager oAuthAppManager;

    @Bean
    MemoryConstrainedCacheManager ehcacheManager() {
      initMocks();
      return ehCacheManager;
    }

    private void initMocks() {
      MockitoAnnotations.openMocks(MockDependenciesConfig.class);
    }

    @Bean
    UserManager userManager() {
      initMocks();
      return mgr;
    }

    @Bean
    WhiteListIPChecker whiteListIPChecker() {
      initMocks();
      return ipWhitelist;
    }

    @Bean
    GroupManager grpManager() {
      initMocks();
      return grpMgr;
    }

    @Bean
    IPropertyHolder propertyHolder() {
      initMocks();
      return propertyHolder;
    }

    @Bean
    WhiteListedIPAddressManager ipMgr() {
      initMocks();
      return ipMgr;
    }

    @Bean
    MaintenanceManager maintenanceMgr() {
      initMocks();
      return maintenanceMgr;
    }

    @Bean
    UserLdapRepo userLdapRepo() {
      initMocks();
      return userLdapRepo;
    }
  }

  @ActiveProfiles({"prod", "securitytest"})
  // since updating to Spring 5 order seems important; MockDependencies has to be 1st.
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
      assertTrue(realmMgr.getRealms().stream().anyMatch(r -> r.getName().equals(API_REALM_NAME)));
    }

    void assertStandardRealm() {
      assertTrue(
          realmMgr.getRealms().stream()
              .anyMatch(r -> r.getName().equals(ShiroRealm.DEFAULT_USER_PASSWD_REALM)));
    }

    void assertSSORealm() {
      assertTrue(realmMgr.getRealms().stream().anyMatch(r -> r.getName().equals(SSO_REALM_NAME)));
    }

    void assertSlackRealm() {
      assertTrue(
          realmMgr.getRealms().stream()
              .anyMatch(r -> r.getName().equals(SlackRealm.SLACK_REALM_NAME)));
    }

    void assertWopiRealm() {
      assertTrue(
          realmMgr.getRealms().stream()
              .anyMatch(r -> r.getName().equals(WopiRealm.WOPI_REALM_NAME)));
    }

    void assertLdapRealm() {
      assertTrue(
          realmMgr.getRealms().stream()
              .anyMatch(r -> r.getName().equals(LdapRealm.LDAP_REALM_NAME)));
    }

    void assertGlobalInitRealm() {
      assertTrue(
          realmMgr.getRealms().stream()
              .anyMatch(r -> r.getName().equals(GlobalInitSysadminRealm.REALM_NAME)));
    }
  }

  @Nested
  @TestPropertySource(properties = {"deployment.standalone=true"})
  public class StandaloneEnterpriseProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(4, realmMgr.getRealms().size());
      assertStandardRealm();
      assertAPIRealm();
      assertSlackRealm();
      assertGlobalInitRealm();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"deployment.standalone=true", "ldap.authentication.enabled=true"})
  public class StandaloneLdapEnterpriseProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(5, realmMgr.getRealms().size());
      assertStandardRealm();
      assertLdapRealm();
      assertAPIRealm();
      assertSlackRealm();
      assertGlobalInitRealm();
    }
  }

  @CommunityTestContext
  @Nested
  public class CommunityProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(5, realmMgr.getRealms().size());
      assertAPIRealm();
      assertTrue(
          realmMgr.getRealms().stream().anyMatch(r -> r.getName().equals(EXT_OAUTH_REAM_NAME)));
      assertSlackRealm();
      assertGlobalInitRealm();
    }
  }

  @Nested
  @SSOTestContext
  public class SSOProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(4, realmMgr.getRealms().size());
      assertAPIRealm();
      assertSSORealm();
      assertSlackRealm();
      assertGlobalInitRealm();
    }
  }

  @Nested
  @SSOTestContext
  @TestPropertySource(properties = {"deployment.sso.adminLogin.enabled=true"})
  public class SSOAdminLoginProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(5, realmMgr.getRealms().size());
      assertAPIRealm();
      assertSSORealm();
      assertStandardRealm();
      assertSlackRealm();
      assertGlobalInitRealm();
    }
  }

  @Nested
  @TestPropertySource(properties = {"deployment.standalone=true", "collabora.wopi.enabled=true"})
  public class CollaboraProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(5, realmMgr.getRealms().size());
      assertAPIRealm();
      assertSlackRealm();
      assertWopiRealm();
      assertStandardRealm();
      assertGlobalInitRealm();
    }
  }

  @Nested
  @TestPropertySource(properties = {"deployment.standalone=true", "msoffice.wopi.enabled=true"})
  public class MsOfficeProdConfigTest extends ProdSecurityTestBase {
    @Test
    public void testRealm() {
      assertEquals(5, realmMgr.getRealms().size());
      assertAPIRealm();
      assertSlackRealm();
      assertWopiRealm();
      assertStandardRealm();
      assertGlobalInitRealm();
    }
  }
}

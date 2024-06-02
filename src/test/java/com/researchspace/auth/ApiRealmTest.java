package com.researchspace.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.UserApiKeyDao;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.cache.CacheManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration()
public class ApiRealmTest extends SpringTransactionalTest {

  private static final String API_AUTHENTICATION_CACHE = "API.authenticationCache";
  @Autowired ApiRealmTestSpy apiRealmTestSpy;
  @Autowired UserApiKeyDao apiKeyDao;
  @Autowired CacheManager cache;
  private User subject = null;
  String key = "";

  @Before
  public void setUp() throws Exception {
    subject = createAndSaveRandomUser();
    key = RandomStringUtils.randomAlphabetic(16);
    UserApiKey apikey = new UserApiKey(subject, key);
    apiKeyDao.save(apikey);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testCaching() {
    // configured in SecurityTestConfig
    assertTrue(apiRealmTestSpy.isAuthenticationCachingEnabled());
    // this must be a name in ehcache-shiro.xml
    assertEquals(API_AUTHENTICATION_CACHE, apiRealmTestSpy.getAuthenticationCacheName());
    assertFalse(apiRealmTestSpy.isDoGetInfoCalled());
    authenticate();
    assertTrue(apiRealmTestSpy.isDoGetInfoCalled());
    apiRealmTestSpy.reset();
    authenticate();
    // this isn't called 2nd time round because the cache is populated
    assertFalse(apiRealmTestSpy.isDoGetInfoCalled());

    // remove from cache, this should trigger a lookup
    cache.getCache(API_AUTHENTICATION_CACHE).remove(subject.getUsername());
    authenticate();
    assertTrue(apiRealmTestSpy.isDoGetInfoCalled());
  }

  private void authenticate() {
    AuthenticationInfo info;
    info =
        apiRealmTestSpy.getAuthenticationInfo(
            new ApiKeyAuthenticationToken(subject.getUsername(), key));
  }
}

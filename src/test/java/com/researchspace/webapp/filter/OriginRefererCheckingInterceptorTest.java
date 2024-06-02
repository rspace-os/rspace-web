package com.researchspace.webapp.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class OriginRefererCheckingInterceptorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  private OriginRefererCheckerImpl originRefererChecker;

  private @Mock IPropertyHolder propertyHolder;
  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  private String testServerUrl = "https://junit.rspace.com:80";
  private String testValidHeaderUrl = testServerUrl + "/workspace";

  private String testServerAlias1 = "https://rs-alias1:80";
  private String testServerAlias2 = "https://rs-alias2";
  private String extraDomains = testServerAlias1 + ", " + testServerAlias2;

  @Before
  public void setUp() throws Exception {
    originRefererChecker = new OriginRefererCheckerImpl();
    originRefererChecker.setProperties(propertyHolder);
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl);
    originRefererChecker.setAcceptedDomainsDeploymentProp(extraDomains);
  }

  @Test
  public void testSingleExtraUrl() {
    originRefererChecker = new OriginRefererCheckerImpl();
    originRefererChecker.setProperties(propertyHolder);
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl);
    originRefererChecker.setAcceptedDomainsDeploymentProp(
        "https://rs-inventory-alpha.researchspace.com");
    req = new MockHttpServletRequest();
    req.addHeader("origin", "https://rs-inventory-alpha.researchspace.com");
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());
  }

  @Test
  public void testValidOriginsAndHeaders() throws IOException {

    req = new MockHttpServletRequest();
    req.addHeader("origin", testValidHeaderUrl);
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());

    req = new MockHttpServletRequest();
    req.addHeader("referer", testValidHeaderUrl);
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());

    // check tunnelled localhost connection (RSPAC-1226)
    req = new MockHttpServletRequest();
    req.addHeader("origin", "http://localhost:8080");
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());

    req = new MockHttpServletRequest();
    req.addHeader("referer", "https://localhost:8443");
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());

    // aliases set through deployment property
    req = new MockHttpServletRequest();
    req.addHeader("origin", testServerAlias1);
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());

    req = new MockHttpServletRequest();
    req.addHeader("referer", testServerAlias2);
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());
  }

  @Test
  public void testInvalidHeaders() throws IOException {
    req = new MockHttpServletRequest();
    req.addHeader("origin", "siteA");
    Optional<String> errOptional = originRefererChecker.checkOriginReferer(req, resp);
    assertTrue(errOptional.isPresent());
    assertTrue(errOptional.get().contains("mismatched origin"));

    req = new MockHttpServletRequest();
    req.addHeader("referer", "siteA");
    errOptional = originRefererChecker.checkOriginReferer(req, resp);
    assertTrue(errOptional.isPresent());
    assertTrue(errOptional.get().contains("mismatched referer"));

    req = new MockHttpServletRequest();
    errOptional = originRefererChecker.checkOriginReferer(req, resp);
    assertTrue(errOptional.isPresent());

    assertTrue(errOptional.get().contains("no origin or referer"));
  }

  @Test
  public void acceptGetRequestFromAnywhere() throws IOException {
    req = new MockHttpServletRequest();
    req.setMethod("GET");
    assertFalse(originRefererChecker.checkOriginReferer(req, resp).isPresent());
  }

  @Test
  public void testSetupValidDomains() {
    List<String> defaultDomains = originRefererChecker.listAcceptedDomains();
    assertEquals(5, defaultDomains.size());
    assertEquals(testServerUrl, defaultDomains.get(0));
    assertEquals(testServerAlias1, defaultDomains.get(1));
    assertEquals(testServerAlias2, defaultDomains.get(2));

    // trailing slash stripped from server url property
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl + "/");
    List<String> strippedServerUrl = originRefererChecker.listAcceptedDomains();
    assertEquals(5, strippedServerUrl.size());
    assertEquals(testServerUrl, strippedServerUrl.get(0));
  }
}

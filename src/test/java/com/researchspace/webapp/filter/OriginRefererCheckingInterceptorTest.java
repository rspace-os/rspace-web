package com.researchspace.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class OriginRefererCheckingInterceptorTest {

  private OriginRefererCheckerImpl originRefererChecker;

  private @Mock IPropertyHolder propertyHolder;
  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  private String testServerUrl = "https://junit.rspace.com:80";
  private String testValidHeaderUrl = testServerUrl + "/workspace";

  private String testServerAlias1 = "https://rs-alias1:80";
  private String testServerAlias2 = "https://rs-alias2";
  private String extraDomains = testServerAlias1 + ", " + testServerAlias2;

  @BeforeEach
  public void setUp() throws Exception {
    originRefererChecker = new OriginRefererCheckerImpl();
    originRefererChecker.setProperties(propertyHolder);
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
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();
  }

  @Test
  public void testValidOriginsAndHeaders() throws IOException {
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl);

    req = new MockHttpServletRequest();
    req.addHeader("origin", testValidHeaderUrl);
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();

    req = new MockHttpServletRequest();
    req.addHeader("referer", testValidHeaderUrl);
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();

    // check tunnelled localhost connection (RSPAC-1226)
    req = new MockHttpServletRequest();
    req.addHeader("origin", "http://localhost:8080");
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();

    req = new MockHttpServletRequest();
    req.addHeader("referer", "https://localhost:8443");
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();

    // aliases set through deployment property
    req = new MockHttpServletRequest();
    req.addHeader("origin", testServerAlias1);
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();

    req = new MockHttpServletRequest();
    req.addHeader("referer", testServerAlias2);
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();
  }

  @Test
  public void testInvalidHeaders() throws IOException {
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl);
    req = new MockHttpServletRequest();
    req.addHeader("origin", "siteA");
    Optional<String> errOptional = originRefererChecker.checkOriginReferer(req, resp);
    assertThat(errOptional).isPresent();
    assertThat(errOptional.get()).contains("mismatched origin");

    req = new MockHttpServletRequest();
    req.addHeader("referer", "siteA");
    errOptional = originRefererChecker.checkOriginReferer(req, resp);
    assertThat(errOptional).isPresent();
    assertThat(errOptional.get()).contains("mismatched referer");

    req = new MockHttpServletRequest();
    errOptional = originRefererChecker.checkOriginReferer(req, resp);
    assertThat(errOptional).isPresent();

    assertThat(errOptional.get()).contains("no origin or referer");
  }

  @Test
  public void acceptGetRequestFromAnywhere() throws IOException {
    req = new MockHttpServletRequest();
    req.setMethod("GET");
    assertThat(originRefererChecker.checkOriginReferer(req, resp)).isNotPresent();
  }

  @Test
  public void testSetupValidDomains() {
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl);
    List<String> defaultDomains = originRefererChecker.listAcceptedDomains();
    assertThat(defaultDomains).hasSize(5);
    assertEquals(testServerUrl, defaultDomains.get(0));
    assertEquals(testServerAlias1, defaultDomains.get(1));
    assertEquals(testServerAlias2, defaultDomains.get(2));

    // trailing slash stripped from server url property
    Mockito.when(propertyHolder.getServerUrl()).thenReturn(testServerUrl + "/");
    List<String> strippedServerUrl = originRefererChecker.listAcceptedDomains();
    assertThat(strippedServerUrl).hasSize(5);
    assertEquals(testServerUrl, strippedServerUrl.get(0));
  }
}

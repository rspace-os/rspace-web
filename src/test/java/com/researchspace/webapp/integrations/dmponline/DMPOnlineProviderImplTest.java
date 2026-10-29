package com.researchspace.webapp.integrations.dmponline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.rda.model.extras.DMPList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DMPOnlineProviderImplTest {

  private static final String BASE_URL = "https://dmponline.example.ac.uk";
  private DMPOnlineProviderImpl provider;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    provider = new DMPOnlineProviderImpl(restTemplate);
    ReflectionTestUtils.setField(provider, "baseUrl", BASE_URL);
    provider.init();
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  void planOnConfiguredServerIsFetchedFromConfiguredBase() {
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans/12345"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    DMPList result = provider.getPlanByUrlId(BASE_URL + "/api/v2/plans/12345", "tok");
    mockServer.verify();
    assertEquals(0, result.getItems().size());
  }

  @Test
  void planListUsesConfiguredBaseAndNumericPaging() {
    ReflectionTestUtils.setField(provider, "baseUrl", BASE_URL + "/");
    provider.init();
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans?page=2&per_page=10"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    provider.listPlans(2, 10, "tok");
    mockServer.verify();
  }

  @Test
  void planUriKeepsOnlyThePlanId() {
    assertEquals(
        BASE_URL + "/api/v2/plans/7",
        provider.planUri("HTTPS://DMPONLINE.example.ac.uk/api/v2/plans/7").toString());
  }

  @Test
  void trailingSlashOnBaseAndExplicitDefaultPortAreAccepted() {
    ReflectionTestUtils.setField(provider, "baseUrl", BASE_URL + "/");
    provider.init();
    assertEquals(
        BASE_URL + "/api/v2/plans/7",
        provider.planUri("https://dmponline.example.ac.uk:443/api/v2/plans/7").toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://dmponline.example.ac.uk/api/v2/plans/7",
        "https://dmponline.example.ac.uk/api/v2/plans/7/",
        "https://https/api/v2/plans/7",
        "https://other.example/dmp/api/v2/plans/7"
      })
  void planUrlsAsDmpOnlineReportsThemAreAccepted(String url) {
    assertEquals(BASE_URL + "/api/v2/plans/7", provider.planUri(url).toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://doi.org/10.48321/D1ABCD",
        "ftp://dmponline.example.ac.uk/api/v2/plans/1",
        "https://user@dmponline.example.ac.uk/api/v2/plans/1",
        "https://dmponline.example.ac.uk/api/v2/plans/../users",
        "https://dmponline.example.ac.uk/api/v2/plans/1/../../admin",
        "https://dmponline.example.ac.uk/api/v2/templates/1",
        "https://dmponline.example.ac.uk/api/v2/plans/",
        "https://dmponline.example.ac.uk/api/v2/plans/abc",
        "file:///etc/passwd",
        "not a url",
        ""
      })
  void otherUrlsAreRejected(String url) {
    assertThrows(IllegalArgumentException.class, () -> provider.planUri(url));
    mockServer.verify();
  }
}

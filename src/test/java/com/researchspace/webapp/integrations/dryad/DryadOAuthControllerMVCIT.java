package com.researchspace.webapp.integrations.dryad;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.webapp.controller.MVCTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@EnabledIfSystemProperty(named = "nightly", matches = ".*")
public class DryadOAuthControllerMVCIT extends MVCTestBase {

  @Value("${dryad.client.id}")
  private String clientId;

  @Value("${dryad.client.secret}")
  private String clientSecret;

  @Value("${dryad.base.url}")
  private String baseUrl;

  private RestTemplate restTemplate;

  @BeforeEach
  public void setUp() {
    restTemplate = new RestTemplate();
  }

  @Test
  public void getToken() throws Exception {
    DryadOAuthController.DryadAccessToken clientCredentialToken = getDryadAccessToken();
    assertNotNull(clientCredentialToken);
  }

  private DryadOAuthController.DryadAccessToken getDryadAccessToken() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "client_credentials");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    ResponseEntity<DryadOAuthController.DryadAccessToken> resp =
        restTemplate.exchange(
            (baseUrl + "/oauth/token"),
            HttpMethod.POST,
            request,
            DryadOAuthController.DryadAccessToken.class);
    return resp.getBody();
  }
}

package com.researchspace.webapp.integrations.dmponline;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.rda.model.extras.DMPList;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class DMPOnlineProviderImpl implements DMPOnlineProvider {

  @Value("${dmponline.base.url}")
  private String baseUrl;

  private final RestTemplate restTemplate;

  private static String URL_DMP_PLANS;

  public DMPOnlineProviderImpl() {
    this.restTemplate = new RestTemplate();
  }

  @PostConstruct
  public void init() throws URISyntaxException, MalformedURLException {
    URL_DMP_PLANS = this.baseUrl + "/api/v2/plans";
  }

  @Override
  public DMPList getPlanByUrlId(String url, String accessToken)
      throws MalformedURLException, URISyntaxException {
    return restTemplate
        .exchange(
            new URL(url).toURI(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeadersWithToken(accessToken)),
            DMPList.class)
        .getBody();
  }

  public JsonNode listPlans(String page, String per_page, String accessToken) {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(URL_DMP_PLANS)
                .queryParam("page", page)
                .queryParam("per_page", per_page)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeadersWithToken(accessToken)),
            JsonNode.class)
        .getBody();
  }

  private HttpHeaders getHttpHeadersWithToken(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    return headers;
  }
}

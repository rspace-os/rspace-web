package com.researchspace.webapp.integrations.dmponline;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.rda.model.extras.DMPList;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final String PLANS_PATH = "/api/v2/plans";
  private static final Pattern PLAN_PATH = Pattern.compile(".*/api/v2/plans/(\\d+)/?");

  @Value("${dmponline.base.url}")
  private String baseUrl;

  private final RestTemplate restTemplate;

  private String urlPlans;

  public DMPOnlineProviderImpl() {
    this(new RestTemplate());
  }

  DMPOnlineProviderImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @PostConstruct
  public void init() {
    String trimmed = baseUrl.replaceAll("/+$", "");
    this.urlPlans = trimmed + PLANS_PATH;
  }

  @Override
  public DMPList getPlanByUrlId(String url, String accessToken) {
    return restTemplate
        .exchange(
            planUri(url),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeadersWithToken(accessToken)),
            DMPList.class)
        .getBody();
  }

  /**
   * DMPonline identifies a plan by its API URL, which the browser sends back when importing. Only
   * the plan id is taken from it, so the request always goes to the configured server.
   *
   * @throws IllegalArgumentException if the URL does not end in a DMPonline plan id
   */
  URI planUri(String planUrl) {
    URI given;
    try {
      given = new URI(planUrl);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Not a valid DMPonline plan URL");
    }
    // the host is not compared: DMPRoadmap servers have reported their own plan URLs with the wrong
    // scheme or host, and the request is rebuilt from the configured base anyway
    Matcher planPath = PLAN_PATH.matcher(Objects.toString(given.getRawPath(), ""));
    if (given.getScheme() == null
        || !given.getScheme().matches("(?i)https?")
        || given.getRawUserInfo() != null
        || !planPath.matches()) {
      throw new IllegalArgumentException("Not a DMPonline plan URL");
    }
    return UriComponentsBuilder.fromUriString(urlPlans)
        .pathSegment(planPath.group(1))
        .build()
        .toUri();
  }

  public JsonNode listPlans(int page, int per_page, String accessToken) {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(urlPlans)
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

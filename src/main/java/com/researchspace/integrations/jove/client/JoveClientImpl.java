package com.researchspace.integrations.jove.client;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.integrations.jove.model.JoveArticle;
import com.researchspace.integrations.jove.model.JoveSearchRequest;
import com.researchspace.integrations.jove.model.JoveSearchResult;
import com.researchspace.integrations.jove.model.JoveToken;
import com.researchspace.integrations.jove.service.JoveAuthService;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@Getter
@Setter
public class JoveClientImpl implements JoveClient {

  @Autowired private IPropertyHolder propertyHolder;

  private RestTemplate restTemplate = new RestTemplate();

  @Autowired private JoveAuthService joveAuthService;

  // Deployment property which is set on enterprise editions to true but false on community
  // to prevent anybody adding restricted content from Jove if it recognises their email
  @Value("${jove.api.access.enabled}")
  private boolean accessEnabled;

  @Override
  public JoveSearchResult search(User user, JoveSearchRequest searchRequest)
      throws URISyntaxException {
    JoveToken accessToken = joveAuthService.getTokenAndRefreshIfExpired(user);
    log.info(
        "Jove Api call to search endpoint for user: {} with query params: {}",
        user.getUsername(),
        searchRequest);
    URI searchUrl = constructJoveSearchUri(searchRequest);
    log.info("Jove Url with Params: {}", searchUrl);
    return callJoveSearchEndpoint(searchUrl, accessToken);
  }

  private URI constructJoveSearchUri(JoveSearchRequest searchRequest) throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(propertyHolder.getJoveApiUrl());
    uriBuilder.setPath("/api/external/search.php");
    if (!isBlank(searchRequest.getQueryString())) {
      uriBuilder.addParameter("q", searchRequest.getQueryString());
    }
    if (!isBlank(searchRequest.getAuthor())) {
      uriBuilder.addParameter("authors", searchRequest.getAuthor());
    }
    if (!isBlank(searchRequest.getInstitution())) {
      uriBuilder.addParameter("institution", searchRequest.getInstitution());
    }
    if (searchRequest.getPageNumber() != null && searchRequest.getPageNumber() >= 0) {
      uriBuilder.addParameter("page", String.valueOf(searchRequest.getPageNumber()));
    }
    if (searchRequest.getPageSize() != null && searchRequest.getPageSize() > 0) {
      uriBuilder.addParameter("perpage", String.valueOf(searchRequest.getPageSize()));
    }

    return uriBuilder.build();
  }

  private JoveSearchResult callJoveSearchEndpoint(URI joveEndpoint, JoveToken accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", String.format("Bearer %s", accessToken.getToken()));

    return restTemplate
        .exchange(
            joveEndpoint, HttpMethod.GET, new HttpEntity<>(null, headers), JoveSearchResult.class)
        .getBody();
  }

  @Override
  public JoveArticle getArticle(User user, Long articleId) throws URISyntaxException {
    JoveToken accessToken = joveAuthService.getTokenAndRefreshIfExpired(user);
    log.info(
        "Jove Api call to article for user: {} with article id: {}", user.getUsername(), articleId);
    URI articleUrl = constructJoveArticleUri(user, articleId);
    log.info("Article Url to Call: {}", articleUrl);
    return callJoveArticleEndpoint(articleUrl, accessToken);
  }

  private URI constructJoveArticleUri(User user, Long articleId) throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(propertyHolder.getJoveApiUrl());
    uriBuilder.setPath("/api/external/article.php");
    uriBuilder.addParameter("id", String.valueOf(articleId));
    if (accessEnabled) {
      uriBuilder.addParameter("email", user.getEmail());
    }

    return uriBuilder.build();
  }

  private JoveArticle callJoveArticleEndpoint(URI joveEndpoint, JoveToken accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", String.format("Bearer %s", accessToken.getToken()));
    return restTemplate
        .exchange(joveEndpoint, HttpMethod.GET, new HttpEntity<>(null, headers), JoveArticle.class)
        .getBody();
  }
}

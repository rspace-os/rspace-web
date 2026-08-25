package com.researchspace.webapp.integrations.dbrepo;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class DBRepoClient {

  static final String CURRENT_DATABASES_PATH = "/api/v1/database";
  static final String LEGACY_DATABASES_PATH = "/api/database";

  private final RestTemplate restTemplate;

  public DBRepoClient() {
    this(new RestTemplate());
  }

  DBRepoClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public String normalizeBaseUrl(String url) {
    if (StringUtils.isBlank(url)) {
      throw new IllegalArgumentException("DBRepo URL is required.");
    }
    try {
      URI uri = new URI(url.trim()).normalize();
      String scheme = uri.getScheme();
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
        throw new IllegalArgumentException("DBRepo URL must start with http:// or https://.");
      }
      if (StringUtils.isBlank(uri.getHost())) {
        throw new IllegalArgumentException("DBRepo URL must include a host.");
      }
      if (StringUtils.isNotBlank(uri.getRawUserInfo())) {
        throw new IllegalArgumentException("DBRepo URL must not include embedded credentials.");
      }
      if (StringUtils.isNotBlank(uri.getRawQuery())
          || StringUtils.isNotBlank(uri.getRawFragment())) {
        throw new IllegalArgumentException(
            "DBRepo URL must not include a query string or fragment.");
      }
      URI cleaned =
          new URI(
              scheme.toLowerCase(),
              null,
              uri.getHost(),
              uri.getPort(),
              stripTrailingSlash(StringUtils.defaultIfBlank(uri.getRawPath(), "")),
              null,
              null);
      return cleaned.toString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("DBRepo URL is invalid.", e);
    }
  }

  public List<DBRepoDatabaseDTO> listDatabases(String baseUrl, DBRepoCredentials credentials) {
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    try {
      return listDatabasesAt(
          normalizedBaseUrl + CURRENT_DATABASES_PATH, normalizedBaseUrl, credentials);
    } catch (HttpStatusCodeException e) {
      if (HttpStatus.NOT_FOUND.value() == e.getStatusCode().value()) {
        return listDatabasesAt(
            normalizedBaseUrl + LEGACY_DATABASES_PATH, normalizedBaseUrl, credentials);
      }
      throw e;
    }
  }

  private List<DBRepoDatabaseDTO> listDatabasesAt(
      String url, String normalizedBaseUrl, DBRepoCredentials credentials) {
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers(credentials)), JsonNode.class);
    JsonNode body = response.getBody();
    if (body == null || !body.isArray()) {
      return Collections.emptyList();
    }
    List<DBRepoDatabaseDTO> databases = new ArrayList<>();
    for (JsonNode node : body) {
      String id = text(node, "id");
      if (StringUtils.isBlank(id)) {
        continue;
      }
      databases.add(
          new DBRepoDatabaseDTO(
              id,
              StringUtils.defaultIfBlank(text(node, "name"), id),
              text(node, "description"),
              normalizedBaseUrl + "/database/" + id));
    }
    return databases;
  }

  private HttpHeaders headers(DBRepoCredentials credentials) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    headers.setBasicAuth(credentials.username(), credentials.password(), StandardCharsets.UTF_8);
    return headers;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && !value.isNull() ? value.asText() : "";
  }

  private String stripTrailingSlash(String path) {
    if ("/".equals(path)) {
      return "";
    }
    return StringUtils.removeEnd(path, "/");
  }
}

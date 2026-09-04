package com.researchspace.webapp.integrations.dbrepo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

@Slf4j
@Component
public class DBRepoClient {

  static final String CURRENT_DATABASES_PATH = "/api/v1/database";
  static final String LEGACY_DATABASES_PATH = "/api/database";
  static final String TABLE_TYPE = "table";
  static final String VIEW_TYPE = "view";
  private static final String SUBSET_TYPE = "subset";
  private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public DBRepoClient() {
    this(new RestTemplate(), new ObjectMapper());
  }

  DBRepoClient(RestTemplate restTemplate) {
    this(restTemplate, new ObjectMapper());
  }

  DBRepoClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
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

  public DBRepoDatabaseResourcesDTO listDatabaseResources(
      String baseUrl, String databaseId, DBRepoCredentials credentials) {
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    String encodedDatabaseId = encodePathSegment(databaseId);
    List<String> failedTypes = new ArrayList<>();
    List<DBRepoLinkedResourceDTO> tables =
        listResourcesAllowingFailure(
            normalizedBaseUrl, encodedDatabaseId, databaseId, TABLE_TYPE, credentials, failedTypes);
    List<DBRepoLinkedResourceDTO> views =
        listResourcesAllowingFailure(
            normalizedBaseUrl, encodedDatabaseId, databaseId, VIEW_TYPE, credentials, failedTypes);
    List<DBRepoLinkedResourceDTO> subsets =
        listResourcesAllowingFailure(
            normalizedBaseUrl,
            encodedDatabaseId,
            databaseId,
            SUBSET_TYPE,
            credentials,
            failedTypes);

    return new DBRepoDatabaseResourcesDTO(databaseId, tables, views, subsets, failedTypes);
  }

  public byte[] downloadResourceCsv(
      String baseUrl,
      String databaseId,
      String resourceType,
      String resourceId,
      DBRepoCredentials credentials) {
    if (!TABLE_TYPE.equals(resourceType)
        && !VIEW_TYPE.equals(resourceType)
        && !SUBSET_TYPE.equals(resourceType)) {
      throw new IllegalArgumentException("DBRepo CSV download is only supported for resources.");
    }
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    String url =
        normalizedBaseUrl
            + "/api/v1/database/"
            + encodePathSegment(databaseId)
            + "/"
            + resourceType
            + "/"
            + encodePathSegment(resourceId)
            + "/data";
    ResponseEntity<byte[]> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers(credentials, TEXT_CSV)), byte[].class);
    return response.getBody() == null ? new byte[0] : response.getBody();
  }

  public DBRepoResourceMetadataDTO getResourceMetadata(
      String baseUrl,
      String databaseId,
      String resourceType,
      String resourceId,
      DBRepoCredentials credentials) {
    validateRowsResourceType(resourceType);
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            resourceUrl(normalizedBaseUrl, databaseId, resourceType, resourceId),
            HttpMethod.GET,
            new HttpEntity<>(headers(credentials)),
            JsonNode.class);
    JsonNode body = response.getBody();
    if (body == null || !body.isObject()) {
      return new DBRepoResourceMetadataDTO(resourceId, resourceType, resourceId, "", List.of());
    }
    return new DBRepoResourceMetadataDTO(
        StringUtils.defaultIfBlank(text(body, "id"), resourceId),
        resourceType,
        StringUtils.defaultIfBlank(text(body, "name"), resourceId),
        text(body, "query"),
        columns(body));
  }

  public DBRepoRowPageDTO getResourceRows(
      String baseUrl,
      String databaseId,
      String resourceType,
      String resourceId,
      int page,
      int size,
      DBRepoCredentials credentials) {
    validateRowsResourceType(resourceType);
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    String dataUrl =
        resourceUrl(normalizedBaseUrl, databaseId, resourceType, resourceId)
            + "/data?page="
            + page
            + "&size="
            + size;
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            dataUrl, HttpMethod.GET, new HttpEntity<>(headers(credentials)), JsonNode.class);
    return new DBRepoRowPageDTO(
        rows(response.getBody()), page, size, countRows(dataUrl, credentials).orElse(null));
  }

  private List<DBRepoLinkedResourceDTO> listResourcesAllowingFailure(
      String normalizedBaseUrl,
      String encodedDatabaseId,
      String databaseId,
      String type,
      DBRepoCredentials credentials,
      List<String> failedTypes) {
    try {
      return listResourcesAt(
          normalizedBaseUrl + "/api/v1/database/" + encodedDatabaseId + "/" + type,
          normalizedBaseUrl,
          encodedDatabaseId,
          type,
          credentials);
    } catch (RestClientException e) {
      log.warn("Could not load DBRepo {} resources for database {}", type, databaseId, e);
      failedTypes.add(type);
      return Collections.emptyList();
    }
  }

  private Optional<Long> countRows(String dataUrl, DBRepoCredentials credentials) {
    try {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              dataUrl, HttpMethod.HEAD, new HttpEntity<>(headers(credentials)), Void.class);
      return Optional.ofNullable(response.getHeaders().getFirst("X-Count")).map(Long::valueOf);
    } catch (RuntimeException e) {
      log.warn("Could not count DBRepo rows at {}", dataUrl, e);
      return Optional.empty();
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

  private List<DBRepoLinkedResourceDTO> listResourcesAt(
      String url,
      String normalizedBaseUrl,
      String encodedDatabaseId,
      String type,
      DBRepoCredentials credentials) {
    ResponseEntity<JsonNode> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers(credentials)), JsonNode.class);
    JsonNode body = response.getBody();
    if (body == null || !body.isArray()) {
      return Collections.emptyList();
    }
    List<DBRepoLinkedResourceDTO> resources = new ArrayList<>();
    for (JsonNode node : body) {
      String id = text(node, "id");
      if (StringUtils.isBlank(id)) {
        continue;
      }
      String label = labelFor(type, node, id);
      resources.add(
          new DBRepoLinkedResourceDTO(
              id,
              type,
              label,
              VIEW_TYPE.equals(type) ? text(node, "query") : "",
              normalizedBaseUrl
                  + "/database/"
                  + encodedDatabaseId
                  + "/"
                  + type
                  + "/"
                  + encodePathSegment(id)));
    }
    return resources;
  }

  private String labelFor(String type, JsonNode node, String id) {
    if (SUBSET_TYPE.equals(type)) {
      return StringUtils.defaultIfBlank(text(node, "query_normalized"), id);
    }
    return StringUtils.defaultIfBlank(text(node, "name"), id);
  }

  private List<DBRepoColumnDTO> columns(JsonNode resource) {
    JsonNode columns = firstArray(resource.get("columns"), resource.at("/schema/columns"));
    if (columns == null) {
      return List.of();
    }
    List<JsonNode> nodes = new ArrayList<>();
    columns.forEach(nodes::add);
    nodes.sort(
        Comparator.comparingInt(
            column -> column.has("ord") ? column.get("ord").asInt() : Integer.MAX_VALUE));
    return nodes.stream()
        .map(
            column ->
                new DBRepoColumnDTO(
                    text(column, "id"),
                    StringUtils.defaultIfBlank(text(column, "name"), text(column, "internal_name")),
                    text(column, "internal_name"),
                    text(column, "type"),
                    column.has("size") && !column.get("size").isNull()
                        ? column.get("size").asInt()
                        : null))
        .toList();
  }

  private JsonNode firstArray(JsonNode... candidates) {
    for (JsonNode candidate : candidates) {
      if (candidate != null && candidate.isArray()) {
        return candidate;
      }
    }
    return null;
  }

  private List<Map<String, Object>> rows(JsonNode body) {
    JsonNode rowNodes =
        body != null && body.isObject() && body.get("data") != null ? body.get("data") : body;
    if (rowNodes == null || !rowNodes.isArray()) {
      return List.of();
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (JsonNode row : rowNodes) {
      if (row.isObject()) {
        rows.add(
            objectMapper.convertValue(row, new TypeReference<LinkedHashMap<String, Object>>() {}));
      }
    }
    return rows;
  }

  private String resourceUrl(
      String normalizedBaseUrl, String databaseId, String resourceType, String resourceId) {
    return normalizedBaseUrl
        + "/api/v1/database/"
        + encodePathSegment(databaseId)
        + "/"
        + resourceType
        + "/"
        + encodePathSegment(resourceId);
  }

  private void validateRowsResourceType(String resourceType) {
    if (!TABLE_TYPE.equals(resourceType) && !VIEW_TYPE.equals(resourceType)) {
      throw new IllegalArgumentException(
          "DBRepo row insertion is only supported for tables and views.");
    }
  }

  private HttpHeaders headers(DBRepoCredentials credentials) {
    return headers(credentials, MediaType.APPLICATION_JSON);
  }

  private HttpHeaders headers(DBRepoCredentials credentials, MediaType accept) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(accept));
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

  private String encodePathSegment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }
}

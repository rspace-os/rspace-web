package com.researchspace.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class BioPortalOntologiesClient {

  private static final int SEARCH_PAGE_SIZE = 20;
  private static final int CACHE_MAX_SIZE = 200;
  private static final int CACHE_TTL_MINUTES = 10;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  private static final String ALLOWED_API_SCHEME = "https";
  private static final String ALLOWED_API_HOST = "data.bioontology.org";

  // The only origin ever sent to RestTemplate, never the configured value itself.
  private static final URI CANONICAL_API_ORIGIN =
      UriComponentsBuilder.newInstance()
          .scheme(ALLOWED_API_SCHEME)
          .host(ALLOWED_API_HOST)
          .build()
          .toUri();

  @Getter
  @Value("${bioportal.base.url}")
  private String bioportalBaseUrl;

  // Validated as an allowlist; requests use CANONICAL_API_ORIGIN.
  @Value("${bioportal.api.base.url}")
  private String bioportalApiBaseUrl;

  @Value("${bioportal.api.key:}")
  private String bioportalApiKey;

  private RestTemplate restTemplate = new RestTemplate(timeoutBoundedRequestFactory());

  private static SimpleClientHttpRequestFactory timeoutBoundedRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  private final AtomicBoolean missingApiKeyLogged = new AtomicBoolean(false);

  private volatile ValidatedApiBaseUri cachedValidation;

  private record ValidatedApiBaseUri(String candidate, URI result) {}

  private final Cache<String, List<BioPortalSearchResult>> searchCache =
      CacheBuilder.newBuilder()
          .maximumSize(CACHE_MAX_SIZE)
          .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
          .build();

  public List<BioPortalSearchResult> search(String searchTerm) {
    if (StringUtils.isBlank(bioportalApiKey)) {
      if (missingApiKeyLogged.compareAndSet(false, true)) {
        log.warn("BioPortal API key is not configured; BioPortal suggestions are unavailable");
      }
      return Collections.emptyList();
    }
    URI validatedApiBaseUri = validateApiBaseUri(bioportalApiBaseUrl);
    if (validatedApiBaseUri == null) {
      return Collections.emptyList();
    }
    String cacheKey = searchTerm.toLowerCase(Locale.ROOT);
    List<BioPortalSearchResult> cached = searchCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }
    List<BioPortalSearchResult> results =
        Collections.unmodifiableList(doSearch(validatedApiBaseUri, searchTerm));
    searchCache.put(cacheKey, results);
    return results;
  }

  private URI validateApiBaseUri(String candidate) {
    ValidatedApiBaseUri cached = cachedValidation;
    if (cached != null && Objects.equals(cached.candidate(), candidate)) {
      return cached.result();
    }
    URI result = computeValidatedApiBaseUri(candidate);
    cachedValidation = new ValidatedApiBaseUri(candidate, result);
    return result;
  }

  // Check the configured value, then discard it in favor of the fixed origin.
  private static URI computeValidatedApiBaseUri(String candidate) {
    if (StringUtils.isBlank(candidate)) {
      log.warn("BioPortal API base URL is not configured; BioPortal suggestions are unavailable");
      return null;
    }
    URI parsed;
    try {
      parsed = new URI(candidate);
    } catch (URISyntaxException e) {
      log.warn(
          "BioPortal API base URL [{}] is not a valid URI ({}); BioPortal suggestions are"
              + " unavailable",
          candidate,
          e.getMessage());
      return null;
    }
    if (!ALLOWED_API_SCHEME.equalsIgnoreCase(parsed.getScheme())
        || !ALLOWED_API_HOST.equalsIgnoreCase(parsed.getHost())) {
      log.warn(
          "BioPortal API base URL [{}] does not match the required https://{} origin; BioPortal"
              + " suggestions are unavailable",
          candidate,
          ALLOWED_API_HOST);
      return null;
    }
    return CANONICAL_API_ORIGIN;
  }

  private List<BioPortalSearchResult> doSearch(URI validatedApiBaseUri, String searchTerm) {
    URI uri =
        UriComponentsBuilder.fromUri(validatedApiBaseUri)
            .replacePath("/search")
            .queryParam("q", searchTerm)
            .queryParam("suggest", "true")
            .queryParam("pagesize", SEARCH_PAGE_SIZE)
            .build()
            .encode()
            .toUri();
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "apikey token=" + bioportalApiKey);
    try {
      BioPortalSearchResponse response =
          restTemplate
              .exchange(
                  uri, HttpMethod.GET, new HttpEntity<>(headers), BioPortalSearchResponse.class)
              .getBody();
      return response == null || response.getCollection() == null
          ? Collections.emptyList()
          : response.getCollection();
    } catch (RestClientException e) {
      log.warn("BioPortal search request failed: provider=BioPortal uri={}", uri, e);
      throw e;
    }
  }
}

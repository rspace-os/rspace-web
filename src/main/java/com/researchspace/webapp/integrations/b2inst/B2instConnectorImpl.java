package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.request.B2instReviewReceiver;
import com.researchspace.b2inst.model.request.B2instReviewRequest;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * {@link B2instConnector} implementation over Spring {@link RestTemplate}. Reads its configuration
 * from the {@code pidinst.b2inst.*} system properties (B2INST reuses the IdentifierSettings shape:
 * {@code username} holds the community id and {@code password} holds the bearer token).
 */
@Slf4j
public class B2instConnectorImpl implements B2instConnector {

  private static final String COMMUNITY_SUBMISSION = "community-submission";

  /*
   * Every B2INST call runs inside an open transaction: InventoryIdentifierApiManagerImpl matches the
   * *Manager pointcut in applicationContext-service.xml, so registering, publishing and deleting each
   * hold a Hibernate session and its pooled JDBC connection for the whole exchange. A bare
   * RestTemplate uses SimpleClientHttpRequestFactory, whose HttpURLConnection defaults to waiting
   * forever, so one unresponsive B2INST host would pin a connection indefinitely and enough
   * concurrent registrations would exhaust the pool. These bounds put a ceiling on that.
   *
   * Read is the more generous of the two: publishing is curator-gated and Invenio can be slow to
   * answer the review actions, whereas a connect that has not completed in 10s is not going to.
   */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  @Autowired private SystemPropertyManager sysPropertyMgr;

  private boolean enabled;
  private String serverUrl;
  private String communityId;
  private String token;
  private RestTemplate restTemplate = new RestTemplate(timeoutBoundedRequestFactory());

  @PostConstruct
  @Override
  public void reloadClient() {
    Map<String, SystemPropertyValue> props = sysPropertyMgr.getAllSysadminPropertiesAsMap();
    enabled = Boolean.parseBoolean(getProperty(props, SystemPropertyName.PIDINST_B2INST_ENABLED));
    serverUrl =
        StringUtils.removeEnd(
            getProperty(props, SystemPropertyName.PIDINST_B2INST_SERVER_URL), "/");
    communityId = getProperty(props, SystemPropertyName.PIDINST_B2INST_COMMUNITY_ID);
    token = getProperty(props, SystemPropertyName.PIDINST_B2INST_TOKEN);
    restTemplate = buildRestTemplate(token);
    log.info("Reloaded B2INST client for server {} (enabled={})", serverUrl, enabled);
  }

  /** See {@link #CONNECT_TIMEOUT}: never build a B2INST client without these bounds. */
  private static SimpleClientHttpRequestFactory timeoutBoundedRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  private RestTemplate buildRestTemplate(String bearerToken) {
    RestTemplate rt = new RestTemplate(timeoutBoundedRequestFactory());
    rt.getInterceptors()
        .add(
            (request, body, execution) -> {
              if (StringUtils.isNotBlank(bearerToken)) {
                request.getHeaders().setBearerAuth(bearerToken);
              }
              request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
              return execution.execute(request, body);
            });
    return rt;
  }

  @Override
  public boolean isConfiguredAndEnabled() {
    // Draft registration (the supported flow) needs only the server URL and token. The community
    // id is required only for the curator-gated publish, which publishDoi() validates separately,
    // so it is deliberately not part of this readiness gate.
    return enabled && StringUtils.isNotBlank(serverUrl) && StringUtils.isNotBlank(token);
  }

  @Override
  public B2instDraftRecord registerDoi(B2instDoi doi) {
    try {
      return restTemplate.postForObject(
          apiBase() + "/records", new HttpEntity<>(doi), B2instDraftRecord.class);
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error creating B2INST draft record: " + reason, reason, e);
    }
  }

  @Override
  public boolean deleteDoi(String rid) {
    try {
      restTemplate.delete(apiBase() + "/records/" + rid + "/draft");
      return true;
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error deleting B2INST draft record " + rid + ": " + reason, reason, e);
    }
  }

  @Override
  public B2instRequestResponse publishDoi(String rid) {
    if (StringUtils.isBlank(communityId)) {
      throw new B2instConnectionException(
          "No B2INST community configured (pidinst.b2inst.community.id); cannot submit " + rid,
          // deliberately omits the property name: this reason is shown to an ordinary user
          "B2INST is not fully configured for publishing, because no community has been set.");
    }
    try {
      B2instRequestResponse alreadySubmitted = openReviewOf(rid);
      if (alreadySubmitted != null) {
        return alreadySubmitted;
      }
      B2instReviewRequest review =
          new B2instReviewRequest(new B2instReviewReceiver(communityId), COMMUNITY_SUBMISSION);
      B2instRequestResponse created =
          restTemplate
              .exchange(
                  apiBase() + "/records/" + rid + "/draft/review",
                  HttpMethod.PUT,
                  new HttpEntity<>(review),
                  B2instRequestResponse.class)
              .getBody();
      String submitUrl = submitUrlOf(created);
      if (submitUrl == null) {
        throw new B2instConnectionException(
            "B2INST review did not return a submit action for record " + rid,
            "B2INST did not offer a submit action for this record.");
      }
      return restTemplate.postForObject(submitUrl, emptyJsonBody(), B2instRequestResponse.class);
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error submitting B2INST record " + rid + " for community review: " + reason, reason, e);
    }
  }

  @Override
  public B2instRequestResponse retractDoi(String rid) {
    throw new UnsupportedOperationException(
        "B2INST does not support retracting a published instrument PID (record " + rid + ")");
  }

  @Override
  public boolean testConnection() {
    try {
      ResponseEntity<String> response =
          restTemplate.getForEntity(apiBase() + "/communities", String.class);
      return response.getStatusCode().is2xxSuccessful();
    } catch (RestClientException e) {
      // redacted: in Spring 6 RestClientResponseException.getMessage() embeds the response body
      log.warn("B2INST connection test failed: {}", redactToken(e.getMessage()));
      return false;
    }
  }

  /**
   * Builds a human-readable reason for a failed B2INST call. When the server replied, prefers the
   * parsed field-validation errors, then the payload's top-level message, then the HTTP status;
   * without a response (transport error) falls back to the client exception message, or the
   * exception type when even that is blank.
   */
  private String describeFailure(RestClientException e) {
    /*
     * Redacted at a single exit, deliberately. This value is the exception's reason, and since the
     * reason/message split it is interpolated into user-facing localized text at three call sites, so
     * every branch below is a path to a user rather than only to the log. Redacting here instead of
     * at each return means a branch added later cannot skip it.
     */
    return redactToken(describeFailureText(e));
  }

  private String describeFailureText(RestClientException e) {
    if (e instanceof RestClientResponseException restError) {
      String body = restError.getResponseBodyAsString();
      String parsedDescription =
          JacksonUtil.fromJsonOpt(body, B2instErrorResponse.class)
              .map(B2instErrorResponse::describe)
              .orElse(null);
      if (parsedDescription != null) {
        return parsedDescription;
      }
      log.warn(
          "No usable failure reason in B2INST error response (HTTP {}): {}",
          restError.getRawStatusCode(),
          StringUtils.abbreviate(redactToken(body), 500));
      return "B2INST returned HTTP "
          + restError.getRawStatusCode()
          + " "
          + restError.getStatusText();
    }
    return StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
  }

  /**
   * The bearer token is the one secret we hold that a proxy error page could echo back. Apply this
   * to anything derived from a provider response before it is logged or shown to a user: a gateway
   * or WAF that reflects request headers into its error body puts the token inside text this class
   * otherwise passes straight through.
   */
  private String redactToken(String text) {
    if (text == null || StringUtils.isBlank(token)) {
      return text;
    }
    return text.replace(token, "***");
  }

  /**
   * The submit action takes no parameters, but the body cannot simply be omitted. {@code
   * SimpleClientHttpRequestFactory} enables output for a POST, and {@code HttpURLConnection} then
   * defaults the {@code Content-Type} to {@code application/x-www-form-urlencoded} when none is
   * set. Invenio's action endpoints answer that with HTTP 415, "Invalid 'Content-Type' header.
   * Expected one of: application/json" (verified against b2inst-test.gwdg.de, July 2026). Sending
   * an explicit empty JSON object with the correct content type is what the endpoint expects.
   */
  private HttpEntity<String> emptyJsonBody() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>("{}", headers);
  }

  @Override
  public Optional<B2instRequestResponse> getReviewOf(String rid) {
    try {
      return Optional.ofNullable(
          restTemplate.getForObject(
              apiBase() + "/records/" + rid + "/draft/review", B2instRequestResponse.class));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error reading B2INST review of record " + rid + ": " + reason, reason, e);
    }
  }

  @Override
  public Optional<B2instDraftRecord> getPublishedRecord(String rid) {
    return getRecord(apiBase() + "/records/" + rid, rid);
  }

  @Override
  public Optional<B2instDraftRecord> getDraftRecord(String rid) {
    return getRecord(apiBase() + "/records/" + rid + "/draft", rid);
  }

  private Optional<B2instDraftRecord> getRecord(String url, String rid) {
    try {
      return Optional.ofNullable(restTemplate.getForObject(url, B2instDraftRecord.class));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error reading B2INST record " + rid + ": " + reason, reason, e);
    }
  }

  /**
   * The record's review request when one is already open, otherwise {@code null}.
   *
   * <p>Makes publishing idempotent. Submitting is two calls (PUT the review, POST its submit
   * action), so a response lost between them leaves B2INST holding a submitted review while RSpace,
   * having thrown, still records the identifier as a draft. The user's natural retry then re-PUTs,
   * and B2INST answers {@code 400 "An open review cannot be deleted."} for good, with no way out
   * through the UI. Verified against b2inst-test.gwdg.de, July 2026.
   *
   * <p>Only an OPEN review short-circuits. A closed one (declined, cancelled, expired) reports
   * {@code is_open: false} and must fall through to a fresh PUT, or a rejected record could never
   * be resubmitted. An unsubmitted review also reports {@code is_open: false}, and re-PUTting that
   * is both accepted by B2INST and necessary, since it returns the submit link the caller needs.
   */
  private B2instRequestResponse openReviewOf(String rid) {
    return getReviewOf(rid).filter(review -> Boolean.TRUE.equals(review.getIsOpen())).orElse(null);
  }

  private String submitUrlOf(B2instRequestResponse created) {
    if (created == null || created.getLinks() == null || created.getLinks().getActions() == null) {
      return null;
    }
    return created.getLinks().getActions().getSubmit();
  }

  private String apiBase() {
    return serverUrl + "/api";
  }

  private String getProperty(Map<String, SystemPropertyValue> props, SystemPropertyName name) {
    SystemPropertyValue value = props.get(name.getPropertyName());
    return value == null ? null : value.getValue();
  }

  /** Visible for testing: lets a MockRestServiceServer bind to the currently configured client. */
  RestTemplate getRestTemplate() {
    return restTemplate;
  }
}

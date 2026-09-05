package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.request.B2instReviewReceiver;
import com.researchspace.b2inst.model.request.B2instReviewRequest;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
import org.springframework.web.util.UriComponentsBuilder;

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
  @Autowired private MessageSourceUtils messages;

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
          "Error creating B2INST draft record: " + developerDetail(e), reason, e);
    }
  }

  @Override
  public B2instDraftRecord updateDraftDoi(String rid, B2instDoi doi) {
    try {
      return restTemplate
          .exchange(
              recordUrl(rid, "draft"),
              HttpMethod.PUT,
              new HttpEntity<>(doi),
              B2instDraftRecord.class)
          .getBody();
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error updating B2INST draft record " + rid + ": " + developerDetail(e), reason, e);
    }
  }

  @Override
  public boolean deleteDoi(String rid) {
    try {
      restTemplate.delete(recordUrl(rid, "draft"));
      return true;
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error deleting B2INST draft record " + rid + ": " + developerDetail(e), reason, e);
    }
  }

  @Override
  public B2instRequestResponse publishDoi(String rid) {
    if (StringUtils.isBlank(communityId)) {
      throw new B2instConnectionException(
          "No B2INST community configured (pidinst.b2inst.community.id); cannot submit " + rid,
          // deliberately omits the property name: this reason is shown to an ordinary user
          messages.getMessage("errors.inventory.identifier.b2instNoCommunity"));
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
                  recordUrl(rid, "draft", "review"),
                  HttpMethod.PUT,
                  new HttpEntity<>(review),
                  B2instRequestResponse.class)
              .getBody();
      String submitUrl = submitUrlOf(created);
      if (submitUrl == null) {
        throw new B2instConnectionException(
            "B2INST review did not return a submit action for record " + rid,
            messages.getMessage("errors.inventory.identifier.b2instNoSubmitAction"));
      }
      return restTemplate.postForObject(submitUrl, emptyJsonBody(), B2instRequestResponse.class);
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error submitting B2INST record " + rid + " for community review: " + developerDetail(e),
          reason,
          e);
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
  /**
   * The user-safe reason. Redacted at a single exit, deliberately: this is interpolated into
   * user-facing localized text and into the audit trail at five call sites, so every branch below
   * is a path to a user rather than only to the log, and redacting here instead of at each return
   * means a branch added later cannot skip it.
   */
  private String describeFailure(RestClientException e) {
    if (e instanceof RestClientResponseException restError) {
      String parsed = providerDescription(restError);
      if (parsed != null) {
        return redactToken(parsed);
      }
      log.warn(
          "No usable failure reason in B2INST error response (HTTP {}): {}",
          restError.getRawStatusCode(),
          StringUtils.abbreviate(redactToken(restError.getResponseBodyAsString()), 500));
      return messages.getMessage(
          "errors.inventory.identifier.b2instHttpStatus",
          new Object[] {restError.getRawStatusCode(), restError.getStatusText()});
    }
    log.warn(
        "Could not reach B2INST: {}", redactToken(StringUtils.defaultString(e.getMessage())), e);
    return messages.getMessage("errors.inventory.identifier.b2instUnreachable");
  }

  /**
   * The developer-facing detail, for the exception message and so the log.
   *
   * <p>Same as the reason while B2INST explained itself: its own parsed description is the most
   * readable thing for both audiences, and Spring's own message for an HTTP failure puts the whole
   * response body in the sentence.
   *
   * <p>It diverges wherever the sentence is RSpace's own. A developer wants fixed English and, for
   * a transport failure, Spring's message naming the request URL and the cause; a user must be
   * shown neither, and gets localized text instead. Redacted all the same.
   */
  private String developerDetail(RestClientException e) {
    if (e instanceof RestClientResponseException restError) {
      String parsed = providerDescription(restError);
      // Deliberately English and unlocalized, unlike the reason: this goes to the log, where a
      // translated sentence would be worse than a fixed one. The status is kept because it is the
      // only thing left when the body explains nothing (404 the record is gone, 403 credentials).
      return parsed != null
          ? redactToken(parsed)
          : "B2INST returned HTTP "
              + restError.getRawStatusCode()
              + " "
              + restError.getStatusText();
    }
    return redactToken(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
  }

  /** B2INST's own description of the failure, or null when its response body carried none. */
  private String providerDescription(RestClientResponseException restError) {
    return JacksonUtil.fromJsonOpt(restError.getResponseBodyAsString(), B2instErrorResponse.class)
        .map(B2instErrorResponse::describe)
        .orElse(null);
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

  /**
   * Pattern a record id (RID) must match before it is put into a URL. Both observed B2INST/Invenio
   * forms fit it (for example {@code k2j9p-7yh21}, {@code abcde-12345}).
   */
  private static final Pattern VALID_RID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  /**
   * A record URL with the RID as one escaped path segment.
   *
   * <p>The RID is {@code DigitalObjectIdentifier.identifier}, which {@code
   * ApiInventoryDOI.applyChangesToDatabaseDOI} writes from a record update, so it is not trusted
   * input. Concatenated raw, a crafted value could point these calls - and the bearer token they
   * carry - at other paths on the provider host, with parts of the answer reaching the user through
   * the identifier's state, provider URL and failure message.
   *
   * <p>Guarded at both ends: the RID is checked against {@link #VALID_RID} first, which rules out
   * the {@code .} and {@code ..} segments a URL normaliser could still collapse after escaping, and
   * {@code pathSegment} then escapes anything else so a value can only ever be one segment. A
   * rejected RID throws rather than being cleaned up: RSpace did not mint it, so there is no
   * correct request to send.
   */
  private String recordUrl(String rid, String... trailingSegments) {
    if (rid == null || !VALID_RID.matcher(rid).matches()) {
      throw new B2instConnectionException(
          "Refusing to call B2INST with an unexpected record id", "invalid record id", null);
    }
    return UriComponentsBuilder.fromUriString(apiBase())
        .pathSegment("records")
        .pathSegment(rid)
        .pathSegment(trailingSegments)
        .build()
        .encode()
        .toUriString();
  }

  @Override
  public Optional<B2instRequestResponse> getReviewOf(String rid) {
    try {
      return Optional.ofNullable(
          restTemplate.getForObject(
              recordUrl(rid, "draft", "review"), B2instRequestResponse.class));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error reading B2INST review of record " + rid + ": " + developerDetail(e), reason, e);
    }
  }

  @Override
  public Optional<B2instDraftRecord> getPublishedRecord(String rid) {
    return getRecord(recordUrl(rid), rid);
  }

  @Override
  public Optional<B2instDraftRecord> getDraftRecord(String rid) {
    return getRecord(recordUrl(rid, "draft"), rid);
  }

  private Optional<B2instDraftRecord> getRecord(String url, String rid) {
    try {
      return Optional.ofNullable(restTemplate.getForObject(url, B2instDraftRecord.class));
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (RestClientException e) {
      String reason = describeFailure(e);
      throw new B2instConnectionException(
          "Error reading B2INST record " + rid + ": " + developerDetail(e), reason, e);
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

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
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  @Autowired private SystemPropertyManager sysPropertyMgr;

  private boolean enabled;
  private String serverUrl;
  private String communityId;
  private String token;
  private RestTemplate restTemplate = new RestTemplate();

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

  private RestTemplate buildRestTemplate(String bearerToken) {
    RestTemplate rt = new RestTemplate();
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
      throw new B2instConnectionException(
          "Error creating B2INST draft record: " + describeFailure(e), e);
    }
  }

  @Override
  public boolean deleteDoi(String rid) {
    try {
      restTemplate.delete(apiBase() + "/records/" + rid + "/draft");
      return true;
    } catch (RestClientException e) {
      throw new B2instConnectionException(
          "Error deleting B2INST draft record " + rid + ": " + describeFailure(e), e);
    }
  }

  @Override
  public B2instRequestResponse publishDoi(String rid) {
    if (StringUtils.isBlank(communityId)) {
      throw new B2instConnectionException(
          "No B2INST community configured (pidinst.b2inst.community.id); cannot submit " + rid);
    }
    try {
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
            "B2INST review did not return a submit action for record " + rid);
      }
      return restTemplate.postForObject(submitUrl, HttpEntity.EMPTY, B2instRequestResponse.class);
    } catch (RestClientException e) {
      throw new B2instConnectionException(
          "Error submitting B2INST record " + rid + " for community review: " + describeFailure(e),
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
      log.warn("B2INST connection test failed: {}", e.getMessage());
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

  /** The bearer token is the one secret we hold that a proxy error page could echo back. */
  private String redactToken(String body) {
    return StringUtils.isBlank(token) ? body : body.replace(token, "***");
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

package com.researchspace.webapp.integrations.orcid;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/** The class wraps real calls to ORCID, to facilitate testing. */
public class OrcidConnector {

  private Logger log = LoggerFactory.getLogger(OrcidConnector.class);

  private String clientId;
  private String clientSecret;

  public OrcidConnector(String clientId, String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public ResponseEntity<Map> getOrcidIdForAuthorizationCode(String code) {

    String uri = "https://orcid.org/oauth/token";
    String data =
        "client_id="
            + clientId
            + "&client_secret="
            + clientSecret
            + "&grant_type=authorization_code&code="
            + code;

    log.warn("connecting to orcid uri: " + uri + " with data: " + data);

    RestTemplate template = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept", "application/json");
    headers.add("Content-Type", "application/x-www-form-urlencoded");

    HttpEntity<?> entity = new HttpEntity<String>(data, headers);
    ResponseEntity<Map> response = template.exchange(uri, HttpMethod.POST, entity, Map.class);
    return response;
  }
}

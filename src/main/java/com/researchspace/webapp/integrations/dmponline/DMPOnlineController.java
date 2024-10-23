package com.researchspace.webapp.integrations.dmponline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Controller
@RequestMapping("/apps/dmponline")
public class DMPOnlineController {
  @Autowired private MediaManager mediaManager;
  @Autowired private UserManager userManager;
  @Autowired private DMPManager dmpManager;

  @Value("${dmponline.api.url}")
  private String dmpOnlineApiUrl;

  @Value("${dmponline.credentials.email}")
  private String dmpOnlineCredentialsEmail;

  @Value("${dmponline.credentials.authorizationCode}")
  private String dmpOnlineCredentialsAuthorizationCode;

  private final RestTemplate restTemplate;

  public DMPOnlineController() {
    this.restTemplate = new RestTemplate();
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listDMPs(
      @RequestParam(name = "page") String page, @RequestParam(name = "per_page") String per_page) {
    try {
      String token = getToken();
      if (token == null) {
        return getErrorResponseOnNullToken();
      }
      return new AjaxReturnObject(
          restTemplate
              .exchange(
                  UriComponentsBuilder.fromUriString(dmpOnlineApiUrl + "/plans")
                      .queryParam("page", page)
                      .queryParam("per_page", per_page)
                      .build()
                      .toUri(),
                  HttpMethod.GET,
                  new HttpEntity<>(getHttpHeaders(token)),
                  JsonNode.class)
              .getBody(),
          null);
    } catch (HttpClientErrorException | URISyntaxException | MalformedURLException e) {
      log.warn("error connecting to DMPonline", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Error connecting to DMPonline."));
    }
  }

  @PostMapping("/importPlan")
  @ResponseBody
  public AjaxReturnObject<JsonNode> importDmp(
      @RequestParam(name = "id") String id, // url to dmp
      @RequestParam(name = "filename") String filename)
      throws URISyntaxException, IOException {

    User user = userManager.getAuthenticatedUserInSession();
    String token = getToken();
    if (token == null) {
      return getErrorResponseOnNullToken();
    }

    var dmps =
        restTemplate
            .exchange(
                new URL(id).toURI(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(token)),
                JsonNode.class)
            .getBody()
            .get("items")
            .elements();
    var dmpWrappedObject = dmps.next();
    var dmpObject = dmpWrappedObject.get("dmp");

    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(dmpObject);
    InputStream is = new ByteArrayInputStream(json.getBytes());
    var file = mediaManager.saveNewDMP(filename, is, user, null);

    DMP dmp = new DMP(id, filename);
    var dmpUser = dmpManager.findByDmpId(dmp.getDmpId(), user).orElse(new DMPUser(user, dmp));
    if (file != null) {
      dmpUser.setDmpDownloadPdf(file);
    } else {
      log.warn("Unexpected null DMP PDF - did download work?");
    }
    dmpManager.save(dmpUser);

    return new AjaxReturnObject(dmpObject, null);
  }

  private String getToken() throws URISyntaxException, MalformedURLException {
    if (StringUtils.isEmpty(dmpOnlineCredentialsEmail)
        || StringUtils.isEmpty(dmpOnlineCredentialsAuthorizationCode)) {
      return null;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept", "application/x-www-form-urlencoded;charset=UTF-8");
    headers.add("Content-Type", "application/json");
    HttpEntity<?> entity =
        new HttpEntity<>(
            "{\"grant_type\":\"authorization_code\","
                + "\"email\":\""
                + dmpOnlineCredentialsEmail
                + "\","
                + "\"code\":\""
                + dmpOnlineCredentialsAuthorizationCode
                + "\"}",
            headers);
    return restTemplate
        .exchange(
            new URL(dmpOnlineApiUrl + "/authenticate").toURI(),
            HttpMethod.POST,
            entity,
            JsonNode.class)
        .getBody()
        .get("access_token")
        .textValue();
  }

  private HttpHeaders getHttpHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.add("Authorization", String.format("Bearer %s", accessToken));
    return headers;
  }

  private static AjaxReturnObject<JsonNode> getErrorResponseOnNullToken() {
    return new AjaxReturnObject<>(
        null,
        ErrorList.of(
            "Couldn't generate DMPonline token. Is RSpace connection with DMPonline configured"
                + " properly?"));
  }
}

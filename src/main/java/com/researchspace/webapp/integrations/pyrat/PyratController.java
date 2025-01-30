package com.researchspace.webapp.integrations.pyrat;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.service.UserAppConfigManager;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Controller
@RequestMapping("/apps/pyrat")
public class PyratController {

  private @Autowired PyratClient client;
  private @Autowired UserAppConfigManager userAppConfigMgr;

  public PyratController() {}

  @GetMapping("/version")
  @ResponseBody
  public JsonNode version(@RequestParam() String serverAlias)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return client.version(serverAlias);
  }

  @GetMapping("/locations")
  @ResponseBody
  public ResponseEntity<JsonNode> locations(
      @RequestParam() MultiValueMap<String, String> queryParams, @RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    try {
      return ResponseEntity.ok().body(client.locations(serverAlias, queryParams));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    }
  }

  @GetMapping("/animals")
  public ResponseEntity<JsonNode> animals(
      @RequestParam() MultiValueMap<String, String> queryParams, @RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    try {
      var body = client.animals(serverAlias, queryParams);

      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.set("x-total-count", body.totalCount);

      return ResponseEntity.ok().headers(responseHeaders).body(body.payload);
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    }
  }

  @GetMapping("/pups")
  public ResponseEntity<JsonNode> pups(
      @RequestParam() MultiValueMap<String, String> queryParams, @RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    try {
      var body = client.pups(serverAlias, queryParams);

      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.set("x-total-count", body.totalCount);

      return ResponseEntity.ok().headers(responseHeaders).body(body.payload);
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    }
  }

  @GetMapping("/projects")
  @ResponseBody
  public ResponseEntity<JsonNode> projects(
      @RequestParam() MultiValueMap<String, String> queryParams, @RequestParam() String serverAlias)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    try {
      return ResponseEntity.ok().body(client.projects(serverAlias, queryParams));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    }
  }

  @GetMapping("/users")
  @ResponseBody
  public ResponseEntity<JsonNode> users(
      @RequestParam() MultiValueMap<String, String> queryParams, @RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    try {
      return ResponseEntity.ok().body(client.users(serverAlias, queryParams));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    }
  }

  @GetMapping("/licenses")
  @ResponseBody
  public ResponseEntity<JsonNode> licenses(
      @RequestParam() MultiValueMap<String, String> queryParams, @RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    try {
      return ResponseEntity.ok().body(client.licenses(serverAlias, queryParams));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    }
  }
}

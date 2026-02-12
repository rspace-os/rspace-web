package com.researchspace.webapp.integrations.dsw;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Controller
@RequestMapping("/apps/dsw")
public class DSWController {

  @Autowired private DSWClient client;
  @Autowired private UserManager userManager;
  @Autowired private UserAppConfigManager userAppConfigMgr;

  private static String MSG_PROJECT_ERROR = "Error retrieving DSW project";
  private static String MSG_PROJECTS_ERROR = "Error getting DSW projects";

  public DSWController() {}

  @GetMapping("/currentUser")
  @ResponseBody
  public ResponseEntity<JsonNode> currentUsers(@RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    // User user = userManager.getAuthenticatedUserInSession();
    User user = userManager.get(-12l);
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      return ResponseEntity.ok().body(client.currentUser(serverAlias, cfg.get()));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(null);
    }
  }

  @GetMapping("/availableDocs")
  @ResponseBody
  public ResponseEntity<JsonNode> availableDocs(@RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    // User user = userManager.getAuthenticatedUserInSession();
    User user = userManager.get(-12l);
    System.out.println("@@@ Using user: " + user.getUsername());
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      return ResponseEntity.ok().body(client.getDocsForCurrentUser(serverAlias, cfg.get()));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(null);
    }
  }

  @GetMapping("/getDocument")
  @ResponseBody
  public ResponseEntity<JsonNode> getDocument(
      @RequestParam() String serverAlias, @RequestParam() String documentUuid)
      throws URISyntaxException, MalformedURLException {
    // User user = userManager.getAuthenticatedUserInSession();
    User user = userManager.get(-12l);
    System.out.println("@@@ Using user: " + user.getUsername());
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      return ResponseEntity.ok().body(client.getDocumentURL(serverAlias, cfg.get(), documentUuid));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(null);
    }
  }

  @PostMapping("/importDSWFile")
  @ResponseBody
  public ResponseEntity<JsonNode> importDSWFile(
      @RequestParam() String serverAlias, @RequestParam() String documentUuid)
      throws URISyntaxException, MalformedURLException {
    // User user = userManager.getAuthenticatedUserInSession();
    User user = userManager.get(-12l);
    System.out.println("@@@ Using user to import: " + user.getUsername());
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      return ResponseEntity.ok().body(client.importDswFile(serverAlias, cfg.get(), documentUuid));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(null);
    }
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listDSWPlans(@RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    // User user = userManager.getAuthenticatedUserInSession();
    User user = userManager.get(-12l);
    System.out.println("@@@ Using user: " + user.getUsername());
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      JsonNode plans = client.getProjectsForCurrentUserJson(serverAlias, cfg.get());
      return new AjaxReturnObject<>(plans, null);
    } catch (Exception e) {
      log.warn(MSG_PROJECTS_ERROR, e);
      return new AjaxReturnObject<>(null, ErrorList.of(MSG_PROJECTS_ERROR));
    }
  }

  @PostMapping("/importPlan")
  @ResponseBody
  public AjaxReturnObject<JsonNode> importPlan(
      @RequestParam() String serverAlias, @RequestParam() String planUuid)
      throws URISyntaxException, MalformedURLException {
    // User user = userManager.getAuthenticatedUserInSession();
    User user = userManager.get(-12l);
    System.out.println("@@@ Using user to import: " + user.getUsername());
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      JsonNode plan = client.importPlan(serverAlias, cfg.get(), planUuid);
      return new AjaxReturnObject<>(plan, null);
    } catch (Exception e) {
      log.warn(MSG_PROJECT_ERROR, e);
      return new AjaxReturnObject<>(null, ErrorList.of(MSG_PROJECT_ERROR));
    }
  }
}

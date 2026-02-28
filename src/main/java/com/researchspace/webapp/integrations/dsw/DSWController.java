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
import com.researchspace.webapp.integrations.dsw.exception.DSWProjectRetrievalException;
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

  private final DSWClient dswClient;
  private final UserManager userManager;
  private final UserAppConfigManager userAppConfigMgr;

  private static String MSG_PROJECT_ERROR = "Error retrieving DSW project";
  private static String MSG_PROJECTS_ERROR = "Error getting DSW projects";

  @Autowired
  public DSWController(
      DSWClient dswClient, UserManager userManager, UserAppConfigManager userAppConfigManager) {
    this.dswClient = dswClient;
    this.userManager = userManager;
    this.userAppConfigMgr = userAppConfigManager;
  }

  @GetMapping("/currentUser")
  @ResponseBody
  public ResponseEntity<JsonNode> currentUsers(@RequestParam() String serverAlias)
      throws URISyntaxException, MalformedURLException {
    User user = userManager.getAuthenticatedUserInSession();
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      return ResponseEntity.ok().body(dswClient.currentUser(serverAlias, cfg.get()));
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
    User user = userManager.getAuthenticatedUserInSession();
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      JsonNode plans = dswClient.getProjectsForCurrentUserJson(serverAlias, cfg.get());
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
    User user = userManager.getAuthenticatedUserInSession();
    UserAppConfig uacfg = userAppConfigMgr.getByAppName("app.dsw", user);
    // TODO: Fix this when we have multiple instances properly supported.  (Although we
    //  might not need to change anything if there's always one result.)
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(
            uacfg.getAppConfigElementSets().iterator().next().getId());
    try {
      JsonNode plan = dswClient.importPlan(serverAlias, cfg.get(), planUuid);
      return new AjaxReturnObject<>(plan, null);
    } catch (DSWProjectRetrievalException e) {
      log.warn(MSG_PROJECT_ERROR, e);
      return new AjaxReturnObject<>(null, ErrorList.of(e.getMessage()));
    }
  }
}

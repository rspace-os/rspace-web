package com.researchspace.webapp.integrations.dsw;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserManager;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
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

  public DSWController() {}

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
      return ResponseEntity.ok().body(client.currentUser(serverAlias, cfg.get()));
    } catch (HttpClientErrorException e) {
      JsonNode err = JacksonUtil.fromJson(e.getResponseBodyAsString(), JsonNode.class);
      return ResponseEntity.status(e.getStatusCode()).body(err);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(null);
    }
  }
}

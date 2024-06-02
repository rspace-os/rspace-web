package com.researchspace.webapp.integrations.orcid;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Class responsible for handling connection between RSpace and Orcid API */
@Controller
@RequestMapping("/orcid")
public class OrcidController {

  private Logger log = LoggerFactory.getLogger(OrcidController.class);

  @Value("${orcid.client.id}")
  private String clientId;

  @Value("${orcid.client.secret}")
  private String clientSecret;

  @Autowired private IntegrationsHandler integrationsHandler;

  @Autowired protected UserManager userManager;

  private OrcidConnector connector;

  @GetMapping("/redirect_uri")
  public String onAuthorization(
      @RequestParam Map<String, String> params, Model model, Principal p) {
    if (params.containsKey("error")) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("Orcid")
              .errorMsg(params.get("error"))
              .errorDetails(params.get("error_description"))
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }

    if (params.containsKey("code")) {
      String code = params.get("code");
      log.warn("received code from Orcid: " + code);

      ResponseEntity<Map> restResponse = getOrcidConnector().getOrcidIdForAuthorizationCode(code);
      if (!restResponse.getStatusCode().is2xxSuccessful()) {
        HttpStatus statusCode = restResponse.getStatusCode();
        log.warn("Status code response was not successful : {}", statusCode);
        OauthAuthorizationError error =
            OauthAuthorizationError.builder()
                .appName("Orcid")
                .errorMsg(statusCode + "")
                .errorDetails(params.get("error_description"))
                .build();
        model.addAttribute("error", error);
        return "connect/authorizationError";
      }

      String orcidId = (String) restResponse.getBody().get("orcid");
      model.addAttribute("orcid_id", orcidId);
      log.warn("orcid id in response: " + orcidId);

      User user = userManager.getUserByUsername(p.getName());
      if (orcidId != null) {
        updateUserOrcidApp(user, orcidId);
        model.addAttribute("orcid_options_id", getOrcidOptionsIdForUser(user));
      }
    }

    return "connect/orcid/connected";
  }

  protected void updateUserOrcidApp(User user, String orcidId) {

    String currentId = getOrcidOptionsIdForUser(user);
    Long optionIdToSave = currentId == null ? null : Long.valueOf(currentId);

    Map<String, String> newOptions = new HashMap<>();
    newOptions.put("ORCID_ID", orcidId);
    integrationsHandler.saveAppOptions(optionIdToSave, newOptions, "ORCID", true, user);
  }

  private String getOrcidOptionsIdForUser(User user) {
    IntegrationInfo integration = integrationsHandler.getIntegration(user, "ORCID");
    return integration.retrieveFirstOptionsId();
  }

  private OrcidConnector getOrcidConnector() {
    if (connector == null) {
      connector = new OrcidConnector(clientId, clientSecret);
    }
    return connector;
  }
}

package com.researchspace.webapp.integrations.raid;

import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;

import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/apps/raid")
public class RaIDOAuthController extends BaseOAuth2Controller {

  @Setter(value = AccessLevel.PROTECTED) // test purposes
  @Autowired
  private RaIDServiceClientAdapter raidServiceClientAdapter;

  @PostMapping("/connect/{serverAlias}")
  public RedirectView connect(@PathVariable String serverAlias)
      throws MalformedURLException, URISyntaxException {
    return new RedirectView(raidServiceClientAdapter.performRedirectConnect(serverAlias));
  }

  @GetMapping("/callback")
  public String callback(@RequestParam Map<String, String> params, Model model, Principal principal)
      throws IOException, URISyntaxException, HttpClientErrorException {
    String redirectResult;
    OauthAuthorizationErrorBuilder error = OauthAuthorizationError.builder().appName("RaID");
    try {
      String serverAlias = params.get("state");
      raidServiceClientAdapter.performCreateAccessToken(
          principal.getName(), serverAlias, params.get("code"));

      log.info("Connected to {} RaID server for user {}", serverAlias, principal.getName());
      model.addAttribute("serverAlias", serverAlias);
      redirectResult = "connect/raid/connected";
    } catch (Exception ex) {
      log.error("Couldn't complete the token request on RaID", ex);
      error.errorMsg("Error during token creation: " + ex.getMessage());
      error.errorDetails(ex.getMessage());
      model.addAttribute("error", error.build());
      redirectResult = "connect/authorizationError";
    }
    return redirectResult;
  }

  @DeleteMapping("/connect/{serverAlias}")
  @ResponseStatus(HttpStatus.OK)
  public void disconnect(@PathVariable String serverAlias, Principal principal) {
    int deletedConnCount =
        userConnectionManager.deleteByUserAndProvider(
            principal.getName(), RAID_APP_NAME, serverAlias);
    log.info("Deleted {} RaID connection(s) for user {}", deletedConnCount, principal.getName());
  }

  @PostMapping("/test_connection/{serverAlias}")
  public Boolean isConnectionAlive(@PathVariable String serverAlias, Principal principal) {
    Boolean isConnectionAlive = Boolean.TRUE;
    try {
      isConnectionAlive =
          raidServiceClientAdapter.isRaidConnectionAlive(principal.getName(), serverAlias);
    } catch (Exception e) {
      log.error(
          "Couldn't perform test connection action on RaID. "
              + "The connection will be flagged as NOT ALIVE",
          e);
      isConnectionAlive = Boolean.FALSE;
    }
    return isConnectionAlive;
  }

  @PostMapping("/refresh_token/{serverAlias}")
  public String refreshToken(@PathVariable String serverAlias, Model model, Principal principal) {
    OauthAuthorizationErrorBuilder error = OauthAuthorizationError.builder().appName("RaID");
    String redirectResult = "";
    try {
      raidServiceClientAdapter.performRefreshToken(principal.getName(), serverAlias);
      redirectResult = "connect/raid/connected";
    } catch (Exception e) {
      log.error("Error while refreshing token on RaID: {}", e.getMessage());
      error.errorMsg("Error during token refresh" + e.getMessage());
      error.errorDetails(e.getMessage());
      model.addAttribute("error", error.build());
      redirectResult = "connect/authorizationError";
    }

    return redirectResult;
  }
}

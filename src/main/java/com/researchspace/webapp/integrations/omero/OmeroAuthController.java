package com.researchspace.webapp.integrations.omero;

import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.webapp.controller.BaseController;
import java.security.Principal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@Controller
@RequestMapping("/apps/omero") // NOT AN OAUTH CONTROLLER
public class OmeroAuthController extends BaseController {
  public static class OmeroAccessTokenReader {
    private static final String credentialsDelimiter = "_,_";

    public static String createDelimitedStringFromOmeroLogin(OmeroUser ou) {
      return ou.getOmerousername() + credentialsDelimiter + ou.getOmeropassword();
    }

    public static OmeroUser createOmeroUserCredentialsFromDelimitedString(String target) {
      String[] args = target.split(credentialsDelimiter);
      return new OmeroUser(args[0], args[1]);
    }
  }

  @Value("${omero.api.url}")
  private String omeroWebUrl;

  @Value("${omero.servername}")
  private String omeroServerName;

  @Autowired
  @Qualifier("userNameToUserConnection")
  private Map<String, UserConnection> userUserConnectionMap;

  @PostMapping("/connect")
  public RedirectView connect(OmeroUser loginData) throws Exception {
    User subject = userManager.getAuthenticatedUserInSession();
    JSONClient jsonClient = new JSONClient(omeroWebUrl);
    Map<String, Integer> servers = jsonClient.getServers();
    jsonClient.login(
        loginData.getOmerousername(), loginData.getOmeropassword(), servers.get(omeroServerName));
    UserConnection omeroConn = new UserConnection();
    omeroConn.setAccessToken(OmeroAccessTokenReader.createDelimitedStringFromOmeroLogin(loginData));
    omeroConn.setDisplayName("RSpace Omero login credentials");
    omeroConn.setId(
        new UserConnectionId(subject.getUsername(), OMERO_APP_NAME, loginData.getOmerousername()));
    omeroConn.setRank(1);
    userUserConnectionMap.put("omero_" + subject.getUsername(), omeroConn);
    String redirectUri = properties.getServerUrl() + "/apps/omero/redirect_uri";
    return new RedirectView(redirectUri);
  }

  @GetMapping("/connect")
  public String getConnect() {
    return "connect/omero/connect";
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    User subject = userManager.getAuthenticatedUserInSession();
    userUserConnectionMap.remove("omero_" + subject.getUsername());
    log.info("Deleted Omero connection for user {}", principal.getName());
  }

  @GetMapping("/redirect_uri")
  public String onAuthorization() {
    return "connect/omero/connected";
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ClientError {
    @JsonProperty("status_code")
    private int statusCode;

    @JsonProperty("error_message")
    private String errorMessage;
  }
}

package com.researchspace.webapp.integrations.omero;

import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.integrations.helper.ConnectionResultPage;
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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@Controller
@RequestMapping("/apps/omero") // NOT AN OAUTH CONTROLLER
public class OmeroAuthController extends BaseController {
  private static final String CONNECTED_VIEW = "connect/connected";
  private static final String APP_DISPLAY_NAME = "OMERO";
  private static final String CONNECTION_CHANNEL = "rspace.apps.omero.connection";
  private static final String CONNECTION_TYPE = "OMERO_CONNECTED";

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
  public ModelAndView connect(OmeroUser loginData) {
    User subject = userManager.getAuthenticatedUserInSession();
    try {
      JSONClient jsonClient = new JSONClient(omeroWebUrl);
      Map<String, Integer> servers = jsonClient.getServers();
      jsonClient.login(
          loginData.getOmerousername(), loginData.getOmeropassword(), servers.get(omeroServerName));
      UserConnection omeroConn = new UserConnection();
      omeroConn.setAccessToken(
          OmeroAccessTokenReader.createDelimitedStringFromOmeroLogin(loginData));
      omeroConn.setDisplayName("RSpace Omero login credentials");
      omeroConn.setId(
          new UserConnectionId(
              subject.getUsername(), OMERO_APP_NAME, loginData.getOmerousername()));
      omeroConn.setRank(1);
      userUserConnectionMap.put("omero_" + subject.getUsername(), omeroConn);
      String redirectUri = properties.getServerUrl() + "/apps/omero/redirect_uri";
      return new ModelAndView(new RedirectView(redirectUri));
    } catch (Exception e) {
      log.warn("Could not connect to OMERO for user {}", subject.getUsername(), e);
      ModelAndView mav = new ModelAndView(CONNECTED_VIEW);
      mav.addObject("appName", APP_DISPLAY_NAME);
      mav.addObject("connectionChannel", CONNECTION_CHANNEL);
      mav.addObject("connectionType", CONNECTION_TYPE);
      mav.addObject("connectionError", "Could not log in to OMERO: " + e.getMessage());
      return mav;
    }
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    User subject = userManager.getAuthenticatedUserInSession();
    userUserConnectionMap.remove("omero_" + subject.getUsername());
    log.info("Deleted Omero connection for user {}", principal.getName());
  }

  @GetMapping("/redirect_uri")
  public String onAuthorization(Model model) {
    ConnectionResultPage.addConnectionAttributes(
        model, APP_DISPLAY_NAME, CONNECTION_CHANNEL, CONNECTION_TYPE);
    return CONNECTED_VIEW;
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

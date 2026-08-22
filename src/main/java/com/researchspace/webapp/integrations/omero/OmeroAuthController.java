package com.researchspace.webapp.integrations.omero;

import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import com.researchspace.webapp.integrations.helper.ConnectionResultPage;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
    static final String credentialsDelimiter = "_,_";

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

  @Autowired private UserConnectionManager userConnectionManager;

  // overridable seam so connect() can be unit-tested without a live OMERO server
  JSONClient newJsonClient() {
    return new JSONClient(omeroWebUrl);
  }

  /**
   * The credentials are stored as a delimited string, so neither half may contain the delimiter and
   * neither may be blank: a blank password would store "user_,_", which splits back into a single
   * element and breaks every later OMERO request.
   */
  private void validateCredentials(OmeroUser loginData) {
    if (StringUtils.isBlank(loginData.getOmerousername())
        || StringUtils.isBlank(loginData.getOmeropassword())) {
      throw new IllegalArgumentException(getText("apps.omero.errors.blankCredentials"));
    }
    if (loginData.getOmerousername().contains(OmeroAccessTokenReader.credentialsDelimiter)
        || loginData.getOmeropassword().contains(OmeroAccessTokenReader.credentialsDelimiter)) {
      throw new IllegalArgumentException(
          getText(
              "apps.omero.errors.credentialsDelimiter",
              new Object[] {OmeroAccessTokenReader.credentialsDelimiter}));
    }
  }

  @PostMapping("/connect")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"omeropassword"})
  public ModelAndView connect(OmeroUser loginData) {
    User subject = userManager.getAuthenticatedUserInSession();
    try {
      validateCredentials(loginData);
      JSONClient jsonClient = newJsonClient();
      Map<String, Integer> servers = jsonClient.getServers();
      Integer serverId = servers.get(omeroServerName);
      if (serverId == null) {
        throw new IllegalStateException(
            getText("apps.omero.errors.unknownServer", new Object[] {omeroServerName}));
      }
      // login() returns null rather than throwing when OMERO rejects the credentials, so the
      // result must be checked or we would store a password OMERO has already refused
      if (jsonClient.login(loginData.getOmerousername(), loginData.getOmeropassword(), serverId)
          == null) {
        throw new IllegalArgumentException(getText("apps.omero.errors.rejectedCredentials"));
      }
      UserConnection omeroConn = new UserConnection();
      omeroConn.setAccessToken(
          OmeroAccessTokenReader.createDelimitedStringFromOmeroLogin(loginData));
      omeroConn.setDisplayName("RSpace Omero login credentials");
      omeroConn.setId(
          new UserConnectionId(
              subject.getUsername(), OMERO_APP_NAME, loginData.getOmerousername()));
      omeroConn.setRank(1);
      // connect replaces any previous connection, which may be under a different OMERO username
      userConnectionManager.replaceConnection(omeroConn);
      String redirectUri = properties.getServerUrl() + "/apps/omero/redirect_uri";
      return new ModelAndView(new RedirectView(redirectUri));
    } catch (Exception e) {
      log.warn("Could not connect to OMERO for user {}", subject.getUsername(), e);
      ModelAndView mav = new ModelAndView(CONNECTED_VIEW);
      mav.addObject("appName", APP_DISPLAY_NAME);
      mav.addObject("connectionChannel", CONNECTION_CHANNEL);
      mav.addObject("connectionType", CONNECTION_TYPE);
      mav.addObject(
          "connectionError", getText("apps.omero.errors.login", new Object[] {e.getMessage()}));
      return mav;
    }
  }

  @DeleteMapping("/connect")
  public void disconnect() {
    // the username is taken from the session, never the request, so a caller can only ever delete
    // their own connection. Log the same identity that was acted on.
    String username = userManager.getAuthenticatedUserInSession().getUsername();
    int deleted = userConnectionManager.deleteByUserAndProvider(username, OMERO_APP_NAME);
    log.info("Deleted {} Omero connection(s) for user {}", deleted, username);
  }

  @GetMapping("/redirect_uri")
  public String onAuthorization(Model model) {
    ConnectionResultPage.addConnectionAttributes(
        model, APP_DISPLAY_NAME, CONNECTION_CHANNEL, CONNECTION_TYPE);
    return CONNECTED_VIEW;
  }
}

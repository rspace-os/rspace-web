package com.researchspace.webapp.integrations.egnyte;

import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;
import static com.researchspace.session.SessionAttributeUtils.SESSION_EGNYTE_TOKEN;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Class responsible for handling connection between RSpace and Box API */
@Controller
@Slf4j
@RequestMapping("/egnyte")
public class EgnyteController {

  /*
   * =================================================
   *  methods for storing access token
   *  for egnyte webapp (tinymce linking tool)
   * =================================================
   */

  @GetMapping("/egnyteSessionToken")
  @ResponseBody
  public String getEgnyteTokenFromSession(HttpSession session) {
    log.info("requesting egnyte token, which is: " + session.getAttribute(SESSION_EGNYTE_TOKEN));
    return (String) session.getAttribute(SESSION_EGNYTE_TOKEN);
  }

  @PostMapping("/egnyteSessionToken")
  @ResponseBody
  public String saveEgnyteTokenToSession(@RequestParam("token") String token, HttpSession session) {
    log.info("saving egnyte token: " + token);
    session.setAttribute(SESSION_EGNYTE_TOKEN, token);
    return "OK";
  }

  /*
   * ==========================================================
   *  methods for setting filestore access token for the user
   *  ('Egnyte as an RSpace filestore' setup)
   * ==========================================================
   */

  @Autowired private UserConnectionManager userConnectionManager;

  @Autowired private EgnyteAuthConnector egnyteConnector;

  @GetMapping("/egnyteConnectionSetup")
  public String getConnectSetupPage() {
    return "/connect/egnyte/egnyteConnectionSetup";
  }

  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"egnytePassword"})
  @PostMapping("/connectUserToEgnyteFilestore")
  @ResponseBody
  public AjaxReturnObject<Boolean> connectUserToEgnyteFilestore(
      @RequestParam(value = "egnyteUsername") String egnyteUsername,
      @RequestParam(value = "egnytePassword") String egnytePassword,
      HttpSession session,
      Principal principal)
      throws IOException {

    // check incoming values
    if (StringUtils.isBlank(egnyteUsername) || StringUtils.isBlank(egnytePassword)) {
      return new AjaxReturnObject<Boolean>(
          null,
          ErrorList.createErrListWithSingleMsg("Egnyte username and password must be provided."));
    }

    // query egnyte for access token
    Map<String, Object> accessTokenResponse =
        egnyteConnector.queryForEgnyteAccessToken(egnyteUsername, egnytePassword);
    if (accessTokenResponse == null || !accessTokenResponse.containsKey("access_token")) {
      return new AjaxReturnObject<Boolean>(
          null,
          ErrorList.createErrListWithSingleMsg("Couldn't authenticate with provided credentials."));
    }
    String token = (String) accessTokenResponse.get("access_token");
    Integer expiresIn = (Integer) accessTokenResponse.get("expires_in");

    // verify that access token works by retrieving details of egnyte user
    Map<String, Object> userDetailsResponse =
        egnyteConnector.queryForEgnyteUserInfoWithAccessToken(token);
    if (userDetailsResponse == null || !userDetailsResponse.containsKey("id")) {
      return new AjaxReturnObject<Boolean>(
          null,
          ErrorList.createErrListWithSingleMsg(
              "Received un-workable Egnyte access token for provided credentials."));
    }

    // save token and egnyte user id to UserConnection table
    Integer egnyteId = (Integer) userDetailsResponse.get("id");
    saveEgnyteFilestoreToken(token, expiresIn, principal.getName(), egnyteId);

    // save connection status in session
    session.setAttribute(SessionAttributeUtils.EXT_FILESTORE_CONNECTION_OK, true);

    return new AjaxReturnObject<Boolean>(true, null);
  }

  @PostMapping("/disconnectUserFromEgnyteFilestore")
  @ResponseBody
  public AjaxReturnObject<Boolean> disconnectUserFromEgnyteFilestore(
      HttpSession session, Principal principal) {

    userConnectionManager.deleteByUserAndProvider(EGNYTE_APP_NAME, principal.getName());
    session.setAttribute(SessionAttributeUtils.EXT_FILESTORE_CONNECTION_OK, false);
    return new AjaxReturnObject<Boolean>(true, null);
  }

  /** Updates UserConnection table with new access token for Egnyte user */
  private UserConnection saveEgnyteFilestoreToken(
      String token, Integer tokenExpiresIn, String rspaceUsername, Integer egnyteId) {

    Optional<UserConnection> existingConnection =
        userConnectionManager.findByUserNameProviderName(rspaceUsername, EGNYTE_APP_NAME);
    UserConnection conn = existingConnection.orElse(new UserConnection());
    if (!existingConnection.isPresent()) {
      conn.setDisplayName("Egnyte internal application access token");
      conn.setId(new UserConnectionId(rspaceUsername, EGNYTE_APP_NAME, Integer.toString(egnyteId)));
      conn.setRank(1);
    }
    conn.setAccessToken(token);
    conn.setExpireTime(tokenExpiresIn.longValue());
    return userConnectionManager.save(conn);
  }

  /*
   * ================
   *  for tests
   * ================
   */

  protected void setEgnyteConnector(EgnyteAuthConnector egnyteConnector) {
    this.egnyteConnector = egnyteConnector;
  }
}

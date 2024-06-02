package com.researchspace.webapp.integrations.box;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile.Info;
import com.box.sdk.BoxFileVersion;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.field.ErrorList;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Class responsible for handling connection between RSpace and Box API */
@Controller
@RequestMapping("/box")
public class BoxController {

  protected static final String SESSION_BOX_API_CONNECTION = "SESSION_BOX_API_CONNECTION";
  protected static final String SESSION_BOX_ANTI_CSRF_TOKEN = "SESSION_BOX_ANTI_CSRF_TOKEN";

  protected static final String USER_NOT_AUTHORIZED = "USER_NOT_AUTHORIZED";
  protected static final String API_CONFIGURATION_INCORRECT = "API_CONFIGURATION_INCORRECT";
  protected static final String API_OTHER_ERROR = "API_OTHER_ERROR";
  protected static final String API_NO_VERSION_HISTORY = "API_NO_VERSION_HISTORY";

  protected static final String AUTHORIZATION_ERROR_MSG = "Authorization problem";
  protected static final String DOWNLOAD_ERROR_MSG =
      "A problem occured when trying to download the file from Box. "
          + "Please close this window and try again.";

  private Logger log = LoggerFactory.getLogger(BoxController.class);

  @Value("${box.client.id}")
  private String clientId;

  @Value("${box.client.secret}")
  private String clientSecret;

  private BoxConnector boxConnector = new BoxConnector();

  @GetMapping("/boxApiAvailabilityCheck")
  @ResponseBody
  public AjaxReturnObject<String> checkApiAvailability(HttpSession session) {

    if (StringUtils.isEmpty(clientSecret)) {
      return new AjaxReturnObject<String>(
          null, ErrorList.createErrListWithSingleMsg(API_CONFIGURATION_INCORRECT));
    }
    if (session.getAttribute(SESSION_BOX_API_CONNECTION) == null) {
      String token = generateBoxAntiCSRFToken();
      session.setAttribute(SESSION_BOX_ANTI_CSRF_TOKEN, token);

      String unauthorizedMsg = USER_NOT_AUTHORIZED + ':' + token;
      return new AjaxReturnObject<String>(unauthorizedMsg, null);
    }
    return new AjaxReturnObject<>("OK", null);
  }

  protected String generateBoxAntiCSRFToken() {
    return SecureStringUtils.getURLSafeSecureRandomString(16);
  }

  @GetMapping("/boxResourceDetails")
  @ResponseBody
  public AjaxReturnObject<BoxResourceInfo> getResourceDetailsFromBox(
      @RequestParam("boxId") String boxId, HttpSession session) {

    BoxAPIConnection apiConnection = restoreBoxApiConnection(session);
    if (apiConnection == null) {
      return new AjaxReturnObject<BoxResourceInfo>(
          null, ErrorList.createErrListWithSingleMsg(USER_NOT_AUTHORIZED));
    }

    BoxResourceInfo boxFileInfo = null;
    try {
      Info info = boxConnector.getBoxFileInfo(apiConnection, boxId);

      // check if version history is available on Box account (will throw 403 exception for Personal
      // account)
      try {
        boxConnector.getBoxFileVersionHistory(info.getResource());
      } catch (BoxAPIException bae) {
        if (bae.getResponseCode() == HttpServletResponse.SC_FORBIDDEN) {
          return new AjaxReturnObject<BoxResourceInfo>(
              null, ErrorList.createErrListWithSingleMsg(API_NO_VERSION_HISTORY));
        } else {
          throw bae;
        }
      }

      boxFileInfo = new BoxResourceInfo(info);

    } catch (BoxAPIException bae) {
      if (bae.getResponseCode() == HttpServletResponse.SC_NOT_FOUND) {
        // maybe it's a folder
        try {
          boxFileInfo = new BoxResourceInfo(boxConnector.getBoxFolderInfo(apiConnection, boxId));
        } catch (BoxAPIException bae2) {
          log.warn("box file & folder not found", bae2);
        }
      } else {
        log.warn("box api exception for file info", bae);
      }
    }

    if (boxFileInfo == null) {
      /* maybe apiConnection is somehow corrupted, let's reset it so user can try again with better chances */
      resetBoxApiConnection(session);
      return new AjaxReturnObject<BoxResourceInfo>(
          null, ErrorList.createErrListWithSingleMsg(API_OTHER_ERROR));
    }

    return new AjaxReturnObject<>(boxFileInfo, null);
  }

  /** downloads file version from box server and redirects stream to response. */
  @GetMapping("/downloadBoxFile")
  @ResponseBody
  public void downloadBoxFile(
      @RequestParam("boxId") String boxId,
      @RequestParam("boxVersionID") String boxVersionID,
      @RequestParam("boxName") String name,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {

    BoxAPIConnection apiConnection = restoreBoxApiConnection(session);
    if (apiConnection == null) {
      response.getWriter().print(USER_NOT_AUTHORIZED);
      return;
    }

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      Info info = boxConnector.getBoxFileInfo(apiConnection, boxId);

      if (info.getVersion().getID().equals(boxVersionID)) {
        info.getResource().download(stream);
      } else {
        Collection<BoxFileVersion> versions =
            boxConnector.getBoxFileVersionHistory(info.getResource());
        BoxFileVersion versionToDownload = null;
        for (BoxFileVersion ver : versions) {
          if (ver.getVersionID().equals(boxVersionID)) {
            versionToDownload = ver;
            break;
          }
        }
        if (versionToDownload == null) {
          log.warn("no file found with id: %s and versionID: %s", boxId, boxVersionID);
          return;
        }
        versionToDownload.download(stream);
      }
    } catch (BoxAPIException bae) {
      log.warn("exception on file download attempt", bae);
    } finally {
      stream.close();
    }

    if (stream.size() == 0) {
      response.getWriter().print(DOWNLOAD_ERROR_MSG);
      resetBoxApiConnection(session);
      return;
    }

    byte[] byteArray = stream.toByteArray();
    try (ByteArrayInputStream in = new ByteArrayInputStream(byteArray)) {
      response.setContentType("application/octet-stream");
      response.setContentLength((int) byteArray.length);
      response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", name));
      OutputStream outStream = response.getOutputStream();
      outStream.write(byteArray);
    }
  }

  @RequestMapping(value = "/redirect_uri", method = RequestMethod.GET)
  public String onAuthorization(
      @RequestParam Map<String, String> params, Model model, HttpSession session) {

    if (params.containsKey("error")) {
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg(params.get("error"))
              .errorDetails(params.get("error_description"))
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }

    String antiCSRFToken = (String) session.getAttribute(SESSION_BOX_ANTI_CSRF_TOKEN);
    String tokenFromRedirect = params.get("state");
    if (StringUtils.isEmpty(antiCSRFToken) || !antiCSRFToken.equals(tokenFromRedirect)) {
      OauthAuthorizationError error = getAuthErrorBuilder().errorMsg("invalid_token").build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }

    String authorizationCode = params.get("code");
    try {
      BoxAPIConnection conn =
          boxConnector.createBoxAPIConnection(clientId, clientSecret, authorizationCode);
      boxConnector.testBoxConnection(conn);
      session.setAttribute(SESSION_BOX_API_CONNECTION, conn.save());
      return "connect/box/connected";

    } catch (BoxAPIException bae) {
      log.warn("error on creating Box connection", bae);
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg("Box API exception: " + bae.getResponseCode())
              .errorDetails(AUTHORIZATION_ERROR_MSG)
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }
  }

  private OauthAuthorizationErrorBuilder getAuthErrorBuilder() {
    return OauthAuthorizationError.builder().appName("Box");
  }

  protected BoxAPIConnection restoreBoxApiConnection(HttpSession session) {
    String apiConnectionState = (String) session.getAttribute(SESSION_BOX_API_CONNECTION);
    if (apiConnectionState != null) {
      return boxConnector.restoreBoxAPIConnection(clientId, clientSecret, apiConnectionState);
    }
    return null;
  }

  private void resetBoxApiConnection(HttpSession session) {
    session.removeAttribute(SESSION_BOX_API_CONNECTION);
  }

  /*
   * =================
   * for testing
   * =================
   */
  protected void setBoxConnector(BoxConnector boxConnector) {
    this.boxConnector = boxConnector;
  }
}

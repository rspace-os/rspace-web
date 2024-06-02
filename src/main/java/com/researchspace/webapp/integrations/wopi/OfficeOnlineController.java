package com.researchspace.webapp.integrations.wopi;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.webapp.integrations.wopi.WopiAccessTokenHandler.WopiAccessToken;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiAction;
import java.security.Principal;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Class handling WOPI protocol endpoints for MS Office Online integration */
@Controller
@RequestMapping("/officeOnline")
public class OfficeOnlineController extends RspaceOnlineController {

  @Autowired private WopiAccessTokenHandler accessTokenHandler;

  @Autowired private WopiUtils wopiUtils;

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @Override
  public String getWopiHostPage(
      @PathVariable String fileId,
      @PathVariable String action,
      @RequestParam Map<String, String> requestParams,
      Model model,
      Principal p) {

    User user = getUserByUsername(p.getName());
    EcatMediaFile mediaFile =
        getMediaRecordFromFileId(fileId, user); // asserts file exists and can be read by user

    // default to 'view' action if user has no write permission
    boolean canWrite = isRecordAccessPermitted(user, mediaFile, PermissionType.WRITE);
    String actionToPerform;
    if ("convert".equals(action) && canWrite) {
      actionToPerform = "convert";
    } else if ("edit".equals(action) && canWrite) {
      actionToPerform = "edit";
    } else {
      actionToPerform = "view";
    }

    model.addAttribute("fileId", fileId);
    model.addAttribute("docName", mediaFile.getName());
    model.addAttribute("docExtension", mediaFile.getExtension());

    // if current action is unavailable, navigate to 'unsupported' view
    WopiAction wopiAction = getWopiActionForMediaFile(actionToPerform, mediaFile);
    if (wopiAction == null) {
      model.addAttribute("actionToPerform", actionToPerform);
      return "connect/msoffice/unsupportedView";
    }

    // set attributes for current action
    String actionUrl = getWopiActionUrlForMediaFile(wopiAction, mediaFile, user, requestParams);
    model.addAttribute("actionUrl", actionUrl);

    WopiAccessToken wopiToken = accessTokenHandler.createAccessToken(user, fileId);
    model.addAttribute("msAccessToken", wopiToken.getAccessToken());
    model.addAttribute("msAccessTokenTTL", wopiToken.getExpiryDate());

    String favIcon = wopiAction.getApp().getFavIconUrl();
    model.addAttribute("favIcon", favIcon);

    // add edit and convert links, so page can correctly handle 'edit in browser' action
    WopiAction editAction = getWopiActionForMediaFile("edit", mediaFile);
    model.addAttribute("editActionAvailable", editAction != null);
    model.addAttribute("editActionUrl", wopiUtils.getUrlToOfficeOnlineViewPage(mediaFile, "edit"));

    WopiAction convertAction = getWopiActionForMediaFile("convert", mediaFile);
    model.addAttribute("convertActionAvailable", convertAction != null);
    model.addAttribute(
        "convertActionTargetext", convertAction != null ? convertAction.getTargetext() : "");
    model.addAttribute(
        "convertActionUrl", wopiUtils.getUrlToOfficeOnlineViewPage(mediaFile, "convert"));

    return "connect/msoffice/officeView";
  }

  /**
   * @return action url for requested action if available for given extension (null otherwise)
   */
  String getWopiActionUrlForMediaFile(
      WopiAction action, EcatMediaFile mediaFile, User user, Map<String, String> requestParams) {
    if (action == null) {
      return "";
    }
    String urlsrc = action.getUrlsrc();
    String actionUrlNoParams = urlsrc.substring(0, urlsrc.indexOf("<ui="));
    String wopiServerFileInfoUrl = wopiUtils.getWopiServerFilesUrl(mediaFile);
    String actionUrl = actionUrlNoParams + "wopisrc=" + wopiServerFileInfoUrl;
    if (urlsrc.contains("BUSINESS_USER") && isBusinessUser(user)) {
      actionUrl += "&IsLicensedUser=1";
    }
    String actionUrlWithWdParams = addWdParametersToActionUrl(actionUrl, requestParams);
    return actionUrlWithWdParams;
  }

  private String addWdParametersToActionUrl(String actionUrl, Map<String, String> requestParams) {
    StringBuilder sb = new StringBuilder(actionUrl);
    for (Entry<String, String> paramVal : requestParams.entrySet()) {
      if (paramVal.getKey().startsWith("wd")) {
        sb.append("&").append(paramVal.getKey()).append("=").append(paramVal.getValue());
      }
    }
    return sb.toString();
  }
}

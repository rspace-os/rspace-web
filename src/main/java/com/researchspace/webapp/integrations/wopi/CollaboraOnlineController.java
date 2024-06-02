package com.researchspace.webapp.integrations.wopi;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.webapp.integrations.wopi.WopiAccessTokenHandler.WopiAccessToken;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiAction;
import java.security.Principal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/collaboraOnline")
public class CollaboraOnlineController extends RspaceOnlineController {

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

    model.addAttribute("fileId", fileId);
    model.addAttribute("docName", mediaFile.getName());
    model.addAttribute("docExtension", mediaFile.getExtension());

    // if current action is unavailable, navigate to 'unsupported' view
    WopiAction wopiAction = getWopiActionForMediaFile(action, mediaFile);
    if (wopiAction == null) {
      return "connect/msoffice/unsupportedView";
    }
    model.addAttribute("actionToPerform", wopiAction.getName());

    // set attributes for current action
    String actionUrl = getWopiActionUrlForMediaFile(wopiAction, mediaFile, requestParams);
    model.addAttribute("actionUrl", actionUrl);

    WopiAccessToken wopiToken = accessTokenHandler.createAccessToken(user, fileId);
    model.addAttribute("collaboraAccessToken", wopiToken.getAccessToken());

    String favIcon = wopiAction.getApp().getFavIconUrl();
    model.addAttribute("favIcon", favIcon);

    return "connect/wopi/collaboraOnline/collaboraView";
  }

  @Override
  public WopiAction getWopiActionForMediaFile(String actionType, EcatMediaFile file) {
    String extension = file.getExtension();
    Map<String, WopiAction> actionsForFile =
        discoveryServiceHandler.getActionsForFileType(extension);
    log.debug("returning wopi action '{}' for extension '{}'", actionType, extension);
    WopiAction actionToReturn = actionsForFile.get(actionType);
    if (actionToReturn != null) {
      return actionToReturn;
    } else {
      // If no action to edit, return first entry.
      return actionsForFile.entrySet().iterator().next().getValue();
    }
  }

  /**
   * @return action url for requested action if available for given extension (null otherwise)
   */
  String getWopiActionUrlForMediaFile(
      WopiAction action, EcatMediaFile mediaFile, Map<String, String> requestParams) {
    if (action == null) {
      return "";
    }
    String urlsrc = action.getUrlsrc();
    String wopiServerFileInfoUrl = wopiUtils.getWopiServerFilesUrl(mediaFile);
    String actionUrl = urlsrc + "WOPISrc=" + wopiServerFileInfoUrl;

    return actionUrl;
  }
}

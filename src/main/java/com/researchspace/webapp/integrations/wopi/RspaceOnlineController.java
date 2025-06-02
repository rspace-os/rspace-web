package com.researchspace.webapp.integrations.wopi;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiAction;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiApp;
import java.security.Principal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public abstract class RspaceOnlineController extends BaseController {

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @GetMapping("/{fileId}/{action}")
  public abstract String getWopiHostPage(
      @PathVariable String fileId,
      @PathVariable String action,
      @RequestParam Map<String, String> requestParams,
      Model model,
      Principal p);

  @GetMapping("/supportedExts")
  @ResponseBody
  public Map<String, WopiApp> getSupportedExtensions() {
    return discoveryServiceHandler.getSupportedExtensions();
  }

  public EcatMediaFile getMediaRecordFromFileId(String fileId, User user) {

    GlobalIdentifier globalId = new GlobalIdentifier(fileId);
    BaseRecord baseRecord = recordManager.get(globalId.getDbId());
    assertAuthorisation(user, baseRecord, PermissionType.READ);
    assertIsMediaRecord(fileId, baseRecord, user);

    return (EcatMediaFile) baseRecord;
  }

  private void assertIsMediaRecord(String fileId, BaseRecord baseRecord, User user) {
    if (!baseRecord.isMediaRecord()) {
      SECURITY_LOG.warn(
          "User [{}] tries accessing media file id [{}], but it's not a media file",
          user.getUsername(),
          fileId);
      throw new IllegalStateException("Provided file id " + fileId + "is not for media record");
    }
  }

  WopiAction getWopiActionForMediaFile(String actionType, EcatMediaFile file) {
    String extension = file.getExtension();
    Map<String, WopiAction> actionsForFile =
        discoveryServiceHandler.getActionsForFileType(extension);
    log.debug("returning wopi action '{}' for extension '{}'", actionType, extension);
    return actionsForFile.get(actionType);
  }

  boolean isConversionSupportedForExtension(String extension) {
    return discoveryServiceHandler.getActionsForFileType(extension).containsKey("convert");
  }

  boolean isBusinessUser(User user) {
    return true; // it seems we need to treat all our users as business users
  }
}

package com.researchspace.webapp.integrations.protocolsio;

import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.protocolsio.PIOStepComponent;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.service.SharingHandler;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for handling import of data from external sources as RSpaceDocuments */
@RestController
@RequestMapping("/importer/generic/protocols_io")
public class ProtocolsIOController extends BaseController {

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class PIOResponse {
    private List<RecordInformation> results;
    private Long importFolderId;
  }

  private @Autowired ProtocolsIOToDocumentConverter converter;
  private @Autowired SharingHandler recordShareHandler;
  private @Autowired IPermissionUtils permissnUtils;

  @PostMapping(value = "/{parentFolderId}")
  public AjaxReturnObject<PIOResponse> importExternalData(
      @PathVariable("parentFolderId") Long parentFolderId, @RequestBody List<Protocol> protocols) {
    log.info("Importing {} protocols", protocols.size());
    List<RecordInformation> results = new ArrayList<>();

    User subject = userManager.getAuthenticatedUserInSession();
    Folder originalParentFolder = folderManager.getFolder(parentFolderId, subject);
    Long finalParentFolderId = folderManager.getImportsFolder(subject).getId();
    if (originalParentFolder.isNotebook()
        && permissnUtils.isPermitted(originalParentFolder, PermissionType.CREATE, subject)) {
      finalParentFolderId = originalParentFolder.getId();
    }
    for (Protocol protocol : protocols) {
      if (protocol.getSteps() != null) {
        protocol.orderComponents(PIOStepComponent.DisplayOrder);
      }
      StructuredDocument converted =
          converter.generateFromProtocol(protocol, subject, finalParentFolderId);
      results.add(converted.toRecordInfo());
      if (recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(
          subject, originalParentFolder)) {
        recordShareHandler.shareIntoSharedFolderOrNotebook(
            subject, originalParentFolder, converted.getId());
      }
    }
    PIOResponse response = new PIOResponse(results, finalParentFolderId);
    return new AjaxReturnObject<>(response, null);
  }
}

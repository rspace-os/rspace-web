package com.researchspace.webapp.integrations.protocolsio;

import static com.researchspace.model.utils.Utils.convertToLongOrNull;

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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /**
   * @param parentFolderId decides where to save the downloaded protocol; handles '0' as a special
   *     value pointing to user's 'Imports' folder.
   * @param grandParentFolderId required when importing into shared notebook
   * @param protocols list of protocols to import
   * @return
   */
  @PostMapping(
      value = "/{parentFolderId}",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public AjaxReturnObject<PIOResponse> importExternalData(
      @PathVariable(value = "parentFolderId") Long parentFolderId,
      @RequestParam(value = "grandParentId", required = false) String grandParentFolderId,
      @RequestBody List<Protocol> protocols) {

    log.info("Importing {} protocols", protocols.size());
    List<RecordInformation> results = new ArrayList<>();

    Long grandParentId = convertToLongOrNull(grandParentFolderId);
    User subject = userManager.getAuthenticatedUserInSession();
    Folder targetFolder = getTargetFolder(parentFolderId, subject);
    for (Protocol protocol : protocols) {
      if (protocol.getSteps() != null) {
        protocol.orderComponents(PIOStepComponent.DisplayOrder);
      }
      StructuredDocument converted =
          converter.generateFromProtocol(protocol, subject, targetFolder.getId());
      results.add(converted.toRecordInfo());
      if (recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(
          subject, targetFolder)) {
        recordShareHandler.shareIntoSharedFolderOrNotebook(
            subject, targetFolder, converted.getId(), grandParentId);
      }
    }
    PIOResponse response = new PIOResponse(results, targetFolder.getId());
    return new AjaxReturnObject<>(response, null);
  }

  private Folder getTargetFolder(Long parentFolderId, User subject) {
    Folder finalParentFolder = folderManager.getImportsFolder(subject);
    if (parentFolderId != 0L) {
      Folder originalParentFolder = folderManager.getFolder(parentFolderId, subject);
      if (originalParentFolder.isNotebook()
          && permissnUtils.isPermitted(originalParentFolder, PermissionType.CREATE, subject)) {
        finalParentFolder = originalParentFolder;
      }
    }
    return finalParentFolder;
  }
}

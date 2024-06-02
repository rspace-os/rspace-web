package com.researchspace.webapp.integrations.protocolsio;

import com.researchspace.model.User;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.protocolsio.PIOStepComponent;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

  @PostMapping()
  public AjaxReturnObject<PIOResponse> importExternalData(@RequestBody List<Protocol> protocols) {
    log.info("Importing {} protocols", protocols.size());
    List<RecordInformation> results = new ArrayList<>();

    User subject = userManager.getAuthenticatedUserInSession();
    Long importId = folderManager.getImportsFolder(subject).getId();
    for (Protocol protocol : protocols) {
      if (protocol.getSteps() != null) {
        protocol.orderComponents(PIOStepComponent.DisplayOrder);
      }
      StructuredDocument converted = converter.generateFromProtocol(protocol, subject);
      results.add(converted.toRecordInfo());
    }
    PIOResponse response = new PIOResponse(results, importId);
    return new AjaxReturnObject<>(response, null);
  }
}

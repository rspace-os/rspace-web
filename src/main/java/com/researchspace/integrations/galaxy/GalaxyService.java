package com.researchspace.integrations.galaxy;

import com.researchspace.files.service.FileStore;
import com.researchspace.galaxy.client.GalaxyClient;
import com.researchspace.galaxy.model.input.workflow.SingleReadRNAFastQsWorkflowInvocationRequest;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.galaxy.model.output.upload.UploadFileResponse;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserConnectionManager;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GalaxyService {

  private final GalaxyClient client;
  private final BaseRecordManager baseRecordManager;
  private final FileStore fileStore;
  private final RecordManager recordManager;
  private final FieldManager fieldManager;
  @Autowired private UserConnectionManager userConnectionManager;

  public GalaxyService(
      GalaxyClient client,
      BaseRecordManager baseRecordManager,
      @Qualifier("compositeFileStore") FileStore fileStore,
      RecordManager recordManager,
      FieldManager fieldManager) {
    this.client = client;
    this.baseRecordManager = baseRecordManager;
    this.fileStore = fileStore;
    this.recordManager = recordManager;
    this.fieldManager = fieldManager;
  }

  public History setUpDataInGalaxyFor(
      User user,
      long recordId,
      long fieldId,
      long[] selectedAttachmentIds,
      String selectedWorkflowId)
      throws IOException {
    String apiKey =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), IntegrationsHandler.GALAXY_APP_NAME)
            .get()
            .getAccessToken();
    BaseRecord theDocument = recordManager.getRecordWithFields(recordId, user);
    Field field = fieldManager.get(fieldId, user).get();
    String fieldName = field.getName();
    String fieldGlobalID = field.getOid().toString();
    String globalID = ((StructuredDocument) theDocument).getOidWithVersion().toString();
    String docName = ((StructuredDocument) theDocument).getEditInfo().getName();
    String metaData = "RSPACE_" + docName + "_" + globalID + "_" + fieldName + "_" + fieldGlobalID;
    History history = client.createNewHistory(apiKey, metaData);
    Map<String, String> uploadedFileNamesToIds = new HashMap<>();
    for (long attachmentId : selectedAttachmentIds) {
      EcatMediaFile ecatMediaFile =
          baseRecordManager.retrieveMediaFile(user, attachmentId, null, null, null);
      File attachmentFile = fileStore.findFile(ecatMediaFile.getFileProperty());
      UploadFileResponse uploadFileResponse =
          client.uploadFile(history.getId(), apiKey, attachmentFile);
      String uploadedFileGalaxyID = uploadFileResponse.getOutputs().get(0).getDatasetId();
      uploadedFileNamesToIds.put(
          uploadFileResponse.getOutputs().get(0).getName(), uploadedFileGalaxyID);
    }
    if (selectedWorkflowId.equals(SingleReadRNAFastQsWorkflowInvocationRequest.forWorkFlowWithId)) {
      client.createDatasetCollection(apiKey, history.getId(), metaData, uploadedFileNamesToIds);
    }
    return history;
  }
}

package com.researchspace.integrations.galaxy.service;

import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService.GALAXY;
import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RSPACE_CONTAINER_TYPE.FIELD;
import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RSPACE_DATA_TYPE.LOCAL;

import com.researchspace.files.service.FileStore;
import com.researchspace.galaxy.client.GalaxyClient;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.galaxy.model.output.upload.DatasetCollectionElement;
import com.researchspace.galaxy.model.output.upload.HistoryDatasetCollectionAssociation;
import com.researchspace.galaxy.model.output.upload.UploadFileResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationReport;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationStepInput;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationStepStatusResponse;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalWorkFlowDataManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserConnectionManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GalaxyService {

  private final GalaxyClient client;
  private final BaseRecordManager baseRecordManager;
  private final FileStore fileStore;
  private final RecordManager recordManager;
  private final FieldManager fieldManager;

  @Value("${galaxy.web.url}")
  private String baseUrl;

  @Autowired private UserConnectionManager userConnectionManager;
  @Autowired private ExternalWorkFlowDataManager externalWorkFlowDataManager;

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

  /**
   * Uploads data to galaxy, creates a dataset for that data with a name matching: "RSPACE_" +
   * docName + "_" + globalID + "_" + fieldName + "_" + fieldGlobalID; saves the upload response
   * data IDs from Galaxy into the RSpace DB, so we can later retrieve the data from Galaxy. Finally
   * uploads an annotation to the data in Galaxy for each file uploaded, with a link to the RSpace
   * page and a download link using the uploaded file's globalID.
   *
   * @param user
   * @param recordId
   * @param fieldId
   * @param selectedAttachmentIds
   * @param serverAddress
   * @return
   * @throws IOException
   */
  public History setUpDataInGalaxyFor(
      User user, long recordId, long fieldId, long[] selectedAttachmentIds, String serverAddress)
      throws IOException {
    String apiKey = getApiKeyForUser(user);
    BaseRecord theDocument = recordManager.getRecordWithFields(recordId, user);
    Field field = fieldManager.get(fieldId, user).get();
    String fieldName = field.getName();
    String fieldGlobalID = field.getOid().toString();
    String globalID = ((StructuredDocument) theDocument).getOidWithVersion().toString();
    String docName = theDocument.getEditInfo().getName();
    String metaData = generateUniqueMetaData(fieldId, docName, globalID, fieldName, fieldGlobalID);
    History history = client.createNewHistory(apiKey, metaData);
    Map<String, String> uploadedFileNamesToIds = new HashMap<>();
    for (long attachmentId : selectedAttachmentIds) {
      EcatMediaFile ecatMediaFile =
          baseRecordManager.retrieveMediaFile(user, attachmentId, null, null, null);
      File attachmentFile = fileStore.findFile(ecatMediaFile.getFileProperty());
      UploadFileResponse uploadFileResponse =
          client.uploadFile(history.getId(), apiKey, attachmentFile);
      String uploadedFileGalaxyID = uploadFileResponse.getOutputs().get(0).getDatasetId();
      String uploadedUUID = uploadFileResponse.getOutputs().get(0).getUuid();
      uploadedFileNamesToIds.put(
          uploadFileResponse.getOutputs().get(0).getName(), uploadedFileGalaxyID);
      ExternalWorkFlowData externalWorkFlowData =
          new ExternalWorkFlowData(
              GALAXY,
              attachmentId,
              LOCAL,
              fieldId,
              fieldName,
              FIELD,
              attachmentFile.getName(),
              uploadedFileGalaxyID,
              uploadedUUID,
              history.getId(),
              history.getName(),
              baseUrl);
      externalWorkFlowDataManager.save(externalWorkFlowData);
      String documentLink =
          serverAddress + "/workspace/editor/structuredDocument/" + theDocument.getId();
      String galleryLink = serverAddress + "/gallery/item/" + ecatMediaFile.getId();
      String downloadLink = serverAddress + "/globalId/" + ecatMediaFile.getGlobalIdentifier();
      String annotation =
          "Document: " + documentLink + " Data: " + galleryLink + " Download: " + downloadLink;
      client.putAnnotationOnDataset(history.getId(), uploadedFileGalaxyID, annotation, apiKey);
    }
    client.createDatasetCollection(apiKey, history.getId(), metaData, uploadedFileNamesToIds);

    return history;
  }

  @NotNull
  public String generateUniqueMetaData(
      long fieldId, String docName, String globalID, String fieldName, String fieldGlobalID) {
    String metaData = "RSPACE_" + docName + "_" + globalID + "_" + fieldName + "_" + fieldGlobalID;
    Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField =
        externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            fieldId, GALAXY);
    if (allDataUploadedToGalaxyForThisRSpaceField != null
        && !allDataUploadedToGalaxyForThisRSpaceField.isEmpty()) {
      Set<String> allHistoryNames =
          allDataUploadedToGalaxyForThisRSpaceField.stream()
              .map(ExternalWorkFlowData::getExtContainerName)
              .collect(HashSet::new, HashSet::add, HashSet::addAll);
      metaData = metaData + "_" + allHistoryNames.size();
    }
    return metaData;
  }

  private String getApiKeyForUser(User user) {
    String apiKey =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), IntegrationsHandler.GALAXY_APP_NAME)
            .get()
            .getAccessToken();
    return apiKey;
  }

  /**
   * Data uploaded from a field in RSpace generates a new 'history' in Galaxy on every upload. That
   * history can then have multiple invocations. We are interested in all data uploaded to Galaxy
   * for a given RSpace 'FIELD' in a document and then in all invocations in all histories created
   * in Galaxy that are using the rspace data We look for the rspace data using a UUID which Galaxy
   * creates, instead of the datasetid which Galaxy also creates. This is because each time a
   * dataset is used in an invocation, a new dataset is created and it has a new datasetId, however
   * it keeps the UUID from the original data uploads. The UUID is stored in our 'generic'
   * ExternalWorkFlow schema as 'extSecondaryID'
   *
   * <p>The GalaxySummary Report displays a single row of data for every invocation, with data names
   * all in one display row, comma separated.
   *
   * @param fieldId
   * @param user
   * @return List<GalaxySummaryStatusReport> may be null
   * @throws IOException
   */
  public List<GalaxySummaryStatusReport> getSummaryGalaxyDataForRSpaceField(long fieldId, User user)
      throws IOException {
    Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField =
        externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            fieldId, GALAXY);
    if (!allDataUploadedToGalaxyForThisRSpaceField.isEmpty()) {
      Set<String> historyIds =
          findAllHistoryIdsForThisData(allDataUploadedToGalaxyForThisRSpaceField);
      String apiKey = getApiKeyForUser(user);
      List<WorkflowInvocationResponse> allTopLevelInvocationsForThisData = new ArrayList<>();
      for (String historyId : historyIds) {
        List<WorkflowInvocationResponse> topLevelInvocationsInAHistory =
            getTopLevelInvocationsInAHistory(historyId, apiKey);
        allTopLevelInvocationsForThisData.addAll(topLevelInvocationsInAHistory);
      }
      if (!allTopLevelInvocationsForThisData.isEmpty()) {
        // check if any invocations have been persisted for this RSpace field
        Set<ExternalWorkFlowInvocation> persistedInvocations =
            findInvocationsFromData(allDataUploadedToGalaxyForThisRSpaceField);
        Set<GalaxyInvocationDetails> allInvocationDetails = new LinkedHashSet<>();
        allTopLevelInvocationsForThisData.forEach(
            (invocation) -> {
              GalaxyInvocationDetails thisGalaxyInvocationDetails = new GalaxyInvocationDetails();
              Optional<ExternalWorkFlowInvocation> existing =
                  persistedInvocations.stream()
                      .filter(
                          persistedInvocation ->
                              persistedInvocation.getExtId().equals(invocation.getInvocationId()))
                      .findFirst();
              if (existing.isPresent()) {
                ExternalWorkFlowInvocation persistedInvocation = existing.get();
                allInvocationDetails.add(thisGalaxyInvocationDetails);
                thisGalaxyInvocationDetails.setPersistedInvocation(persistedInvocation);
                thisGalaxyInvocationDetails.setInvocation(invocation);
                if (!persistedInvocation.getStatus().equals(invocation.getState())) {
                  persistedInvocation.setStatus(invocation.getState());
                  externalWorkFlowDataManager.save(persistedInvocation);
                }

              } else {
                getLatestInvocationDataFromGalaxy(
                    invocation,
                    apiKey,
                    allDataUploadedToGalaxyForThisRSpaceField,
                    allInvocationDetails,
                    thisGalaxyInvocationDetails);
              }
            });
        return GalaxySummaryStatusReport.createForForInvocationsAndForDataAlone(
            allInvocationDetails, allDataUploadedToGalaxyForThisRSpaceField);
      }
      // no invocations were present
      return GalaxySummaryStatusReport.createForForDataAlone(
          allDataUploadedToGalaxyForThisRSpaceField);
    }
    return null;
  }

  private void getLatestInvocationDataFromGalaxy(
      WorkflowInvocationResponse invocation,
      String apiKey,
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField,
      Set<GalaxyInvocationDetails> allInvocationDetails,
      GalaxyInvocationDetails thisGalaxyInvocationDetails) {
    WorkflowInvocationStepStatusResponse details =
        client.getWorkflowInvocationData(apiKey, invocation.getInvocationId());
    Map<String, WorkflowInvocationStepInput> inputs = details.getInputs();
    List<ExternalWorkFlowData> allMatchingDataForThisInvocation = new ArrayList<>();
    inputs
        .values()
        .forEach(
            input -> {
              // hdca stands for HistoryDatasetCollectionAssociation - this is the
              // datatype Galaxy creates when data is used in an invocation
              if (input.getSrc().equals("hdca")) {
                HistoryDatasetCollectionAssociation underlyingDataHDCA =
                    client.getDataSetCollectionDetails(
                        apiKey, invocation.getHistoryId(), input.getId());
                for (DatasetCollectionElement element : underlyingDataHDCA.getElements()) {
                  String elementUuid = element.getObject().getUuid();
                  Optional<ExternalWorkFlowData> matchingData =
                      allDataUploadedToGalaxyForThisRSpaceField.stream()
                          .filter(data -> data.getExtSecondaryId().equals(elementUuid))
                          .findFirst();
                  boolean dataMatched = matchingData.isPresent();
                  if (dataMatched) {
                    allMatchingDataForThisInvocation.add(matchingData.get());
                    thisGalaxyInvocationDetails.setInvocation(invocation);
                    allInvocationDetails.add(thisGalaxyInvocationDetails);
                    if (thisGalaxyInvocationDetails.getDataUsedInInvocation() == null) {
                      thisGalaxyInvocationDetails.setDataUsedInInvocation(new ArrayList<>());
                    }
                    thisGalaxyInvocationDetails.getDataUsedInInvocation().add(element.getObject());
                    WorkflowInvocationReport workflowInvocationReport =
                        client.getWorkflowInvocationReport(apiKey, invocation.getInvocationId());
                    thisGalaxyInvocationDetails.setWorkflowName(
                        workflowInvocationReport.getTitle());
                  }
                }
              }
            });
    saveInvocation(invocation, thisGalaxyInvocationDetails, allMatchingDataForThisInvocation);
  }

  private Set<ExternalWorkFlowInvocation> findInvocationsFromData(
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField) {
    List<ExternalWorkFlowInvocation> invocations = new ArrayList<>();
    allDataUploadedToGalaxyForThisRSpaceField.forEach(
        data -> invocations.addAll(data.getExternalWorkflowInvocations()));
    return new HashSet<>(invocations);
  }

  private void saveInvocation(
      WorkflowInvocationResponse invocation,
      GalaxyInvocationDetails galaxyInvocationDetails,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation) {

    externalWorkFlowDataManager.saveExternalWorkfFlowInvocation(
        invocation.getWorkflowId(),
        galaxyInvocationDetails.getWorkflowName(),
        invocation.getInvocationId(),
        allMatchingDataForThisInvocation,
        invocation.getState());
  }

  private Set<String> findAllHistoryIdsForThisData(
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField) {
    Set<String> historyIDs = new HashSet<>();
    for (ExternalWorkFlowData data : allDataUploadedToGalaxyForThisRSpaceField) {
      historyIDs.add(data.getExtContainerID());
    }
    return historyIDs;
  }

  public List<WorkflowInvocationResponse> getTopLevelInvocationsInAHistory(
      String historyId, String apiKey) throws IOException {
    return client.getTopLevelInvocationsInAHistory(apiKey, historyId);
  }
}

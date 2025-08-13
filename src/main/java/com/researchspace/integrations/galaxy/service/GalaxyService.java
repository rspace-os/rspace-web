package com.researchspace.integrations.galaxy.service;

import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService.GALAXY;
import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceContainerType.FIELD;
import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceDataType.LOCAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.files.service.FileStore;
import com.researchspace.galaxy.client.GalaxyClient;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.galaxy.model.output.upload.DataSet;
import com.researchspace.galaxy.model.output.upload.DatasetCollectionElement;
import com.researchspace.galaxy.model.output.upload.HistoryDatasetCollectionAssociation;
import com.researchspace.galaxy.model.output.upload.UploadFileResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationReport;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationStepInput;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationStepStatusResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationSummaryStatusResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowOverallStates;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.model.field.Field;
import com.researchspace.model.oauth.UserConnection;
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
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
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

  @Autowired private UserConnectionManager userConnectionManager;
  @Autowired private ExternalWorkFlowDataManager externalWorkFlowDataManager;

  @Value("${galaxy.server.config}")
  private String mapString;

  @Getter private List<GalaxyAliasToServer> aliasServerPairs;

  @PostConstruct
  private void init() throws JsonProcessingException {
    if (StringUtils.isBlank(mapString)) {
      this.aliasServerPairs = new ArrayList<>();
    } else {
      ObjectMapper objectMapper = new ObjectMapper();
      aliasServerPairs =
          objectMapper.readValue(mapString, new TypeReference<List<GalaxyAliasToServer>>() {});
    }
  }

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
   * Browser localStorage will cache this data and it will only be requeried if no localstorage data
   * exists for the field (for example: new document, shared document, localstorage erased, user
   * switches browser).
   */
  public boolean galaxyDataExists(long fieldId) {
    Set<ExternalWorkFlowData> data =
        externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            fieldId, GALAXY);
    return data != null && !data.isEmpty();
  }

  /**
   * Uploads data to galaxy, creates a dataset for that data with a name matching: "RSPACE_" +
   * docName + "_" + globalID + "_" + fieldName + "_" + fieldGlobalID; saves the upload response
   * data IDs from Galaxy into the RSpace DB, so we can later retrieve the data from Galaxy. Finally
   * uploads an annotation to the data in Galaxy for each file uploaded, with a link to the RSpace
   * page and a download link using the uploaded file's globalID.
   */
  public History setUpDataInGalaxyFor(
      User user,
      long recordId,
      long fieldId,
      long[] selectedAttachmentIds,
      String rspaceServerAddress,
      String targetAlias)
      throws IOException {
    BaseRecord theDocument = recordManager.getRecordWithFields(recordId, user);
    Field field = fieldManager.get(fieldId, user).get();
    String fieldName = field.getName();
    String fieldGlobalID = field.getOid().toString();
    String globalID = ((StructuredDocument) theDocument).getOidWithVersion().toString();
    String docName = theDocument.getEditInfo().getName();
    String metaData = generateUniqueMetaData(fieldId, docName, globalID, fieldName, fieldGlobalID);
    Map<String, String> keyByAlias = findApiKeyByGalaxyServerForUser(user);
    String galaxyUrl = getGalaxyUrlFrom(aliasServerPairs, targetAlias) + "/api";
    String apiKey = keyByAlias.get(targetAlias);
    History history = client.createNewHistory(apiKey, metaData, galaxyUrl);
    Map<String, String> uploadedFileNamesToIds = new HashMap<>();
    for (long attachmentId : selectedAttachmentIds) {
      EcatMediaFile ecatMediaFile =
          baseRecordManager.retrieveMediaFile(user, attachmentId, null, null, null);
      File attachmentFile = fileStore.findFile(ecatMediaFile.getFileProperty());
      UploadFileResponse uploadFileResponse =
          client.uploadFile(history.getId(), apiKey, attachmentFile, galaxyUrl);
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
              getGalaxyUrlFrom(aliasServerPairs, targetAlias));
      externalWorkFlowDataManager.save(externalWorkFlowData);
      String documentLink =
          rspaceServerAddress + "/workspace/editor/structuredDocument/" + theDocument.getId();
      String galleryLink = rspaceServerAddress + "/gallery/item/" + ecatMediaFile.getId();
      String downloadLink =
          rspaceServerAddress + "/globalId/" + ecatMediaFile.getGlobalIdentifier();
      String annotation =
          "Document: " + documentLink + " Data: " + galleryLink + " Download: " + downloadLink;
      client.putAnnotationOnDataset(
          history.getId(), uploadedFileGalaxyID, annotation, apiKey, galaxyUrl);
    }
    client.createDatasetCollection(
        apiKey, history.getId(), metaData, uploadedFileNamesToIds, galaxyUrl);

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

  private Map<String, String> findApiKeyByGalaxyServerForUser(User user) {
    Map<String, String> apiKeyByGalaxyServer = new HashMap<>();
    List<UserConnection> connectionsToGalaxy =
        userConnectionManager.findListByUserNameProviderName(
            user.getUsername(), IntegrationsHandler.GALAXY_APP_NAME);
    for (UserConnection connection : connectionsToGalaxy) {
      apiKeyByGalaxyServer.put(connection.getId().getProviderUserId(), connection.getAccessToken());
    }
    return apiKeyByGalaxyServer;
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
   * @return List<GalaxySummaryStatusReport> may be null
   */
  public List<GalaxySummaryStatusReport> getSummaryGalaxyDataForRSpaceField(long fieldId, User user)
      throws IOException {
    Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField =
        externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            fieldId, GALAXY);
    if (!allDataUploadedToGalaxyForThisRSpaceField.isEmpty()) {

      Map<String, String> keyByAlias = findApiKeyByGalaxyServerForUser(user);
      List<GalaxySummaryStatusReport> allServers = new ArrayList<>();
      for (String alias : keyByAlias.keySet()) {
        String baseUrl = getGalaxyUrlFrom(aliasServerPairs, alias);
        Set<ExternalWorkFlowData> dataUploadedForThisGalaxyServer =
            allDataUploadedToGalaxyForThisRSpaceField.stream()
                .filter(data -> data.getBaseUrl().equals(baseUrl))
                .collect(Collectors.toSet());
        if (!dataUploadedForThisGalaxyServer.isEmpty()) {
          Set<String> historyIds = findAllHistoryIdsForThisData(dataUploadedForThisGalaxyServer);
          String apiKey = keyByAlias.get(alias);
          String galaxyUrl = baseUrl + "/api";
          allServers.addAll(
              getGalaxySummaryForServer(
                  historyIds, apiKey, dataUploadedForThisGalaxyServer, galaxyUrl));
        }
      }
      return allServers;
    }
    return new ArrayList<>();
  }

  public GalaxyInvocationAndDataCounts getGalaxyInvocationCountForRSpaceField(
      long fieldId, User user) throws IOException {
    GalaxyInvocationAndDataCounts giadc = new GalaxyInvocationAndDataCounts();
    Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField =
        externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            fieldId, GALAXY);
    if (!allDataUploadedToGalaxyForThisRSpaceField.isEmpty()) {

      Map<String, String> keyByAlias = findApiKeyByGalaxyServerForUser(user);
      List<WorkflowInvocationResponse> allInvocations = new ArrayList<>();
      for (String alias : keyByAlias.keySet()) {
        String baseUrl = getGalaxyUrlFrom(aliasServerPairs, alias);
        Set<ExternalWorkFlowData> dataUploadedForThisGalaxyServer =
            allDataUploadedToGalaxyForThisRSpaceField.stream()
                .filter(data -> data.getBaseUrl().equals(baseUrl))
                .collect(Collectors.toSet());
        if (!dataUploadedForThisGalaxyServer.isEmpty()) {
          giadc.setDataCount(giadc.getDataCount() + dataUploadedForThisGalaxyServer.size());
          Set<String> historyIds = findAllHistoryIdsForThisData(dataUploadedForThisGalaxyServer);
          String apiKey = keyByAlias.get(alias);
          String galaxyUrl = getGalaxyUrlFrom(aliasServerPairs, alias) + "/api";
          allInvocations.addAll(getGalaxyInvocationsForServer(historyIds, apiKey, galaxyUrl));
        }
      }
      giadc.setInvocationCount(allInvocations.size());
    } else {
      giadc.setInvocationCount(0);
    }
    return giadc;
  }

  private static String getGalaxyUrlFrom(List<GalaxyAliasToServer> aliasServerPairs, String alias) {
    return aliasServerPairs.stream()
        .filter(sp -> sp.getAlias().equals(alias))
        .findFirst()
        .get()
        .getUrl();
  }

  @NotNull
  private List<WorkflowInvocationResponse> getGalaxyInvocationsForServer(
      Set<String> historyIds, String apiKey, String galaxyUrl) throws IOException {
    List<WorkflowInvocationResponse> allTopLevelInvocationsForThisData = new ArrayList<>();
    for (String historyId : historyIds) {
      List<WorkflowInvocationResponse> topLevelInvocationsInAHistory =
          getTopLevelInvocationsInAHistory(historyId, apiKey, galaxyUrl);
      allTopLevelInvocationsForThisData.addAll(topLevelInvocationsInAHistory);
    }
    return allTopLevelInvocationsForThisData;
  }

  @NotNull
  private List<GalaxySummaryStatusReport> getGalaxySummaryForServer(
      Set<String> historyIds,
      String apiKey,
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField,
      String galaxyUrl)
      throws IOException {
    List<WorkflowInvocationResponse> allTopLevelInvocationsForThisData =
        getGalaxyInvocationsForServer(historyIds, apiKey, galaxyUrl);

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
            String finalCalculatedState = calculateStateFor(invocation, apiKey, galaxyUrl);
            thisGalaxyInvocationDetails.setState(finalCalculatedState);
            if (existing.isPresent()) {
              ExternalWorkFlowInvocation persistedInvocation = existing.get();
              allInvocationDetails.add(thisGalaxyInvocationDetails);
              thisGalaxyInvocationDetails.setPersistedInvocation(persistedInvocation);
              thisGalaxyInvocationDetails.setInvocation(invocation);
              if (!persistedInvocation.getStatus().equals(finalCalculatedState)) {
                persistedInvocation.setStatus(finalCalculatedState);
                externalWorkFlowDataManager.save(persistedInvocation);
              }

            } else {
              getLatestInvocationDataFromGalaxy(
                  invocation,
                  apiKey,
                  allDataUploadedToGalaxyForThisRSpaceField,
                  allInvocationDetails,
                  thisGalaxyInvocationDetails,
                  galaxyUrl);
            }
          });
      return GalaxySummaryStatusReport.createForInvocationsAndForDataAlone(
          allInvocationDetails, allDataUploadedToGalaxyForThisRSpaceField);
    }
    // no invocations were present
    return GalaxySummaryStatusReport.createPerHistoryForDataUnusedByAnyInvocation(
        allDataUploadedToGalaxyForThisRSpaceField);
  }

  private String calculateStateFor(
      WorkflowInvocationResponse invocation, String apiKey, String galaxyUrl) {
    WorkflowInvocationSummaryStatusResponse summary =
        client.getWorkflowInvocatioSummaryStatus(apiKey, invocation.getInvocationId(), galaxyUrl);
    WorkflowOverallStates jobStates = summary.getStates();
    String jobStatesStatus = jobStates.getState().toString();
    String populatedState = summary.getPopulatedState();
    // Assume that CANCELLED overrules ERROR
    // AND both CANCELLED and ERROR always overrules complete or running state.
    return jobStatesStatus.equalsIgnoreCase("Cancelled")
        ? jobStatesStatus
        : populatedState.equalsIgnoreCase("CANCELLED")
            ? "Cancelled"
            : populatedState.equalsIgnoreCase("failed") ? "Failed" : jobStatesStatus;
  }

  private void getLatestInvocationDataFromGalaxy(
      WorkflowInvocationResponse invocation,
      String apiKey,
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField,
      Set<GalaxyInvocationDetails> allInvocationDetails,
      GalaxyInvocationDetails thisGalaxyInvocationDetails,
      String galaxyUrl) {
    WorkflowInvocationStepStatusResponse details =
        client.getWorkflowInvocationData(apiKey, invocation.getInvocationId(), galaxyUrl);
    Map<String, WorkflowInvocationStepInput> inputs = details.getInputs();
    List<ExternalWorkFlowData> allMatchingDataForThisInvocation = new ArrayList<>();
    inputs
        .values()
        .forEach(
            input -> {
              // hdca stands for HistoryDatasetCollectionAssociation - this is the
              // datatype Galaxy creates when data that is part of a 'Collection' is used in an
              // invocation
              if (input.getSrc().equals("hdca")) {
                HistoryDatasetCollectionAssociation underlyingDataHDCA =
                    client.getDataSetCollectionDetails(
                        apiKey, invocation.getHistoryId(), input.getId(), galaxyUrl);
                List<DatasetCollectionElement> elements = underlyingDataHDCA.getElements();
                List<String> elementUUIDs = new ArrayList<>();
                searchElementsForMatchesToRSpaceData(
                    invocation,
                    apiKey,
                    allDataUploadedToGalaxyForThisRSpaceField,
                    allInvocationDetails,
                    thisGalaxyInvocationDetails,
                    allMatchingDataForThisInvocation,
                    elements,
                    elementUUIDs,
                    galaxyUrl);
              } else if (input
                  .getSrc()
                  .equals("hda")) { // data that is not part of any 'Collection' is HDA type
                DataSet dataset = client.getDataSetDetails(apiKey, input.getId(), galaxyUrl);
                String uuid = dataset.getUuid();
                Optional<ExternalWorkFlowData> matchingData =
                    allDataUploadedToGalaxyForThisRSpaceField.stream()
                        .filter(data -> data.getExtSecondaryId().equals(uuid))
                        .findFirst();
                boolean dataMatched = matchingData.isPresent();
                if (dataMatched) {
                  addDataDetailsForDataSet(
                      invocation,
                      apiKey,
                      allInvocationDetails,
                      thisGalaxyInvocationDetails,
                      dataset,
                      allMatchingDataForThisInvocation,
                      matchingData,
                      galaxyUrl);
                }
              }
            });
    if (!allMatchingDataForThisInvocation.isEmpty()) {
      saveInvocation(invocation, thisGalaxyInvocationDetails, allMatchingDataForThisInvocation);
    }
  }

  private void searchElementsForMatchesToRSpaceData(
      WorkflowInvocationResponse invocation,
      String apiKey,
      Set<ExternalWorkFlowData> allDataUploadedToGalaxyForThisRSpaceField,
      Set<GalaxyInvocationDetails> allInvocationDetails,
      GalaxyInvocationDetails thisGalaxyInvocationDetails,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      List<DatasetCollectionElement> elements,
      List<String> elementUUIDs,
      String galaxyUrl) {
    for (DatasetCollectionElement element : elements) {
      if (element.getObject().getModelClass().equals("DatasetCollection")) {
        searchElementsForMatchesToRSpaceData(
            invocation,
            apiKey,
            allDataUploadedToGalaxyForThisRSpaceField,
            allInvocationDetails,
            thisGalaxyInvocationDetails,
            allMatchingDataForThisInvocation,
            element.getObject().getElements(),
            elementUUIDs,
            galaxyUrl);
      } else {
        elementUUIDs.add(element.getObject().getUuid());
      }
      boolean dataMatched;
      for (String elementUuid : elementUUIDs) {
        Optional<ExternalWorkFlowData> matchingData =
            allDataUploadedToGalaxyForThisRSpaceField.stream()
                .filter(data -> data.getExtSecondaryId().equals(elementUuid))
                .findFirst();
        dataMatched = matchingData.isPresent();
        if (dataMatched && !allMatchingDataForThisInvocation.contains(matchingData.get())) {
          addDataDetailsForDataSetCollection(
              invocation,
              apiKey,
              allInvocationDetails,
              thisGalaxyInvocationDetails,
              element,
              allMatchingDataForThisInvocation,
              matchingData,
              galaxyUrl);
        }
      }
    }
  }

  private void addDataDetailsForDataSetCollection(
      WorkflowInvocationResponse invocation,
      String apiKey,
      Set<GalaxyInvocationDetails> allInvocationDetails,
      GalaxyInvocationDetails thisGalaxyInvocationDetails,
      DatasetCollectionElement element,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      Optional<ExternalWorkFlowData> matchingData,
      String galaxyUrl) {
    addDataDetailsForDataSetOrDataSetCollection(
        invocation,
        apiKey,
        allInvocationDetails,
        thisGalaxyInvocationDetails,
        allMatchingDataForThisInvocation,
        matchingData,
        element,
        null,
        galaxyUrl);
  }

  private void addDataDetailsForDataSetOrDataSetCollection(
      WorkflowInvocationResponse invocation,
      String apiKey,
      Set<GalaxyInvocationDetails> allInvocationDetails,
      GalaxyInvocationDetails thisGalaxyInvocationDetails,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      Optional<ExternalWorkFlowData> matchingData,
      DatasetCollectionElement element,
      DataSet dataset,
      String galaxyUrl) {
    allMatchingDataForThisInvocation.add(matchingData.get());
    thisGalaxyInvocationDetails.setInvocation(invocation);
    allInvocationDetails.add(thisGalaxyInvocationDetails);
    if (element != null) {
      if (thisGalaxyInvocationDetails.getDataSetCollectionsUsedInInvocation() == null) {
        thisGalaxyInvocationDetails.setDataSetCollectionsUsedInInvocation(new ArrayList<>());
      }
      thisGalaxyInvocationDetails.getDataSetCollectionsUsedInInvocation().add(element.getObject());
    } else if (dataset != null) {
      if (thisGalaxyInvocationDetails.getDataSetsUsedInInvocation() == null) {
        thisGalaxyInvocationDetails.setDataSetsUsedInInvocation(new ArrayList<>());
      }
      thisGalaxyInvocationDetails.getDataSetsUsedInInvocation().add(dataset);
    }
    WorkflowInvocationReport workflowInvocationReport =
        client.getWorkflowInvocationReport(apiKey, invocation.getInvocationId(), galaxyUrl);
    thisGalaxyInvocationDetails.setWorkflowName(workflowInvocationReport.getTitle());
  }

  private void addDataDetailsForDataSet(
      WorkflowInvocationResponse invocation,
      String apiKey,
      Set<GalaxyInvocationDetails> allInvocationDetails,
      GalaxyInvocationDetails thisGalaxyInvocationDetails,
      DataSet dataset,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      Optional<ExternalWorkFlowData> matchingData,
      String galaxyUrl) {
    addDataDetailsForDataSetOrDataSetCollection(
        invocation,
        apiKey,
        allInvocationDetails,
        thisGalaxyInvocationDetails,
        allMatchingDataForThisInvocation,
        matchingData,
        null,
        dataset,
        galaxyUrl);
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
      String historyId, String apiKey, String galaxyUrl) throws IOException {
    return client.getTopLevelInvocationsInAHistory(apiKey, historyId, galaxyUrl);
  }
}

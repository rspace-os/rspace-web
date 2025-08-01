package com.researchspace.integrations.galaxy.service;

import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.API_KEY;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DATASET_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_INVOCATION_STATE;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_UUID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.ECATMEDIA_FILE1_GLOBAL_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.ECATMEDIA_FILE1_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.HISTORY_DATASET_ASSOCIATION_DATA_SET_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.HISTORY_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.INPUT_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.INVOCATION_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.RSPACE_DOCUMENT_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.WORKFLOW_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createHistoryDatasetAssociation;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createMatchingHDA;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createMatchingHDCA;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createMatchingHDCAWithNestedData;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createNonMatchingHDCA;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createWorkFlowInvocationReport;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.createWorkflowInvocationSummaryStatusResponseForState;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.makeGalaxyDataAssertions;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.makeGalaxyDataAssertionsWithInvocation;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.files.service.FileStore;
import com.researchspace.galaxy.client.GalaxyClient;
import com.researchspace.galaxy.model.output.history.History;
import com.researchspace.galaxy.model.output.upload.HistoryDatasetAssociation;
import com.researchspace.galaxy.model.output.upload.UploadFileResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationSummaryStatusResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowOverallStates.OverAllState;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceContainerType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceDataType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.model.field.Field;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalWorkFlowDataManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserConnectionManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

public class GalaxyServiceTest {

  public static final String HISTORY_NAME_ON_GALAXY =
      "RSPACE_rspaceDocument_GL4444v4_field1_GL1111v1";
  @Mock private GalaxyClient client;
  @Mock private BaseRecordManager baseRecordManager;
  @Mock private FileStore fileStore;
  @Mock private RecordManager recordManager;
  @Mock private FieldManager fieldManager;
  @Mock private UserConnectionManager userConnectionManager;
  @Mock private ExternalWorkFlowDataManager externalWorkFlowDataManager;
  @Mock private User user;
  @Mock private UserConnection userConnection;
  @Mock private StructuredDocument rspaceDocument;
  @Mock private Field field;
  @Mock private History history;
  @Mock private EcatMediaFile ecatMediaFile1;
  @Mock private EcatMediaFile ecatMediaFile2;
  @Mock private FileProperty fileProperyt1;
  @Mock private FileProperty fileProperyt2;
  private File attachmentFile1;
  private File attachmentFile2;
  @Mock private UploadFileResponse uploadFileResponse1;
  @Mock private UploadFileResponse uploadFileResponse2;
  private HistoryDatasetAssociation historyDatasetAssociation1;
  private HistoryDatasetAssociation historyDatasetAssociation2;
  @Captor private ArgumentCaptor<ExternalWorkFlowData> externalWorkFlowDataArgumentCaptor;

  @Captor
  private ArgumentCaptor<ExternalWorkFlowInvocation> externalWorkFlowInvocationArgumentCaptor;

  private GalaxyService galaxyService;
  private WorkflowInvocationSummaryStatusResponse workflowInvocationSummaryStatusResponse;

  @Before
  public void setUp() throws IOException {
    openMocks(this);
    when(user.getUsername()).thenReturn("userName");
    attachmentFile1 =
        Paths.get("/src/test/resources/TestResources/file_store/galaxyAttachmentFiles/test1.txt")
            .toFile();
    attachmentFile2 =
        Paths.get("/src/test/resources/TestResources/file_store/galaxyAttachmentFiles/test2.txt")
            .toFile();
    galaxyService =
        new GalaxyService(client, baseRecordManager, fileStore, recordManager, fieldManager);
    ReflectionTestUtils.setField(galaxyService, "userConnectionManager", userConnectionManager);
    ReflectionTestUtils.setField(
        galaxyService, "externalWorkFlowDataManager", externalWorkFlowDataManager);
    ReflectionTestUtils.setField(galaxyService, "baseUrl", "https://galaxy.eu");
    when(userConnectionManager.findByUserNameProviderName(anyString(), eq("GALAXY")))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn(API_KEY);
    when(recordManager.getRecordWithFields(eq(1L), eq(user))).thenReturn(rspaceDocument);
    when(rspaceDocument.getOidWithVersion()).thenReturn(new GlobalIdentifier("GL4444v4"));
    when(fieldManager.get(eq(1L), eq(user))).thenReturn(Optional.of(field));
    when(field.getName()).thenReturn("field1");
    when(field.getOid()).thenReturn(new GlobalIdentifier("GL1111v1"));
    EditInfo editInfo = new EditInfo();
    editInfo.setName("rspaceDocument");
    when(rspaceDocument.getEditInfo()).thenReturn(editInfo);
    when(rspaceDocument.getId()).thenReturn(RSPACE_DOCUMENT_ID);
    when(client.createNewHistory(eq(API_KEY), eq(HISTORY_NAME_ON_GALAXY))).thenReturn(history);
    when(history.getId()).thenReturn(HISTORY_ID_1);
    when(history.getName()).thenReturn(HISTORY_NAME_ON_GALAXY);
    when(baseRecordManager.retrieveMediaFile(eq(user), eq(1L), eq(null), eq(null), eq(null)))
        .thenReturn(ecatMediaFile1);
    when(baseRecordManager.retrieveMediaFile(eq(user), eq(2L), eq(null), eq(null), eq(null)))
        .thenReturn(ecatMediaFile2);
    when(ecatMediaFile1.getFileProperty()).thenReturn(fileProperyt1);
    when(ecatMediaFile2.getFileProperty()).thenReturn(fileProperyt2);
    when(ecatMediaFile1.getId()).thenReturn(ECATMEDIA_FILE1_ID);
    when(ecatMediaFile1.getGlobalIdentifier()).thenReturn(ECATMEDIA_FILE1_GLOBAL_ID);
    when(fileStore.findFile(eq(fileProperyt1))).thenReturn(attachmentFile1);
    when(fileStore.findFile(eq(fileProperyt2))).thenReturn(attachmentFile2);
    when(client.uploadFile(eq(HISTORY_ID_1), eq(API_KEY), eq(attachmentFile1)))
        .thenReturn(uploadFileResponse1);
    when(client.uploadFile(eq(HISTORY_ID_1), eq(API_KEY), eq(attachmentFile2)))
        .thenReturn(uploadFileResponse2);
    historyDatasetAssociation1 = createHistoryDatasetAssociation(1);
    historyDatasetAssociation2 = createHistoryDatasetAssociation(2);
    when(uploadFileResponse1.getOutputs()).thenReturn(List.of(historyDatasetAssociation1));
    when(uploadFileResponse2.getOutputs()).thenReturn(List.of(historyDatasetAssociation2));
    workflowInvocationSummaryStatusResponse =
        createWorkflowInvocationSummaryStatusResponseForState(OverAllState.Running, "ok");
    when(client.getWorkflowInvocatioSummaryStatus(API_KEY, INVOCATION_ID_1))
        .thenReturn(workflowInvocationSummaryStatusResponse);
  }

  @Test
  public void shouldUploadDataToGalaxyWhenSetUpDataInGalaxy() throws IOException {
    galaxyService.setUpDataInGalaxyFor(user, 1L, 1L, new long[] {1L, 2L}, "serverAddress");
    verify(client).createNewHistory(eq(API_KEY), eq(HISTORY_NAME_ON_GALAXY));
    verify(client).uploadFile(eq(HISTORY_ID_1), eq(API_KEY), eq(attachmentFile1));
  }

  @Test
  public void
      shouldIncrementHistoryNameByOneWhenExistingUploadDataForThatFieldWhenSetUpDataInGalaxy()
          throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowData(
            HISTORY_ID_1, DATASET_ID_1, "Test History");

    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));
    when(client.createNewHistory(eq(API_KEY), eq(HISTORY_NAME_ON_GALAXY + "_1")))
        .thenReturn(history);
    galaxyService.setUpDataInGalaxyFor(user, 1L, 1L, new long[] {1L, 2L}, "serverAddress");
    verify(client).createNewHistory(eq(API_KEY), eq(HISTORY_NAME_ON_GALAXY + "_1"));
    verify(client).uploadFile(eq(HISTORY_ID_1), eq(API_KEY), eq(attachmentFile1));
  }

  @Test
  public void shouldSaveDataWhenUploadToGalaxyIsCompleteWhenSetUpDataInGalaxy() throws IOException {
    galaxyService.setUpDataInGalaxyFor(user, 1L, 1L, new long[] {1L, 2L}, "serverAddress");
    verify(externalWorkFlowDataManager, times(2))
        .save(externalWorkFlowDataArgumentCaptor.capture());
    ExternalWorkFlowData externalWorkFlowData1 =
        externalWorkFlowDataArgumentCaptor.getAllValues().get(0);
    ExternalWorkFlowData externalWorkFlowData2 =
        externalWorkFlowDataArgumentCaptor.getAllValues().get(1);
    assertEquals("historyDatasetAssociationDataSetId1", externalWorkFlowData1.getExtId());
    assertEquals("historyDatasetAssociationDataSetId2", externalWorkFlowData2.getExtId());
    assertEquals(ExternalService.GALAXY, externalWorkFlowData1.getExternalService());
    assertEquals("test1.txt", externalWorkFlowData1.getExtName());
    assertEquals(HISTORY_ID_1, externalWorkFlowData1.getExtContainerID());
    assertEquals("https://galaxy.eu", externalWorkFlowData1.getBaseUrl());
    assertEquals("field1", externalWorkFlowData1.getRspacecontainerName());
    assertEquals(HISTORY_NAME_ON_GALAXY, externalWorkFlowData1.getExtContainerName());
    assertEquals("historyDatasetAssociationUuid1", externalWorkFlowData1.getExtSecondaryId());
    assertEquals(1L, externalWorkFlowData1.getRspacecontainerid());
    assertEquals(RspaceContainerType.FIELD, externalWorkFlowData1.getRspaceContainerType());
    assertEquals(RspaceDataType.LOCAL, externalWorkFlowData1.getRspaceDataType());
    assertEquals("field1", externalWorkFlowData1.getRspacecontainerName());
  }

  @Test
  public void shouldCreateDataSetCollectionInGalaxyWhenSetUpDataInGalaxy() throws IOException {
    galaxyService.setUpDataInGalaxyFor(user, 1L, 1L, new long[] {1L, 2L}, "serverAddress");
    Map<String, String> uploadedFileNamesToIds = new HashMap<>();
    uploadedFileNamesToIds.put(
        "historyDatasetAssociationName1", "historyDatasetAssociationDataSetId1");
    uploadedFileNamesToIds.put(
        "historyDatasetAssociationName2", "historyDatasetAssociationDataSetId2");
    verify(client)
        .createDatasetCollection(
            eq(API_KEY), eq(HISTORY_ID_1), eq(HISTORY_NAME_ON_GALAXY), eq(uploadedFileNamesToIds));
  }

  @Test
  public void shouldPutAnnotationsInGalaxyWhenSetUpDataInGalaxy() throws IOException {
    galaxyService.setUpDataInGalaxyFor(user, 1L, 1L, new long[] {1L, 2L}, "serverAddress");
    verify(client)
        .putAnnotationOnDataset(
            eq(HISTORY_ID_1),
            eq(HISTORY_DATASET_ASSOCIATION_DATA_SET_ID + 1),
            eq(
                "Document: serverAddress/workspace/editor/structuredDocument/444 Data:"
                    + " serverAddress/gallery/item/123 Download:"
                    + " serverAddress/globalId/ecatmediafile1GlobalId"),
            eq(API_KEY));
  }

  @Test
  public void testGetSummaryGalaxyDataForRSpaceFieldWhenNoDataUploaded() throws IOException {
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Collections.emptySet());

    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);

    assertEquals(0, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verifyNoMoreInteractions(client);
  }

  @Test
  public void testGetSummaryGalaxyDataForRSpaceFieldWhenDataUploadedNoInvocations()
      throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowData(
            HISTORY_ID_1, DATASET_ID_1, "Test History");

    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));

    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.emptyList());

    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);

    assertEquals(1, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verify(client).getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1);
    verify(userConnectionManager)
        .findByUserNameProviderName(user.getUsername(), IntegrationsHandler.GALAXY_APP_NAME);
    makeGalaxyDataAssertions(result);
  }

  @Test
  public void
      testGetSummaryGalaxyDataForRSpaceFieldWhenDataUploadedInvocationsNotMatchingAndNoInvocationsPreexisting()
          throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowData(
            HISTORY_ID_1, DATASET_ID_1, "Test History");
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));

    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    when(client.getWorkflowInvocationData(API_KEY, INVOCATION_ID_1))
        .thenReturn(ExternalWorkFlowTestMother.createWorkflowInvocationHDCAStepStatusResponse());
    when(client.getDataSetCollectionDetails(API_KEY, HISTORY_ID_1, "inputId"))
        .thenReturn(createNonMatchingHDCA());
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);

    assertEquals(1, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    makeGalaxyDataAssertions(result);
    verify(externalWorkFlowDataManager, never())
        .saveExternalWorkfFlowInvocation(
            any(String.class), isNull(), any(String.class), eq(List.of()), any(String.class));
  }

  @Test
  public void
      testGetSummaryGalaxyDataForRSpaceFieldWhenDataUploadedInvocationsAreMatchingAndNoInvocationsPreexisting()
          throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowData(
            HISTORY_ID_1, DATASET_ID_1, "Test History");
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(
            ExternalWorkFlowTestMother.INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));

    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    when(client.getWorkflowInvocationData(API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1))
        .thenReturn(ExternalWorkFlowTestMother.createWorkflowInvocationHDCAStepStatusResponse());
    when(client.getDataSetCollectionDetails(API_KEY, HISTORY_ID_1, INPUT_ID))
        .thenReturn(createMatchingHDCA(DEFAULT_UUID));
    when(client.getWorkflowInvocationReport(API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1))
        .thenReturn(createWorkFlowInvocationReport());
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    assertEquals(1, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verify(externalWorkFlowDataManager)
        .saveExternalWorkfFlowInvocation(
            WORKFLOW_ID_1,
            ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED,
            ExternalWorkFlowTestMother.INVOCATION_ID_1,
            List.of(testExternalWorkFlowData),
            DEFAULT_INVOCATION_STATE);
    makeGalaxyDataAssertionsWithInvocation(result.get(0));
  }

  @Test // this tests the data shape when a 'list of pairs dataset is created'. The HDCA object
  // has an underlying dataset collection and the actual data elements are inside that
  public void
      testGetSummaryGalaxyDataForRSpaceFieldWhenDataUploadedNewInvocationsAreMatchingAndNestedDataELementsAreDataSetCollections()
          throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowData(
            HISTORY_ID_1, DATASET_ID_1, "Test History");
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(
            ExternalWorkFlowTestMother.INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));
    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    when(client.getWorkflowInvocationData(API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1))
        .thenReturn(ExternalWorkFlowTestMother.createWorkflowInvocationHDCAStepStatusResponse());
    when(client.getDataSetCollectionDetails(API_KEY, HISTORY_ID_1, INPUT_ID))
        .thenReturn(createMatchingHDCAWithNestedData(DEFAULT_UUID));
    when(client.getWorkflowInvocationReport(API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1))
        .thenReturn(createWorkFlowInvocationReport());
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    assertEquals(1, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verify(externalWorkFlowDataManager)
        .saveExternalWorkfFlowInvocation(
            WORKFLOW_ID_1,
            ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED,
            ExternalWorkFlowTestMother.INVOCATION_ID_1,
            List.of(testExternalWorkFlowData),
            DEFAULT_INVOCATION_STATE);
    makeGalaxyDataAssertionsWithInvocation(result.get(0));
  }

  @Test
  public void
      testGetSummaryGalaxyDataForRSpaceFieldWhenDataUploadedHDADATAInvocationsAreMatchingAndNoInvocationsPreexisting()
          throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowData(
            HISTORY_ID_1, DATASET_ID_1, "Test History");
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(
            ExternalWorkFlowTestMother.INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));

    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    when(client.getWorkflowInvocationData(API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1))
        .thenReturn(ExternalWorkFlowTestMother.createWorkflowInvocationHDAStepStatusResponse());
    when(client.getDataSetDetails(API_KEY, INPUT_ID)).thenReturn(createMatchingHDA(DEFAULT_UUID));
    when(client.getWorkflowInvocationReport(API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1))
        .thenReturn(createWorkFlowInvocationReport());
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    assertEquals(1, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verify(externalWorkFlowDataManager)
        .saveExternalWorkfFlowInvocation(
            WORKFLOW_ID_1,
            ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED,
            ExternalWorkFlowTestMother.INVOCATION_ID_1,
            List.of(testExternalWorkFlowData),
            DEFAULT_INVOCATION_STATE);
    makeGalaxyDataAssertionsWithInvocation(result.get(0));
  }

  @Test
  public void testGetSummaryGalaxyDataForRSpaceFieldWhenInvocationsPreexisting()
      throws IOException {
    // Verify that data is queried from the database and requests to galaxy are not performed.
    // Also verify that any new 'state' seen in the GalaxyInvocationresponse is saved to the DB
    // ExternalWorkFlowInvocation data
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithInvocations(
            HISTORY_ID_1,
            DATASET_ID_1,
            "Test History",
            "new"); // the state returned by the test mother for the invocation response is
    // 'RUNNING'
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));

    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    verifyNoMoreInteractions(client);
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    assertEquals(1, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verify(externalWorkFlowDataManager).save(externalWorkFlowInvocationArgumentCaptor.capture());
    ExternalWorkFlowInvocation savedInvocation =
        externalWorkFlowInvocationArgumentCaptor.getValue();
    assertEquals(DEFAULT_INVOCATION_STATE, savedInvocation.getStatus());
    assertEquals(INVOCATION_ID_1, savedInvocation.getExtId());
    makeGalaxyDataAssertionsWithInvocation(result.get(0));
  }

  @Test
  public void testGetSummaryGalaxyDataForRSpaceFieldWhenInvocationsStateIsCancelled()
      throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData = createExternalWorkFlowData();
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));
    when(client.getWorkflowInvocatioSummaryStatus(API_KEY, INVOCATION_ID_1))
        .thenReturn(
            createWorkflowInvocationSummaryStatusResponseForState(OverAllState.Cancelled, "ok"));
    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    verify(externalWorkFlowDataManager).save(externalWorkFlowInvocationArgumentCaptor.capture());
    ExternalWorkFlowInvocation savedInvocation =
        externalWorkFlowInvocationArgumentCaptor.getValue();
    assertEquals("Cancelled", savedInvocation.getStatus());
    when(client.getWorkflowInvocatioSummaryStatus(API_KEY, INVOCATION_ID_1))
        .thenReturn(
            createWorkflowInvocationSummaryStatusResponseForState(
                OverAllState.Failed, "Cancelled"));
    result = galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    verify(externalWorkFlowDataManager).save(externalWorkFlowInvocationArgumentCaptor.capture());
    savedInvocation = externalWorkFlowInvocationArgumentCaptor.getValue();
    assertEquals("Cancelled", savedInvocation.getStatus());
  }

  @Test
  public void testGetSummaryGalaxyDataForRSpaceFieldWhenInvocationsStateIsFailed()
      throws IOException {
    ExternalWorkFlowData testExternalWorkFlowData = createExternalWorkFlowData();
    WorkflowInvocationResponse workflowInvocationResponse =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(INVOCATION_ID_1, HISTORY_ID_1);
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(Set.of(testExternalWorkFlowData));
    when(client.getWorkflowInvocatioSummaryStatus(API_KEY, INVOCATION_ID_1))
        .thenReturn(
            createWorkflowInvocationSummaryStatusResponseForState(OverAllState.Failed, "ok"));
    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(Collections.singletonList(workflowInvocationResponse));
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    verify(externalWorkFlowDataManager).save(externalWorkFlowInvocationArgumentCaptor.capture());
    ExternalWorkFlowInvocation savedInvocation =
        externalWorkFlowInvocationArgumentCaptor.getValue();
    assertEquals("Failed", savedInvocation.getStatus());
    when(client.getWorkflowInvocatioSummaryStatus(API_KEY, INVOCATION_ID_1))
        .thenReturn(
            createWorkflowInvocationSummaryStatusResponseForState(OverAllState.Running, "Failed"));
    result = galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    verify(externalWorkFlowDataManager).save(externalWorkFlowInvocationArgumentCaptor.capture());
    savedInvocation = externalWorkFlowInvocationArgumentCaptor.getValue();
    assertEquals("Failed", savedInvocation.getStatus());
  }

  private static ExternalWorkFlowData createExternalWorkFlowData() {
    ExternalWorkFlowData testExternalWorkFlowData =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithInvocations(
            HISTORY_ID_1,
            DATASET_ID_1,
            "Test History",
            "new"); // the state returned by the test mother for the invocation response is
    // 'Running'
    return testExternalWorkFlowData;
  }

  @Test
  public void
      testGetSummaryGalaxyDataForRSpaceFieldWhenInvocationsPreexistingAndSomeNotPreexisting()
          throws IOException {
    // verify that data is queried from the database for pre-existing invocations and
    // requested from Galaxy for new invocations; which are then saved to the DB
    ExternalWorkFlowData testExternalWorkFlowDataPreexisting = createExternalWorkFlowData();
    ExternalWorkFlowData testExternalWorkFlowDataNotPreexisting =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithInvocations(
            HISTORY_ID_1 + "_not_pre",
            DATASET_ID_1 + "_not_pre",
            "Test History" + "_not_pre",
            "new");
    WorkflowInvocationResponse workflowInvocationResponsePreexisting =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(INVOCATION_ID_1, HISTORY_ID_1);
    WorkflowInvocationResponse workflowInvocationResponseNotPreexisting =
        ExternalWorkFlowTestMother.createWorkflowInvocationResponse(
            INVOCATION_ID_1 + "_not_pre", HISTORY_ID_1 + "_not_pre");
    when(externalWorkFlowDataManager.findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY))
        .thenReturn(
            Set.of(testExternalWorkFlowDataPreexisting, testExternalWorkFlowDataNotPreexisting));

    when(client.getTopLevelInvocationsInAHistory(API_KEY, HISTORY_ID_1))
        .thenReturn(
            List.of(
                workflowInvocationResponsePreexisting, workflowInvocationResponseNotPreexisting));

    when(client.getWorkflowInvocationData(
            API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1 + "_not_pre"))
        .thenReturn(ExternalWorkFlowTestMother.createWorkflowInvocationHDCAStepStatusResponse());
    when(client.getDataSetCollectionDetails(API_KEY, HISTORY_ID_1 + "_not_pre", INPUT_ID))
        .thenReturn(createMatchingHDCA(DATASET_ID_1 + "_not_pre" + "_uuid"));
    when(client.getWorkflowInvocationReport(
            API_KEY, ExternalWorkFlowTestMother.INVOCATION_ID_1 + "_not_pre"))
        .thenReturn(createWorkFlowInvocationReport());
    when(client.getWorkflowInvocatioSummaryStatus(API_KEY, INVOCATION_ID_1 + "_not_pre"))
        .thenReturn(workflowInvocationSummaryStatusResponse);
    List<GalaxySummaryStatusReport> result =
        galaxyService.getSummaryGalaxyDataForRSpaceField(1L, user);
    assertEquals(2, result.size());
    verify(externalWorkFlowDataManager)
        .findWorkFlowDataByRSpaceContainerIdAndServiceType(
            1L, ExternalWorkFlowData.ExternalService.GALAXY);
    verify(externalWorkFlowDataManager).save(externalWorkFlowInvocationArgumentCaptor.capture());
    ExternalWorkFlowInvocation savedPreExistingInvocation =
        externalWorkFlowInvocationArgumentCaptor.getValue();

    assertEquals(DEFAULT_INVOCATION_STATE, savedPreExistingInvocation.getStatus());
    assertEquals(INVOCATION_ID_1, savedPreExistingInvocation.getExtId());
    verify(externalWorkFlowDataManager)
        .saveExternalWorkfFlowInvocation(
            WORKFLOW_ID_1,
            WORKFLOWTHATWASUSED,
            INVOCATION_ID_1 + "_not_pre",
            List.of(testExternalWorkFlowDataNotPreexisting),
            DEFAULT_INVOCATION_STATE);
    makeGalaxyDataAssertionsWithInvocation(result.get(0));
    makeGalaxyDataAssertionsWithInvocation(result.get(1), "_not_pre");
  }
}

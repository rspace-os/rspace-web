package com.researchspace.integrations.galaxy.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.researchspace.galaxy.model.output.upload.DataSet;
import com.researchspace.galaxy.model.output.upload.DatasetCollection;
import com.researchspace.galaxy.model.output.upload.DatasetCollectionElement;
import com.researchspace.galaxy.model.output.upload.HistoryDatasetAssociation;
import com.researchspace.galaxy.model.output.upload.HistoryDatasetCollectionAssociation;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationReport;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationResponse;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationStepInput;
import com.researchspace.galaxy.model.output.workflow.WorkflowInvocationStepStatusResponse;
import com.researchspace.integrations.galaxy.service.GalaxySummaryStatusReport.GalaxyInvocationStatus;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalWorkFlowDataBuilder;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceContainerType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceDataType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joda.time.DateTime;

public class ExternalWorkFlowTestMother {
  public static final long ECATMEDIA_FILE1_ID = 123L;
  public static final String ECATMEDIA_FILE1_GLOBAL_ID = "ecatmediafile1GlobalId";
  public static final long RSPACE_DOCUMENT_ID = 444L;
  public static final String HISTORY_ID_1 = "history-id-1";
  public static final String WORKFLOWTHATWASUSED = "WORKFLOWTHATWASUSED";
  public static final String INVOCATION_ID_1 = "invocation-id-1";
  public static final String API_KEY = "API_KEY";
  public static final Date CREATED_ON = DateTime.parse("1970").toDate();
  public static final String WORKFLOW_ID_1 = "workflow-id-1";
  public static final String DEFAULT_INVOCATION_STATE = "RUNNING";
  public static final String DEFAULT_DATA_NAME = "default-name";
  public static final String HDCA = "hdca";
  public static final String HDA = "hda";
  public static final String INPUT_ID = "inputId";
  public static final String DEFAULT_DATA_EXITID = "default-exitid";
  public static final String DEFAULT_BASEURL = "default-baseurl";
  public static final String DEFAULT_EXT_CONTAINER_ID = "default-ext-container-id";
  public static final String DEFAULT_EXT_CONTAINER_NAME = "default-ext-container-name";
  public static final String DEFAULT_UUID = "default-ext-secondary-id";
  public static final long RSPACECONTAINER_ID = 0L;
  public static final String DEFAULT_RSPACE_CONTAINER_NAME = "default-rspace-container-name";
  public static final long RSPACEDATAID = 0L;
  public static final String DATASET_ID_1 = "dataset-id-1";
  public static final String HISTORY_DATASET_ASSOCIATION_DATA_SET_ID =
      "historyDatasetAssociationDataSetId";
  public static final String HISTORY_DATASET_ASSOCIATION_UUID = "historyDatasetAssociationUuid";
  public static final String HISTORY_DATASET_ASSOCIATION_NAME = "historyDatasetAssociationName";

  private static ExternalWorkFlowDataBuilder getBuilderWithNonNullValuesSet() {
    ExternalWorkFlowDataBuilder builder = ExternalWorkFlowData.builder();
    builder
        .extName(DEFAULT_DATA_NAME)
        .extId(DEFAULT_DATA_EXITID)
        .baseUrl(DEFAULT_BASEURL)
        .externalService(ExternalService.GALAXY)
        .extContainerID(DEFAULT_EXT_CONTAINER_ID)
        .extContainerName(DEFAULT_EXT_CONTAINER_NAME)
        .extSecondaryId(DEFAULT_UUID)
        .rspacecontainerId(RSPACECONTAINER_ID)
        .rspaceContainerName(DEFAULT_RSPACE_CONTAINER_NAME)
        .rspaceContainerType(RspaceContainerType.FIELD)
        .rspacedataid(RSPACEDATAID)
        .rspaceDataType(RspaceDataType.LOCAL);
    return builder;
  }

  public static HistoryDatasetAssociation createHistoryDatasetAssociation(int num) {
    HistoryDatasetAssociation historyDatasetAssociation = new HistoryDatasetAssociation();
    historyDatasetAssociation.setDatasetId(HISTORY_DATASET_ASSOCIATION_DATA_SET_ID + num);
    historyDatasetAssociation.setUuid(HISTORY_DATASET_ASSOCIATION_UUID + num);
    historyDatasetAssociation.setName(HISTORY_DATASET_ASSOCIATION_NAME + num);
    return historyDatasetAssociation;
  }

  public static WorkflowInvocationStepStatusResponse createWorkflowInvocationHDCAStepStatusResponse() {
    WorkflowInvocationStepStatusResponse wissr = new WorkflowInvocationStepStatusResponse();
    Map<String, WorkflowInvocationStepInput> inputs = new HashMap<>();
    WorkflowInvocationStepInput input = new WorkflowInvocationStepInput();
    input.setSrc(HDCA);
    input.setId(INPUT_ID);
    inputs.put("1", input);
    wissr.setInputs(inputs);
    return wissr;
  }
  public static WorkflowInvocationStepStatusResponse createWorkflowInvocationHDAStepStatusResponse() {
    WorkflowInvocationStepStatusResponse wissr = new WorkflowInvocationStepStatusResponse();
    Map<String, WorkflowInvocationStepInput> inputs = new HashMap<>();
    WorkflowInvocationStepInput input = new WorkflowInvocationStepInput();
    input.setSrc(HDA);
    input.setId(INPUT_ID);
    inputs.put("1", input);
    wissr.setInputs(inputs);
    return wissr;
  }

  public static WorkflowInvocationResponse createWorkflowInvocationResponse(
      String invocationId, String historyId) {
    WorkflowInvocationResponse invocationResponse = new WorkflowInvocationResponse();
    invocationResponse.setInvocationId(invocationId);
    invocationResponse.setHistoryId(historyId);
    invocationResponse.setWorkflowId(WORKFLOW_ID_1);
    invocationResponse.setState(DEFAULT_INVOCATION_STATE);
    invocationResponse.setCreateTime(CREATED_ON);
    return invocationResponse;
  }

  public static ExternalWorkFlowData createExternalWorkFlowData(String containerId, String extId) {
    ExternalWorkFlowDataBuilder builder = getBuilderWithNonNullValuesSet();
    builder.extContainerID(containerId);
    builder.extId(extId);
    return builder.build();
  }

  public static ExternalWorkFlowData createExternalWorkFlowData(
      String extContainerId, String extId, String historyName) {
    ExternalWorkFlowDataBuilder builder = getBuilderWithNonNullValuesSet();
    builder.extContainerID(extContainerId);
    builder.extId(extId);
    builder.extContainerName(historyName);
    return builder.build();
  }

  public static ExternalWorkFlowData createExternalWorkFlowDataWithInvocations(
      String extContainerId, String extId, String historyName, String invocationStatus) {
    ExternalWorkFlowDataBuilder builder = getBuilderWithNonNullValuesSet();
    builder.extContainerID(extContainerId);
    builder.extId(extId);
    builder.extContainerName(historyName);
    builder.extSecondaryId(extId + "_uuid");
    ExternalWorkFlowData toReturn = builder.build();
    ExternalWorkFlow externalWorkFlow = new ExternalWorkFlow("extID", WORKFLOWTHATWASUSED, "");
    ExternalWorkFlowInvocation invocation =
        new ExternalWorkFlowInvocation(
            INVOCATION_ID_1, Set.of(toReturn), invocationStatus, externalWorkFlow);
    return toReturn;
  }

  public static WorkflowInvocationReport createWorkFlowInvocationReport() {
    WorkflowInvocationReport wir = new WorkflowInvocationReport();
    wir.setTitle(ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED);
    return wir;
  }

  public static HistoryDatasetCollectionAssociation createMatchingHDCA(String secondaryId) {
    return createHDCA(secondaryId);
  }
  public static DataSet createMatchingHDA(String secondaryId) {
    return createHDA(secondaryId);
  }

  private static DataSet createHDA(String secondaryId) {
    DataSet hda = new DataSet();
    hda.setUuid(secondaryId);
    hda.setName(DEFAULT_DATA_NAME);
    return hda;
  }

  public static HistoryDatasetCollectionAssociation createNonMatchingHDCA() {
    return createHDCA(null);
  }

  public static HistoryDatasetCollectionAssociation createHDCA(String secondaryId) {
    HistoryDatasetCollectionAssociation hdca = new HistoryDatasetCollectionAssociation();
    List<DatasetCollectionElement> elements = new ArrayList<>();
    DatasetCollectionElement element = new DatasetCollectionElement();
    DatasetCollection elementObject = new DatasetCollection();
    if (secondaryId != null) {
      elementObject.setUuid(secondaryId);
    } else {
      elementObject.setUuid("different-uuid");
    }
    elementObject.setName(DEFAULT_DATA_NAME);
    element.setObject(elementObject);
    elements.add(element);
    hdca.setElements(elements);
    return hdca;
  }


  public static void makeGalaxyDataAssertions(List<GalaxySummaryStatusReport> result) {
    assertEquals(DEFAULT_DATA_NAME, result.get(0).getGalaxyDataNames());
    assertEquals("Test History", result.get(0).getGalaxyHistoryName());
    assertEquals(HISTORY_ID_1, result.get(0).getGalaxyHistoryId());
    assertEquals("default-baseurl", result.get(0).getGalaxyBaseUrl());
    assertEquals("default-rspace-container-name", result.get(0).getRspaceFieldName());
    assertNull(result.get(0).getGalaxyInvocationId());
    assertNull(result.get(0).getCreatedOn());
    assertNull(result.get(0).getGalaxyInvocationName());
    assertNull(result.get(0).getGalaxyInvocationStatus());
  }

  public static void makeGalaxyDataAssertionsWithInvocation(GalaxySummaryStatusReport result) {
    makeGalaxyDataAssertionsWithInvocation(result, "");
  }

  public static void makeGalaxyDataAssertionsWithInvocation(
      GalaxySummaryStatusReport result, String suffix) {
    assertEquals(DEFAULT_DATA_NAME, result.getGalaxyDataNames());
    assertEquals("Test History" + suffix, result.getGalaxyHistoryName());
    assertEquals(HISTORY_ID_1 + suffix, result.getGalaxyHistoryId());
    assertEquals("default-baseurl", result.getGalaxyBaseUrl());
    assertEquals("default-rspace-container-name", result.getRspaceFieldName());
    assertEquals(INVOCATION_ID_1 + suffix, result.getGalaxyInvocationId());
    assertEquals(CREATED_ON, result.getCreatedOn());
    assertEquals(ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED, result.getGalaxyInvocationName());
    assertEquals(GalaxyInvocationStatus.IN_PROGRESS, result.getGalaxyInvocationStatus());
  }
}

package com.researchspace.archive;

import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_BASEURL;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_DATA_EXITID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_DATA_NAME;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.DEFAULT_EXT_CONTAINER_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.HISTORY_ID_1;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.NEW_INVOCATION_STATUS;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.RSPACECONTAINER_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.WF_EXT_ID;
import static com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother.WORKFLOWTHATWASUSED;
import static com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService.GALAXY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.researchspace.integrations.galaxy.service.ExternalWorkFlowTestMother;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceContainerType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceDataType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.service.archive.export.externalWorkFlow.LinkableExternalWorkFlowData;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

public class ArchiveExternalWorkFlowDataTest {

  public static final long DATA_ID = 1L;
  public static final long DEFAULT_INVOCATION_ID = 2L;
  public static final long DEFAULT_WORKFLOW_ID = 1L;
  private LinkableExternalWorkFlowData item;
  private String archiveLink;
  @Mock private ArchivalField archiveField;
  private Set<ArchiveExternalWorkFlowInvocation> invocations;

  @Before
  public void setUp() {
    invocations = new HashSet<>();
    initMocks(this);
    ExternalWorkFlowData data =
        ExternalWorkFlowTestMother.createExternalWorkFlowDataWithInvocations(
            DEFAULT_EXT_CONTAINER_ID, DEFAULT_DATA_EXITID, HISTORY_ID_1, NEW_INVOCATION_STATUS);
    ReflectionTestUtils.setField(data, "id", DATA_ID);
    ExternalWorkFlowInvocation invocation = data.getExternalWorkflowInvocations().iterator().next();
    ExternalWorkFlow workflow = invocation.getExternalWorkFlow();
    ReflectionTestUtils.setField(workflow, "id", DEFAULT_WORKFLOW_ID);
    ReflectionTestUtils.setField(invocation, "id", DEFAULT_INVOCATION_ID);
    archiveLink = "rspace_data_file_name";
    item = new LinkableExternalWorkFlowData(data);
    when(archiveField.getFieldId()).thenReturn(1L);
    when(archiveField.getExternalWorkFlowInvocations()).thenReturn(invocations);
  }

  @Test
  public void testCorrectDataPassedIntoArchiveNoExistingInvocationInArchiveField() {
    ArchiveExternalWorkFlowData data =
        new ArchiveExternalWorkFlowData(item, archiveLink, archiveField);
    assertEquals(DATA_ID, data.getId());

    assertEquals(DEFAULT_EXT_CONTAINER_ID, data.getExtContainerId());

    assertEquals(DEFAULT_DATA_EXITID, data.getExtId());
    assertEquals(RSPACECONTAINER_ID, data.getRspaceDataId());

    assertEquals(GALAXY.name(), data.getExternalService());
    assertEquals(DEFAULT_DATA_NAME, data.getExtName());
    assertEquals(DEFAULT_DATA_EXITID, data.getExtId());
    assertEquals(DEFAULT_DATA_EXITID + "_uuid", data.getExtSecondaryId());
    assertEquals(DEFAULT_EXT_CONTAINER_ID, data.getExtContainerId());
    assertEquals(HISTORY_ID_1, data.getExtContainerName());
    assertEquals(DEFAULT_BASEURL, data.getBaseUrl());
    assertEquals(RspaceDataType.LOCAL.name(), data.getRspaceDataType());
    assertEquals(RspaceContainerType.FIELD.name(), data.getRspaceContainerType());

    ArchiveExternalWorkFlowInvocation invocation = makeInvocationAssertions();

    assertEquals(archiveLink, data.getFileName());
    assertEquals(archiveLink, data.getLinkFile());

    assertEquals(archiveField.getFieldId(), data.getParentId());

    makeWorkFlowAssertions(invocation);
  }

  private static void makeWorkFlowAssertions(ArchiveExternalWorkFlowInvocation invocation) {
    ArchiveExternalWorkFlow workFlow = invocation.getWorkFlowMetaData();
    assertEquals(DEFAULT_WORKFLOW_ID, workFlow.getId());
    assertEquals(WORKFLOWTHATWASUSED, workFlow.getName());
    assertEquals(WF_EXT_ID, workFlow.getExtId());
  }

  @NotNull
  private ArchiveExternalWorkFlowInvocation makeInvocationAssertions() {
    ArchiveExternalWorkFlowInvocation invocation = invocations.iterator().next();
    assertEquals(NEW_INVOCATION_STATUS, invocation.getStatus());

    assertEquals(DEFAULT_WORKFLOW_ID, invocation.getWorkFlowId());

    assertEquals(DEFAULT_INVOCATION_ID, invocation.getId());
    assertEquals(1, invocations.size());
    assertEquals(1, invocation.getDataIds().size());
    assertEquals(1, invocation.getDataIds().iterator().next().longValue());
    return invocation;
  }

  @Test
  public void testCorrectDataPassedIntoArchiveMatchingExistingInvocationInArchiveField() {
    ExternalWorkFlowInvocation existingWFI =
        item.getExternalWorkflowData().getExternalWorkflowInvocations().iterator().next();
    ArchiveExternalWorkFlowInvocation existingInField =
        new ArchiveExternalWorkFlowInvocation(existingWFI, GALAXY.name());
    invocations.add(existingInField);
    ArchiveExternalWorkFlowData data =
        new ArchiveExternalWorkFlowData(item, archiveLink, archiveField);
    makeInvocationAssertions();
    makeWorkFlowAssertions(existingInField);
  }
}

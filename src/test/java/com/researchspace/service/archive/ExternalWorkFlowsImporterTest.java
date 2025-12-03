package com.researchspace.service.archive;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.researchspace.archive.AllArchiveExternalWorkFlowMetaData;
import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchiveExternalWorkFlow;
import com.researchspace.archive.ArchiveExternalWorkFlowData;
import com.researchspace.archive.ArchiveExternalWorkFlowInvocation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceContainerType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.RspaceDataType;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.ExternalWorkFlowDataManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class ExternalWorkFlowsImporterTest {

  private static final String OLD_FIELD_NAME = "old field name";
  private static final String NEW_FIELD_NAME = "new field name";
  @Mock
  private StructuredDocument newDoc;
  @Mock
  private ArchivalDocumentParserRef ref;
  private Map<String, EcatMediaFile> oldIdToNewGalleryItem;
  @Mock
  private ExternalWorkFlowDataManager externalWorkFlowDataManager;
  private ExternalWorkFlowsImporter importer;
  @Mock
  private ArchivalDocument archivalDoc;
  private AllArchiveExternalWorkFlowMetaData allArchiveExternalWorkFlows;
  private ArchiveExternalWorkFlowData oldData;
  private ArchiveExternalWorkFlowInvocation oldInvocation;
  private ArchiveExternalWorkFlow archiveExternalWorkFlow;
  @Mock
  private ArchivalField oldField;
  private List<ArchivalField> listFields;
  @Mock
  private Field newField;
  @Captor
  private ArgumentCaptor<ExternalWorkFlowData> externalWorkFlowDataArgumentCaptor;
  private ArchiveExternalWorkFlowInvocation invocationToAnotherWF;
  private ArchiveExternalWorkFlowInvocation umatchedDataInvocation;
  private ArchiveExternalWorkFlow archiveExternalWorkFlowOther;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    listFields = List.of(oldField);
    oldIdToNewGalleryItem = new HashMap<>();
    importer = new ExternalWorkFlowsImporter();
    oldData = ArchiveExternalWorkFlowDataTestMother.makeDefault();
    oldInvocation = ArchiveExternalWorkFlowInvocationTestMother.makeDefault();
    invocationToAnotherWF = ArchiveExternalWorkFlowInvocationTestMother.withWFId(6L);
    umatchedDataInvocation = ArchiveExternalWorkFlowInvocationTestMother.withDataId(6L);
    archiveExternalWorkFlow = ArchiveExternalWorkFlowTestMother.makeDefault();
    archiveExternalWorkFlowOther = ArchiveExternalWorkFlowTestMother.withIdAppendedToValues(6L);
    allArchiveExternalWorkFlows = new AllArchiveExternalWorkFlowMetaData();
    when(ref.getArchivalDocument()).thenReturn(archivalDoc);
    when(oldField.getExternalWorkFlowData()).thenReturn(Set.of(oldData));
    when(oldField.getExternalWorkFlowInvocations()).thenReturn(Set.of(oldInvocation));
    when(oldField.getFieldName()).thenReturn(OLD_FIELD_NAME);
    when(newDoc.getField(eq(OLD_FIELD_NAME))).thenReturn(newField);
    when(newField.getName()).thenReturn(NEW_FIELD_NAME);
    when(newField.getId()).thenReturn(1L);
    allArchiveExternalWorkFlows.setWorkFlows(Set.of(archiveExternalWorkFlow));
    when(ref.getArchiveExternalWorkFlowMetaData()).thenReturn(allArchiveExternalWorkFlows);
    when(archivalDoc.getListFields()).thenReturn(List.of(oldField));
  }

  @Test
  public void testNoExternalWorkflowsAndNoExternalWorkFlowData() {
    when(oldField.getExternalWorkFlowData()).thenReturn(new HashSet<>());
    importer.importExternalWorkFlows(newDoc, ref, oldIdToNewGalleryItem,
        externalWorkFlowDataManager);
    verify(externalWorkFlowDataManager, never()).save(any(ExternalWorkFlowData.class));
  }

  @Test
  public void testOneExternalWorkflowsAndNoInvocations() {
    when(oldField.getExternalWorkFlowInvocations()).thenReturn(new HashSet<>());
    importer.importExternalWorkFlows(newDoc, ref, oldIdToNewGalleryItem,
        externalWorkFlowDataManager);
    ExternalWorkFlowData data = makeExternalWFDataAssertions();
    assertEquals(0, data.getExternalWorkflowInvocations().size());
  }

  @Test
  public void testOneExternalWorkflowInvocationAndExternalWorkFlowData() {
    importer.importExternalWorkFlows(newDoc, ref, oldIdToNewGalleryItem,
        externalWorkFlowDataManager);
    ExternalWorkFlowData data = makeExternalWFDataAssertions();
    ExternalWorkFlow externalWorkFlow = data.getExternalWorkflowInvocations().iterator().next()
        .getExternalWorkFlow();
    assertEquals(ArchiveExternalWorkFlowTestMother.NAME, externalWorkFlow.getName());
  }

  @Test
  public void testPersistedInvocationOverwritesMatchingImportedInvocationStatus() {

  }

  private ExternalWorkFlowData makeExternalWFDataAssertions() {
    verify(externalWorkFlowDataManager).save(externalWorkFlowDataArgumentCaptor.capture());
    ExternalWorkFlowData data = externalWorkFlowDataArgumentCaptor.getAllValues().get(0);
    assertEquals(ArchiveExternalWorkFlowDataTestMother.EXT_ID, data.getExtId());
    assertEquals(ExternalService.GALAXY, data.getExternalService());
    assertEquals(ArchiveExternalWorkFlowDataTestMother.LINK_FILE, data.getExtName());
    assertEquals(ArchiveExternalWorkFlowDataTestMother.EXT_CONTAINER_ID, data.getExtContainerID());
    assertEquals(ArchiveExternalWorkFlowDataTestMother.BASE_URL, data.getBaseUrl());
    assertEquals(NEW_FIELD_NAME, data.getRspacecontainerName());
    assertEquals(ArchiveExternalWorkFlowDataTestMother.EXT_CONTAINER_NAME,
        data.getExtContainerName());
    assertEquals(ArchiveExternalWorkFlowDataTestMother.EXT_SECONDARY_ID, data.getExtSecondaryId());
    assertEquals(1L, data.getRspacecontainerid());
    assertEquals(RspaceContainerType.FIELD, data.getRspaceContainerType());
    assertEquals(RspaceDataType.LOCAL, data.getRspaceDataType());
    return data;
  }

  @Test
  public void testMultipleAndOneUnmatchedExternalWorkflowInvocationAndExternalWorkFlowData() {
    allArchiveExternalWorkFlows.setWorkFlows(Set.of(archiveExternalWorkFlow, archiveExternalWorkFlowOther));
    when(oldField.getExternalWorkFlowInvocations()).thenReturn(
        Set.of(oldInvocation, invocationToAnotherWF, umatchedDataInvocation));
    importer.importExternalWorkFlows(newDoc, ref, oldIdToNewGalleryItem,
        externalWorkFlowDataManager);
    verify(externalWorkFlowDataManager).save(externalWorkFlowDataArgumentCaptor.capture());
    ExternalWorkFlowData data = externalWorkFlowDataArgumentCaptor.getAllValues().get(0);
    Set<ExternalWorkFlowInvocation> externalWorkFlowInvocations = data.getExternalWorkflowInvocations();
    assertEquals(2, externalWorkFlowInvocations.size());
    List<ExternalWorkFlowInvocation> externalWorkFlowInvocationsWithWF = data.getExternalWorkflowInvocations()
        .stream().filter(wf -> wf.getExternalWorkFlow().getName().equals(ArchiveExternalWorkFlowTestMother.NAME)).collect(
            Collectors.toList());
    assertEquals(1, externalWorkFlowInvocationsWithWF.size());

  }

}

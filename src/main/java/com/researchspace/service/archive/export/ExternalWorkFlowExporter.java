package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchiveExternalWorkFlowDataMetaData;
import com.researchspace.archive.ArchiveExternalWorkFlowInvocationMetaData;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.service.archive.export.externalWorkFlow.LinkableExternalWorkFlowData;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;
import org.apache.commons.io.FileUtils;

/** Exports ExternalWorkFlow data associated with an RSpace Field in a Structured Doc. */
public class ExternalWorkFlowExporter extends AbstractFieldExporter<LinkableExternalWorkFlowData> {

  public ExternalWorkFlowExporter(FieldExporterSupport support) {
    super(support);
  }

  /**
   * External workflow data linked to a field will be written to the export xml.
   *
   * <p>Any associated data will also be included in the export (if it exists - data can be deleted
   * independently of the External Workflow records of its use)
   */
  public void addExternalWorkflowDataToExport(
      FieldExportContext fieldExportContext, FieldContents fieldContents) {
    FieldElementLinkPairs<LinkableExternalWorkFlowData> externalWorkFlowDataLinks =
        new FieldElementLinkPairs<>(LinkableExternalWorkFlowData.class);
    for (ExternalService externalService :
        ExternalService.values()) { // On 19/11/2025, the only external service is GALAXY
      Set<ExternalWorkFlowData> externalWorkFlowData =
          support
              .getExternalWorkFlowDataManager()
              .findWorkFlowDataByRSpaceContainerIdAndServiceType(
                  fieldExportContext.getArchiveField().getFieldId(), externalService);
      for (ExternalWorkFlowData data : externalWorkFlowData) {
        externalWorkFlowDataLinks.add(
            new FieldElementLinkPair<>(new LinkableExternalWorkFlowData(data), ""));
      }
    }
    for (FieldElementLinkPair<LinkableExternalWorkFlowData> linkableExternalWorkFlowDataPair :
        externalWorkFlowDataLinks.getPairs()) {
      fieldExportContext
          .getExportRecordList()
          .getExternalWorkFlows()
          .add(linkableExternalWorkFlowDataPair.getElement().getExternalWorkflowData());
      export(fieldExportContext, linkableExternalWorkFlowDataPair);
    }
  }

  @Override
  void createFieldArchiveObject(
      LinkableExternalWorkFlowData item, String archiveLink, FieldExportContext context) {
    ArchivalField archiveField = context.getArchiveField();
    ExternalWorkFlowData data = item.getExternalWorkflowData();
    ArchiveExternalWorkFlowDataMetaData aEWFMeta = new ArchiveExternalWorkFlowDataMetaData();
    aEWFMeta.setId(item.getId());
    aEWFMeta.setParentId(archiveField.getFieldId());
    aEWFMeta.setFileName(archiveLink);
    aEWFMeta.setLinkFile(archiveLink);
    aEWFMeta.setRspaceDataId(data.getRspacedataid());
    aEWFMeta.setExternalService(data.getExternalService().name());
    aEWFMeta.setExtName(data.getExtName());
    aEWFMeta.setExtId(data.getExtId());
    aEWFMeta.setExtSecondaryId(data.getExtSecondaryId());
    aEWFMeta.setExtContainerId(data.getExtContainerID());
    aEWFMeta.setExtContainerName(data.getExtContainerName());
    aEWFMeta.setBaseUrl(data.getBaseUrl());
    for (ExternalWorkFlowInvocation invocation :
        item.getExternalWorkflowData().getExternalWorkflowInvocations()) {
      ArchiveExternalWorkFlowInvocationMetaData invocationAgm = new ArchiveExternalWorkFlowInvocationMetaData();
      invocationAgm.setId(invocation.getId());
      invocationAgm.setParentId(item.getId());
      invocationAgm.setStatus(invocation.getStatus());
      invocationAgm.setExtId(invocation.getExtId());
      aEWFMeta.getInvocations().add(invocationAgm);
    }
    //    String ext = "png";
    //    agm.setExtension(ext);
    //    agm.setContentType("image/" + ext);
    //    agm.setAnnotation(item.getAnnotations());
    //    agm.setOriginalId(item.getImageId());
    archiveField.addArchivalExternalWorkFlowData(aEWFMeta);
  }

  @Override
  String doUpdateLinkText(
      FieldElementLinkPair<LinkableExternalWorkFlowData> itemPair,
      String replacementUrl,
      FieldExportContext context) {
    return context
        .getArchiveField()
        .getFieldData(); // we do not add ExternalWorkFlowData to the html of the field
  }

  /**
   * The abstract factory being used expects this method to also copy any required resources to the
   * archive and the other implementing classes do this with EcatMedia files.
   */
  @Override
  String getReplacementUrl(FieldExportContext context, LinkableExternalWorkFlowData item)
      throws URISyntaxException, IOException {
    String nameOfGalleryFile = item.getExternalWorkflowData().getExtName();
    long mediaFileID = item.getExternalWorkflowData().getRspacedataid();
    EcatMediaFile theGalleryFileUsedInTheExternalWorkFlow =
        (EcatMediaFile) support.getRecordById(mediaFileID);
    File attachmentFile =
        support.getFileStore().findFile(theGalleryFileUsedInTheExternalWorkFlow.getFileProperty());
    support
        .getDiskSpaceChecker()
        .assertEnoughDiskSpaceToCopyFileIntoArchiveDir(attachmentFile, context.getExportFolder());
    FileUtils.copyFileToDirectory(attachmentFile, context.getRecordFolder());
    return nameOfGalleryFile; // TODO - is there a way to avoid implementing this method and do the
    // file copying elsewhere in the code, or will that cause more
    // problems that it solves?
  }
}

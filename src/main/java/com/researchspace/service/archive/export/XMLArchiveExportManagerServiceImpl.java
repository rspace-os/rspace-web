package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalLinkResolver;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("archiverServiceManager")
public class XMLArchiveExportManagerServiceImpl extends AbstractArchiveExporter
    implements ArchiveExportServiceManager {

  @Autowired private List<ArchiveDataHandler> dataHandlers;

  protected void preZip(
      ArchiveManifest manifest,
      ImmutableExportRecordList exportList,
      IArchiveExportConfig aconfig,
      ExportContext context,
      List<ExportedRecord> archived)
      throws Exception {
    File archiveAssmblyFlder = context.getArchiveAssmblyFlder();
    String archiveId = manifest.generateArchiveIdInManifest();
    context.setArchiveId(archiveId);
    writeManifestFile(manifest, archiveAssmblyFlder);
    writeSchemaFiles(archiveAssmblyFlder);
    writeLinkResolver(aconfig, archiveAssmblyFlder);
    writeFolderTree(exportList, archiveAssmblyFlder);
    writeNfsFiles(archived, archiveAssmblyFlder);
    for (ArchiveDataHandler handler : dataHandlers) {
      handler.archiveData(aconfig, archiveAssmblyFlder);
    }
  }

  @Data
  @XmlRootElement(name = "nfsLinkInfo")
  @AllArgsConstructor
  @NoArgsConstructor
  @XmlAccessorType(XmlAccessType.FIELD)
  static class ArchivalNfsFileList {
    @XmlElementWrapper(name = "nfsLinks")
    @XmlElement
    private Set<ArchivalNfsFile> nfsLink = new HashSet<>();
  }

  // only write file if we have some Nfs Links
  private void writeNfsFiles(List<ExportedRecord> archived, File archiveAssmblyFlder)
      throws Exception {
    File nfsExports = new File(archiveAssmblyFlder, ExportImport.NFS_EXPORT_XML);
    Set<ArchivalNfsFile> allNfs = getAllNfsLinksInExport(archived);
    if (allNfs.size() > 0) {
      ArchivalNfsFileList allNfsWrapper = new ArchivalNfsFileList(allNfs);
      XMLReadWriteUtils.toXML(nfsExports, allNfsWrapper, ArchivalNfsFileList.class);
    }
  }

  private void writeSchemaFiles(File archiveAssmblyFlder) throws JAXBException, IOException {
    File xsdDoc = new File(archiveAssmblyFlder, ExportImport.ZIP_SCHEMA);
    generateDocumentSchema(xsdDoc);
    File xsdForm = new File(archiveAssmblyFlder, ExportImport.ZIP_FORM_SCHEMA);
    generateFormSchema(xsdForm);
  }

  private void generateDocumentSchema(File xsdFile) throws JAXBException, IOException {
    XMLReadWriteUtils.generateSchemaFromXML(xsdFile, ArchivalDocument.class);
  }

  private void generateFormSchema(File xsdFile) throws JAXBException, IOException {
    XMLReadWriteUtils.generateSchemaFromXML(xsdFile, ArchivalForm.class);
  }

  private File writeLinkResolver(IArchiveExportConfig aconfig, File archiveAssmblyFlder)
      throws Exception {
    ArchivalLinkResolver linkResolver = aconfig.getResolver();
    // File rsvF = new File(folder, xmlDoc.getZipName()+"_Linkresolver.xml");
    File rsvF = new File(archiveAssmblyFlder, ExportImport.ZIP_LINK_SOLVER);
    linkResolver.outputXML(rsvF);
    return rsvF;
  }

  private File writeFolderTree(ImmutableExportRecordList exportList, File archiveAssmblyFlder)
      throws Exception {
    File folderXML = new File(archiveAssmblyFlder, ExportImport.FOLDER_TREE);
    XMLReadWriteUtils.toXML(folderXML, (ExportRecordList) exportList, ExportRecordList.class);
    return folderXML;
  }

  @Override
  public boolean isArchiveType(String archiveType) {
    return ArchiveExportConfig.XML.equals(archiveType);
  }
}

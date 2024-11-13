package com.researchspace.service.impl;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.SignatureDao;
import com.researchspace.export.pdf.ExportFormat;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Signature;
import com.researchspace.model.SignatureHashType;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.PostSigningManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.PostArchiveCompletion;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PostRecordSigningExportHash implements PostSigningManager {

  private static final String SIGNED_EXPORTS_FILESTORE_CATEGORY = "signed_exports";
  private @Autowired ExportImport exportImport;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired SignatureDao sigDao;

  @Value("${server.urls.prefix}")
  private String serverURLPrefix;

  private @Autowired CommunicationManager commMgr;
  Logger log = LoggerFactory.getLogger(PostRecordSigningExportHash.class);

  // package scoped for mockito testing.mockito 2.17 no longer sets in mocks by reflection if a
  // constructor is present
  PostRecordSigningExportHash(
      ExportImport exportImport,
      SignatureDao sigDao,
      CommunicationManager commMgr,
      FileStore store) {
    this(exportImport);
    this.commMgr = commMgr;
    this.fileStore = store;
    this.sigDao = sigDao;
  }

  public PostRecordSigningExportHash(ExportImport exportImport) {
    this.exportImport = exportImport;
  }

  static interface ArchiveSignable {
    Optional<ArchiveResult> archiveAndSign() throws InterruptedException, ExecutionException;

    SignatureHashType getSignatureHashType();
  }

  @Data
  @AllArgsConstructor
  class ArchiveSigner implements ArchiveSignable {
    private ArchiveExportConfig config;
    private Record exported;
    private SignatureHashType signatureHashType;

    @Override
    public Optional<ArchiveResult> archiveAndSign()
        throws InterruptedException, ExecutionException {
      ExportSelection exportSelection =
          ExportSelection.createRecordsExportSelection(
              new Long[] {exported.getId()}, new String[] {RecordType.NORMAL.toString()});
      ArchiveResult result =
          exportImport
              .asyncExportSelectionToArchive(
                  exportSelection,
                  config,
                  exported.getOwner(),
                  toUrl(serverURLPrefix),
                  PostArchiveCompletion.NULL)
              .get();
      return Optional.ofNullable(result);
    }
  }

  @Data
  @AllArgsConstructor
  class ExportSigner implements ArchiveSignable {
    private ExportToFileConfig config;
    private Record exported;
    private SignatureHashType signatureHashType;

    @Override
    public Optional<ArchiveResult> archiveAndSign()
        throws InterruptedException, ExecutionException {
      try {
        File result =
            exportImport
                .asyncExportSelectionToPdfForSigning(
                    new Long[] {exported.getId()},
                    new String[] {exported.getName()},
                    new String[] {RecordType.NORMAL.toString()},
                    config,
                    exported.getOwner())
                .get();
        ArchiveResult ar = new ArchiveResult();
        ar.setExportFile(result);
        ar.setArchivedRecords(TransformerUtils.toList(exported));
        return Optional.ofNullable(ar);
      } catch (IOException e) {
        log.error("Error generating PDF", e);
      }
      return Optional.ofNullable(null);
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void postRecordSign(SigningResult signatureResult) {
    Signature signature =
        signatureResult
            .getSignature()
            .orElseThrow(() -> new IllegalStateException("Signature cannot be null"));
    Record exported = signatureResult.getSigned();
    User signer = exported.getOwner();
    ArchiveExportConfig htmlConfig = createConfig(ArchiveExportConfig.HTML);
    ArchiveExportConfig xmlConfig = createConfig(ArchiveExportConfig.XML);
    ExportToFileConfig pdfConfig = new ExportToFileConfig();
    pdfConfig.setExportFormat(ExportFormat.PDF.name());

    ArchiveSignable html = new ArchiveSigner(htmlConfig, exported, SignatureHashType.HTML_EXPORT);
    ArchiveSignable xml = new ArchiveSigner(xmlConfig, exported, SignatureHashType.XML_EXPORT);
    ArchiveSignable pdf = new ExportSigner(pdfConfig, exported, SignatureHashType.PDF_EXPORT);

    List<ArchiveSignable> exportJobs = TransformerUtils.toList(html, xml, pdf);
    for (ArchiveSignable job : exportJobs) {
      log.info(
          "Generating export to {} for signed document {}  - id: {}",
          job.getSignatureHashType(),
          exported.getName(),
          exported.getId());
      try {
        Optional<ArchiveResult> result = job.archiveAndSign();
        if (result.isPresent()) {
          FileProperty fp = createAndSaveFileProperty(signer, job, result.get());
          // create Hash
          Sha256Hash hash = new Sha256Hash(result.get().getExportFile());
          signature.addHash(hash, job.getSignatureHashType(), fp);
          sigDao.save(signature);
        }
      } catch (Exception e) {
        log.error(
            "export to {} failed for signed document {}  - id: {}",
            job.getSignatureHashType(),
            exported.getName(),
            exported.getId());
      }
    }
    commMgr.systemNotify(
        NotificationType.PROCESS_COMPLETED,
        String.format(
            "Snapshot exports of the signed record %s have been generated", exported.getName()),
        signer.getUsername(),
        true);
  }

  private FileProperty createAndSaveFileProperty(
      User signer, ArchiveSignable job, ArchiveResult result) throws IOException {
    FileProperty fp = generateFilePropertyForExportedFile(job, signer, result);
    fileStore.save(fp, result.getExportFile(), FileDuplicateStrategy.AS_NEW);
    return fp;
  }

  private FileProperty generateFilePropertyForExportedFile(
      ArchiveSignable job, User exporter, ArchiveResult result) {
    FileProperty fp = new FileProperty();
    fp.setFileCategory(SIGNED_EXPORTS_FILESTORE_CATEGORY);
    fp.setFileUser(exporter.getUsername());
    fp.setFileOwner(exporter.getUsername());
    fp.setFileGroup(job.getSignatureHashType().toString());
    fp.setFileVersion("v1");
    fp.setFileName(result.getExportFile().getName());
    return fp;
  }

  private ArchiveExportConfig createConfig(String type) {
    ArchiveExportConfig htmlConfig = new ArchiveExportConfig();
    htmlConfig.setExportScope(ExportScope.SELECTION);
    htmlConfig.setArchiveType(type);
    htmlConfig.setMaxLinkLevel(0);
    return htmlConfig;
  }

  private URI toUrl(String serverURLPrefix2) {
    try {
      return new URI(serverURLPrefix2);
    } catch (URISyntaxException e) {
      log.error("Error creating URI: ", e);
      return null;
    }
  }

  void setServerURLPrefix(String serverURLPrefix) {
    this.serverURLPrefix = serverURLPrefix;
  }
}

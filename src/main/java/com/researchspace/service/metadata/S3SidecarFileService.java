package com.researchspace.service.metadata;

import com.researchspace.archive.ArchiveUtils;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsFieldLinkManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RoRService;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesFactory;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes metadata sidecars for an S3 filestore folder and writes them back to S3. Deliberately
 * not a {@code *Manager}: no DAO, so it runs outside a DB transaction; the Linked documents come
 * from {@link NfsFieldLinkManager} in its own. Phase 1: folder-level, current user as sole creator,
 * DataCite the only format.
 */
@Service
@AllArgsConstructor
@Slf4j
public class S3SidecarFileService {

  private static final String SIDECAR_SUFFIX = ".sidecar.yaml";

  private final NfsManager nfsManager;
  private final S3UtilitiesFactory s3UtilitiesFactory;
  private final FilestoreAclChecker aclChecker;
  private final UserExternalIdResolver orcidResolver;
  private final IPropertyHolder propertyHolder;
  private final DataCiteYamlSidecarFileGenerator sidecarFileGenerator;
  private final AuditTrailService auditService;
  private final MessageSourceUtils messages;
  private final RoRService rorService;
  private final NfsFieldLinkManager nfsFieldLinkManager;

  /** Composes and returns a sidecar for the folder without writing anything. */
  public GeneratedSidecarFile preview(Long filestoreId, String folderPath, User user) {
    NfsFileStore filestore = loadS3Filestore(filestoreId);
    aclChecker.assertCanRead(user, filestore.getFileSystem());
    S3Utilities s3 = s3UtilitiesFactory.createS3UtilitiesForNfsConnector(filestore.getFileSystem());
    return compose(s3, prefixFor(filestore, folderPath), user, filestore.getFileSystem());
  }

  /** Composes a sidecar, writes it into the folder, and records the action in the audit trail. */
  public GeneratedSidecarFile save(Long filestoreId, String folderPath, User user) {
    NfsFileStore filestore = loadS3Filestore(filestoreId);
    aclChecker.assertCanWrite(user, filestore.getFileSystem());
    S3Utilities s3 = s3UtilitiesFactory.createS3UtilitiesForNfsConnector(filestore.getFileSystem());
    String prefix = prefixFor(filestore, folderPath);
    GeneratedSidecarFile sidecarFile = compose(s3, prefix, user, filestore.getFileSystem());
    writeToS3(s3, prefix, sidecarFile, user);
    String filestoreName = filestore.getFileSystem().getName();
    auditService.notify(
        new GenericEvent(
            user,
            new SidecarFileAuditEvent(filestoreName, prefix, sidecarFile.getFilename()),
            AuditAction.CREATE,
            "Generated metadata sidecar '%s' for folder '%s' on filestore '%s'"
                .formatted(sidecarFile.getFilename(), prefix, filestoreName)));
    return sidecarFile;
  }

  /**
   * Resolves the folder to its absolute S3 key prefix within the bucket (no leading/trailing
   * slash).
   */
  private String prefixFor(NfsFileStore filestore, String folderPath) {
    return normalizeKeyPrefix(filestore.getAbsolutePath(folderPath));
  }

  private GeneratedSidecarFile compose(
      S3Utilities s3, String prefix, User user, NfsFileSystem fileSystem) {
    List<SidecarFileEntry> files = listFiles(s3, prefix);
    SidecarFileGenerationContext ctx =
        SidecarFileGenerationContext.builder()
            .user(user)
            .orcidId(
                orcidResolver
                    .getExternalIdForUser(user, IdentifierScheme.ORCID)
                    .map(id -> id.getIdentifier())
                    .orElse(null))
            .institutionName(propertyHolder.getCustomerName())
            .rorId(rorService.getSystemRoRValue())
            .bucketName(s3.getBucketName())
            .folderPath(prefix)
            .files(files)
            .linkedDocumentUrls(linkedDocumentUrls(fileSystem, prefix, files, user))
            .build();
    return sidecarFileGenerator.generate(ctx);
  }

  /** Readable documents linking the folder itself or one of its files, once each, oldest first. */
  private List<String> linkedDocumentUrls(
      NfsFileSystem fileSystem, String prefix, List<SidecarFileEntry> files, User user) {
    List<String> paths = new ArrayList<>(List.of(prefix));
    files.forEach(file -> paths.add(file.key()));
    List<String> globalIds =
        nfsFieldLinkManager.getLinkedDocuments(fileSystem, paths, user).stream()
            // documents the user cannot read come back as placeholders without a Global ID
            .filter(info -> info.getOid() != null)
            .map(info -> info.getOid().getIdString())
            .toList();
    if (globalIds.isEmpty()) {
      return globalIds;
    }
    String serverUrl = propertyHolder.getServerUrl();
    return globalIds.stream().map(id -> ArchiveUtils.getAbsoluteGlobalLink(id, serverUrl)).toList();
  }

  /** Files in the folder, skipping subfolders and any existing sidecar, with full S3 keys. */
  private List<SidecarFileEntry> listFiles(S3Utilities s3, String prefix) {
    return s3.listFolderContents(prefix).stream()
        .filter(item -> !item.isFolder())
        .filter(item -> !item.getName().endsWith(SIDECAR_SUFFIX))
        .map(
            item ->
                new SidecarFileEntry(
                    joinKey(prefix, item.getName()),
                    item.getSizeInBytes(),
                    item.getEtag(),
                    item.getStorageClass()))
        .toList();
  }

  // The sidecar endpoints are S3-only; an unknown or non-S3 filestore is a 404, not a 500.
  private NfsFileStore loadS3Filestore(Long filestoreId) {
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    if (filestore == null) {
      throw new NotFoundException(
          messages.getMessage(
              "netFileStores.errors.fileStoreNotFound", new Object[] {filestoreId}));
    }
    if (!NfsClientType.S3.equals(filestore.getFileSystem().getClientType())) {
      throw new NotFoundException(messages.getMessage("netFileStores.sidecarFile.errors.notS3"));
    }
    return filestore;
  }

  private static String joinKey(String prefix, String name) {
    return prefix.isEmpty() ? name : prefix + "/" + name;
  }

  @SneakyThrows
  private void writeToS3(
      S3Utilities s3, String prefix, GeneratedSidecarFile sidecarFile, User user) {
    Path dir = Files.createTempDirectory("rspace-sidecar");
    File file = dir.resolve(sidecarFile.getFilename()).toFile();
    try {
      Files.writeString(file.toPath(), sidecarFile.getContent());
      var attribution =
          new WriteAttribution(user.getUsername(), null, Instant.now()).metadataForRecord(null);
      s3.uploadToS3(prefix, file, attribution);
    } finally {
      // Best-effort cleanup: a temp-file deletion failure must not mask a successful S3 write.
      try {
        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
      } catch (IOException e) {
        log.warn("Could not clean up temp sidecar files in {}", dir, e);
      }
    }
  }

  // S3 key prefixes carry no leading or trailing slash; the bucket root is "". A leading slash
  // arises when the filestore base path is empty, since getAbsolutePath just concatenates.
  private String normalizeKeyPrefix(String path) {
    return path == null ? "" : path.replaceAll("^/+", "").replaceAll("/+$", "");
  }
}

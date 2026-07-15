package com.researchspace.service.metadata;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

/**
 * Composes metadata sidecars for an <b>S3</b> filestore folder and writes them back to S3. Not a
 * transactional {@code *Manager}: it reads no DAO and its only writes are to S3 (plus an audit
 * event), so it deliberately runs outside a database transaction. Phase 1: folder-level, current
 * user as sole creator, DataCite the only format (see {@link DataCiteYamlSidecarGenerator}); the
 * storage is S3-specific, hence the {@code S3} prefix.
 */
@Service
@AllArgsConstructor
public class S3SidecarService {

  private static final String SIDECAR_SUFFIX = ".sidecar.yaml";

  private final NfsManager nfsManager;
  private final S3UtilitiesFactory s3UtilitiesFactory;
  private final FilestoreAclChecker aclChecker;
  private final UserExternalIdResolver orcidResolver;
  private final IPropertyHolder propertyHolder;
  private final DataCiteYamlSidecarGenerator sidecarGenerator;
  private final AuditTrailService auditService;

  /** Composes and returns a sidecar for the folder without writing anything. */
  public GeneratedSidecar preview(Long filestoreId, String folderPath, User user) {
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    aclChecker.assertCanRead(user, filestore.getFileSystem());
    S3Utilities s3 = s3UtilitiesFactory.createS3UtilitiesForNfsConnector(filestore.getFileSystem());
    return compose(s3, prefixFor(filestore, folderPath), user);
  }

  /** Composes a sidecar, writes it into the folder, and records the action in the audit trail. */
  public GeneratedSidecar save(Long filestoreId, String folderPath, User user) {
    NfsFileStore filestore = nfsManager.getNfsFileStore(filestoreId);
    aclChecker.assertCanWrite(user, filestore.getFileSystem());
    S3Utilities s3 = s3UtilitiesFactory.createS3UtilitiesForNfsConnector(filestore.getFileSystem());
    String prefix = prefixFor(filestore, folderPath);
    GeneratedSidecar sidecar = compose(s3, prefix, user);
    writeToS3(s3, prefix, sidecar, user);
    auditService.notify(
        new GenericEvent(
            user,
            sidecar,
            AuditAction.CREATE,
            "Generated metadata sidecar " + sidecar.getFilename()));
    return sidecar;
  }

  /** Resolves the folder to its absolute S3 key prefix within the bucket (no trailing slash). */
  private String prefixFor(NfsFileStore filestore, String folderPath) {
    return stripTrailingSlash(filestore.getAbsolutePath(folderPath));
  }

  private GeneratedSidecar compose(S3Utilities s3, String prefix, User user) {
    SidecarGenerationContext ctx =
        SidecarGenerationContext.builder()
            .user(user)
            .orcidId(
                orcidResolver
                    .getExternalIdForUser(user, IdentifierScheme.ORCID)
                    .map(id -> id.getIdentifier())
                    .orElse(null))
            .institutionName(propertyHolder.getCustomerName())
            .bucketName(s3.getBucketName())
            .folderPath(prefix)
            .files(listFiles(s3, prefix))
            .build();
    return sidecarGenerator.generate(ctx);
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

  private static String joinKey(String prefix, String name) {
    return prefix.isEmpty() ? name : prefix + "/" + name;
  }

  @SneakyThrows
  private void writeToS3(S3Utilities s3, String prefix, GeneratedSidecar sidecar, User user) {
    Path dir = Files.createTempDirectory("rspace-sidecar");
    File file = dir.resolve(sidecar.getFilename()).toFile();
    try {
      Files.writeString(file.toPath(), sidecar.getContent());
      var attribution =
          new WriteAttribution(user.getUsername(), null, Instant.now()).metadataForRecord(null);
      s3.uploadToS3(prefix, file, attribution);
    } finally {
      Files.deleteIfExists(file.toPath());
      Files.deleteIfExists(dir);
    }
  }

  private String stripTrailingSlash(String path) {
    return path == null ? "" : path.replaceAll("/+$", "");
  }
}

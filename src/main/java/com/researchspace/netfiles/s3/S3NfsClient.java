package com.researchspace.netfiles.s3;

import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.DeletableTarget;
import com.researchspace.netfiles.FilestoreAuditMetadata;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.netfiles.WritableNfsClient;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class S3NfsClient extends NfsAbstractClient implements WritableNfsClient {

  final S3Utilities s3Utilities;

  public S3NfsClient(String currentUsername, S3Utilities s3Utilities) {
    super(currentUsername);
    this.s3Utilities = s3Utilities;
  }

  @Override
  public boolean isUserLoggedIn() {
    return true;
  }

  @Override
  public void tryConnectAndReadTarget(String target) {
    // S3 connectivity is validated at S3Utilities initialization time via HeadBucket
  }

  @Override
  public NfsFileTreeNode createFileTree(
      String combinedPath, String nfsOrder, NfsFileStore activeFilestore) throws IOException {

    NfsFileTreeNode rootNode = new NfsFileTreeNode();
    NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsOrder);
    rootNode.setOrderType(order);
    rootNode.setIsFolder(true);

    String path = StringUtils.isEmpty(combinedPath) ? "" : combinedPath;
    path = stripStartAndEndSlashFromPath(path);

    String rootNodeName = getFileNameFromPath(path);
    rootNode.calculateFileName(rootNodeName);
    rootNode.setNodePath(path);

    List<S3FolderContentItem> s3FolderContentItems = s3Utilities.listFolderContents(path);
    for (S3FolderContentItem item : s3FolderContentItems) {
      rootNode.addNode(getNodeFromS3Item(item, rootNode.getNodePath(), order, activeFilestore));
    }

    return rootNode;
  }

  private static String stripStartAndEndSlashFromPath(String path) {
    return path == null ? "" : StringUtils.strip(path, "/");
  }

  private static String getFileNameFromPath(String path) {
    String stripped = stripStartAndEndSlashFromPath(path);
    String name = StringUtils.substringAfterLast(stripped, "/");
    return name.isEmpty() ? stripped : name;
  }

  private static String joinPath(String parent, String name) {
    return StringUtils.isBlank(parent) ? name : parent + "/" + name;
  }

  protected NfsFileTreeNode getNodeFromS3Item(
      S3FolderContentItem item,
      String rootPath,
      NfsFileTreeOrderType order,
      NfsFileStore activeFilestore) {

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.setOrderType(order);
    node.calculateFileName(item.getName());
    node.setIsFolder(item.isFolder());
    if (!item.isFolder()) {
      node.setFileSize(String.valueOf(item.getSizeInBytes()));
      node.setFileSizeBytes(item.getSizeInBytes());
      node.setModificationDateMillis(item.getLastModified().toEpochMilli());
    }

    String fullPathToTarget = joinPath(rootPath, item.getName());
    node.setNodePath(fullPathToTarget);
    node.calculateLogicPath("/" + fullPathToTarget, activeFilestore);

    return node;
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {
    String path = stripStartAndEndSlashFromPath(nfsTarget.getPath());
    S3FolderContentItem item = s3Utilities.getObjectDetails(path);
    if (item != null && !item.isFolder()) {
      NfsFileDetails details = new NfsFileDetails(item.getName());
      details.setFileSystemFullPath(path);
      details.setFileSystemParentPath(getParentPath(path));
      details.setSize(item.getSizeInBytes());
      return details;
    }
    return null;
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws IOException {
    String path = stripStartAndEndSlashFromPath(nfsTarget.getPath());
    S3FolderContentItem item = s3Utilities.getObjectDetails(path);
    if (item != null && item.isFolder()) {
      NfsFolderDetails folderDetails = new NfsFolderDetails(item.getName());
      folderDetails.setFileSystemFullPath(path);
      folderDetails.setFileSystemParentPath(getParentPath(path));

      List<S3FolderContentItem> contents = s3Utilities.listFolderContents(path);
      for (S3FolderContentItem contentItem : contents) {
        NfsResourceDetails resource = getNfsResourceDetailsFromContentItem(contentItem, path);
        folderDetails.getContent().add(resource);
      }
      return folderDetails;
    }
    return null;
  }

  @NotNull
  private static NfsResourceDetails getNfsResourceDetailsFromContentItem(
      S3FolderContentItem contentItem, String path) {
    NfsResourceDetails resource;
    String fullPath = joinPath(path, contentItem.getName());
    if (contentItem.isFolder()) {
      resource = new NfsFolderDetails(contentItem.getName());
    } else {
      NfsFileDetails fileDetails = new NfsFileDetails(contentItem.getName());
      fileDetails.setSize(contentItem.getSizeInBytes());
      resource = fileDetails;
    }
    resource.setFileSystemFullPath(fullPath);
    resource.setFileSystemParentPath(path);
    return resource;
  }

  private static String getParentPath(String path) {
    String stripped = stripStartAndEndSlashFromPath(path);
    return stripped.contains("/") ? StringUtils.substringBeforeLast(stripped, "/") : "";
  }

  @Override
  public boolean supportsServerSideTransfer() {
    return true;
  }

  @Override
  public String uploadFile(File source, String destDirectoryPath) throws IOException {
    return uploadFile(source, destDirectoryPath, java.util.Collections.emptyMap());
  }

  /**
   * S3-specific overload that attaches user-defined object metadata (e.g. RSpace username, op,
   * record id) to the PutObject request. See {@link
   * com.researchspace.service.aws.S3Utilities#uploadToS3(String, java.io.File, java.util.Map)}.
   *
   * <p>Leading and trailing slashes on {@code destDirectoryPath} are stripped before use, so a
   * filestore path like {@code /my-folder} behaves identically to {@code my-folder}. This keeps
   * uploads consistent with {@link #createFileTree}, which also normalises the path.
   */
  public String uploadFile(
      File source, String destDirectoryPath, java.util.Map<String, String> metadata)
      throws IOException {
    String normalizedDir = stripStartAndEndSlashFromPath(destDirectoryPath);
    if (s3Utilities.isFileInS3(normalizedDir, source.getName())) {
      throw new IOException(
          "File already exists at destination: " + joinPath(normalizedDir, source.getName()));
    }
    s3Utilities.uploadToS3(normalizedDir, source, metadata);
    return joinPath(normalizedDir, source.getName());
  }

  /**
   * S3 override of the batch upload: applies per-record audit metadata derived from {@link
   * WriteAttribution} to each PutObject. Falls through to the no-attribution default when {@code
   * attribution} is null.
   */
  @Override
  public ApiExternalStorageOperationResult uploadFilesToNfs(
      String destinationPath, Map<Long, File> mapRecordIdToFile, WriteAttribution attribution) {
    if (attribution == null) {
      return uploadFilesToNfs(destinationPath, mapRecordIdToFile);
    }
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    for (Map.Entry<Long, File> entry : mapRecordIdToFile.entrySet()) {
      Long recordId = entry.getKey();
      File file = entry.getValue();
      try {
        uploadFile(file, destinationPath, attribution.metadataForRecord(recordId));
        result.add(new ApiExternalStorageOperationInfo(recordId, null, file.getName(), true));
      } catch (Exception e) {
        result.add(
            new ApiExternalStorageOperationInfo(recordId, file.getName(), false, e.getMessage()));
      }
    }
    return result;
  }

  @Override
  public void deleteFile(String absolutePath) throws IOException {
    String path = stripStartAndEndSlashFromPath(absolutePath);
    String folder = getParentPath(path);
    String fileName = getFileNameFromPath(path);
    s3Utilities.deleteFromS3(folder, fileName);
  }

  @Override
  public String createFolder(String absolutePath, Map<String, String> metadata) throws IOException {
    String path = stripStartAndEndSlashFromPath(absolutePath);
    S3FolderContentItem existing = s3Utilities.getObjectDetails(path);
    if (existing != null && !existing.isFolder()) {
      throw new IOException("A file already exists at: " + path);
    }
    s3Utilities.createFolder(path, metadata);
    return path;
  }

  @Override
  public String moveWithin(String sourceAbsolutePath, String destFolderAbsolutePath)
      throws IOException {
    String sourceKey = stripStartAndEndSlashFromPath(sourceAbsolutePath);
    String destFolder = stripStartAndEndSlashFromPath(destFolderAbsolutePath);
    String leafName = getFileNameFromPath(sourceKey);

    S3FolderContentItem sourceDetails = s3Utilities.getObjectDetails(sourceKey);
    if (sourceDetails == null) {
      throw new IOException("Source not found: " + sourceKey);
    }

    if (sourceDetails.isFolder()) {
      if (!s3Utilities.listFolderContents(sourceKey).isEmpty()) {
        throw new IOException("Cannot move a non-empty folder: " + sourceKey);
      }
      String destBase = joinPath(destFolder, leafName);
      if (s3Utilities.getObjectDetails(destBase) != null) {
        throw new IOException("Destination already exists: " + destBase);
      }
      // empty metadata => S3 COPY directive, preserving the placeholder's created-by/created-at
      s3Utilities.copyObjectFromBucket(
          s3Utilities.getBucketName(),
          sourceKey + "/",
          destBase + "/",
          java.util.Collections.emptyMap());
      s3Utilities.deleteObject(sourceKey + "/");
      return destBase;
    }

    // copyObject (not copyObjectFromBucket) adds the 5 GB limit + destination-collision checks
    String destKey = joinPath(destFolder, leafName);
    copyObject(sourceKey, this, destKey, java.util.Collections.emptyMap());
    s3Utilities.deleteObject(sourceKey);
    return destKey;
  }

  @Override
  public DeletableTarget resolveDeletableTarget(String absolutePath) throws IOException {
    String key = stripStartAndEndSlashFromPath(absolutePath);
    S3FolderContentItem details = s3Utilities.getObjectDetails(key);
    if (details == null) {
      throw new IOException("Not found: " + key);
    }
    if (!details.isFolder()) {
      return new DeletableTarget(key, FilestoreAuditMetadata.from(details.getUserMetadata()));
    }
    if (!s3Utilities.listFolderContents(key).isEmpty()) {
      throw new IOException("Cannot delete a non-empty folder: " + key);
    }
    String placeholderKey = key + "/";
    S3FolderContentItem placeholder = s3Utilities.getObjectDetails(placeholderKey);
    return new DeletableTarget(
        placeholderKey,
        FilestoreAuditMetadata.from(placeholder == null ? null : placeholder.getUserMetadata()));
  }

  @Override
  public void deleteByKey(String objectKey) throws IOException {
    s3Utilities.deleteObject(objectKey);
  }

  static final long S3_SINGLE_OP_COPY_LIMIT_BYTES = 5L * 1024L * 1024L * 1024L;

  @Override
  public String copyObject(
      String sourceAbsolutePath, WritableNfsClient destClient, String destAbsolutePath)
      throws IOException {
    return copyObject(
        sourceAbsolutePath, destClient, destAbsolutePath, java.util.Collections.emptyMap());
  }

  /**
   * S3-specific overload that attaches user-defined object metadata to the CopyObject request.
   * Non-empty metadata sets {@code MetadataDirective.REPLACE}; empty preserves source metadata.
   */
  @Override
  public String copyObject(
      String sourceAbsolutePath,
      WritableNfsClient destClient,
      String destAbsolutePath,
      java.util.Map<String, String> metadata)
      throws IOException {
    if (!destClient.supportsServerSideTransfer()) {
      throw new UnsupportedOperationException(
          "Cross-filestore copy from S3 only supports S3 destinations");
    }
    String sourceKey = stripStartAndEndSlashFromPath(sourceAbsolutePath);
    String destKey = stripStartAndEndSlashFromPath(destAbsolutePath);
    S3FolderContentItem sourceDetails = s3Utilities.getObjectDetails(sourceKey);
    if (sourceDetails != null
        && sourceDetails.getSizeInBytes() != null
        && sourceDetails.getSizeInBytes() > S3_SINGLE_OP_COPY_LIMIT_BYTES) {
      throw new IOException(
          "Source object size "
              + sourceDetails.getSizeInBytes()
              + " bytes exceeds S3 single-op CopyObject limit of "
              + S3_SINGLE_OP_COPY_LIMIT_BYTES
              + " bytes (5 GB); multipart copy is not yet supported");
    }
    S3NfsClient destS3 = (S3NfsClient) destClient;
    S3FolderContentItem destDetails = destS3.getObjectDetails(destKey);
    if (destDetails != null && !destDetails.isFolder()) {
      throw new IOException("File already exists at destination: " + destKey);
    }
    destS3.copyObjectFromBucket(s3Utilities.getBucketName(), sourceKey, destKey, metadata);
    return destKey;
  }

  @Override
  public NfsFileDetails queryNfsFileForDownload(NfsTarget target) throws IOException {
    log.debug("file download request for: {}", target.getPath());
    String pathToDownload = stripStartAndEndSlashFromPath(target.getPath());

    boolean filePresent = s3Utilities.isFileInS3("", pathToDownload);
    if (!filePresent) {
      throw new IllegalArgumentException("file not found in S3: " + pathToDownload);
    }

    File tmpFile = File.createTempFile("downloaded", ".tmp");
    s3Utilities.downloadFromS3(pathToDownload, tmpFile);

    NfsFileDetails nfsDetails = new NfsFileDetails(getFileNameFromPath(pathToDownload));
    nfsDetails.setRemoteInputStream(new TempFileDeletingInputStream(tmpFile));
    return nfsDetails;
  }

  S3FolderContentItem getObjectDetails(String key) {
    return s3Utilities.getObjectDetails(key);
  }

  void copyObjectFromBucket(
      String sourceBucketName,
      String sourceKey,
      String destKey,
      java.util.Map<String, String> metadata) {
    s3Utilities.copyObjectFromBucket(sourceBucketName, sourceKey, destKey, metadata);
  }

  /** InputStream wrapper that deletes its backing temp file when the stream is closed. */
  private static class TempFileDeletingInputStream extends FilterInputStream {
    private final File tempFile;

    TempFileDeletingInputStream(File tempFile) throws FileNotFoundException {
      super(new FileInputStream(tempFile));
      this.tempFile = tempFile;
    }

    @Override
    public void close() throws IOException {
      try {
        super.close();
      } finally {
        tempFile.delete();
      }
    }
  }
}

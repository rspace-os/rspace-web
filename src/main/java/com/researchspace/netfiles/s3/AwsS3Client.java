package com.researchspace.netfiles.s3;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsResourceDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AwsS3Client extends NfsAbstractClient implements NfsClient {

  final S3Utilities s3Utilities;

  public AwsS3Client(String currentUsername, S3Utilities s3Utilities) {
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

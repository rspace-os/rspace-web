package com.researchspace.netfiles.s3;

import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.netfiles.NfsAbstractClient;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsException;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsFileTreeOrderType;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

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
  public void tryConnectAndReadTarget(String target) throws NfsException, MalformedURLException {}

  @Override
  public NfsFileTreeNode createFileTree(
      String combinedPath, String nfsOrder, NfsFileStore activeFilestore) throws IOException {

    NfsFileTreeNode rootNode = new NfsFileTreeNode();
    NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsOrder);
    rootNode.setOrderType(order);
    rootNode.setIsFolder(true);

    String path = StringUtils.isEmpty(combinedPath) ? "" : combinedPath;
    path = stripStartAndEndSlashFromPath(path);

    String rootNodeName;
    if (path.lastIndexOf('/') != -1) {
      rootNodeName = StringUtils.substringAfterLast(path, "/");
    } else {
      rootNodeName = path;
    }
    rootNode.calculateFileName(rootNodeName);
    rootNode.setNodePath(path);

    List<S3FolderContentItem> s3FolderContentItems = s3Utilities.listFolderContents(path);
    for (S3FolderContentItem item : s3FolderContentItems) {
      rootNode.addNode(getNodeFromS3Item(item, rootNode.getNodePath(), order, activeFilestore));
    }

    return rootNode;
  }

  private static String stripStartAndEndSlashFromPath(String path) {
    path = Strings.CS.removeStart(path, "/");
    path = Strings.CS.removeEnd(path, "/");
    return path;
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
      node.setFileSize("" + item.getSizeInBytes());
      node.setFileSizeBytes(item.getSizeInBytes());
      node.setModificationDateMillis(item.getLastModified().toEpochMilli());
    }

    String fullPathToTarget =
        StringUtils.isBlank(rootPath)
            ? item.getName()
            : String.format("%s/%s", rootPath, item.getName());
    node.setNodePath(fullPathToTarget);
    node.calculateLogicPath("/" + fullPathToTarget, activeFilestore);

    return node;
  }

  @Override
  public NfsFileDetails queryForNfsFile(NfsTarget nfsTarget) {

    return null;
  }

  @Override
  public NfsFolderDetails queryForNfsFolder(NfsTarget nfsTarget) throws IOException {
    return null;
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
    NfsFileDetails nfsDetails = new NfsFileDetails("downloaded.test");
    nfsDetails.setRemoteInputStream(new FileInputStream(tmpFile));
    return nfsDetails;
  }
}

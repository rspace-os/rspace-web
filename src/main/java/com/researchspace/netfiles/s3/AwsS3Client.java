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
import org.apache.commons.lang.StringUtils;

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
      String target, String nfsOrder, NfsFileStore activeFilestore) throws IOException {

    NfsFileTreeNode rootNode = new NfsFileTreeNode();
    NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsOrder);
    rootNode.setOrderType(order);

    String pathToList = StringUtils.isEmpty(target) ? "" : target;
    // ui may be passing '/' for root folder, but s3 paths start without it
    if (pathToList.startsWith("/")) {
      pathToList = pathToList.substring(1);
    }
    List<S3FolderContentItem> s3FolderContentItems = s3Utilities.listFolderContents(pathToList);
    for (S3FolderContentItem item : s3FolderContentItems) {
      rootNode.addNode(getNodeFromS3Item(item, order, activeFilestore));
    }
    rootNode.setNodePath(target);

    return rootNode;
  }

  protected NfsFileTreeNode getNodeFromS3Item(
      S3FolderContentItem item, NfsFileTreeOrderType order, NfsFileStore activeFilestore) {

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.setOrderType(order);
    node.calculateFileName(item.getName());
    node.setIsFolder(item.isFolder());
    if (!item.isFolder()) {
      node.setFileSize("" + item.getSizeInBytes());
      node.setFileSizeBytes(item.getSizeInBytes());
      node.setModificationDateMillis(item.getLastModified().toEpochMilli());
    }

    node.setNodePath(item.getName());
    node.calculateLogicPath(item.getName(), activeFilestore);

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

    boolean filePresent = s3Utilities.isFileInS3("", target.getPath());
    if (!filePresent) {
      throw new IllegalArgumentException("file not found in S3: " + target.getPath());
    }

    File tmpFile = File.createTempFile("downloaded", ".tmp");
    s3Utilities.downloadFromS3(target.getPath(), tmpFile);
    NfsFileDetails nfsDetails = new NfsFileDetails("downloaded.test");
    nfsDetails.setRemoteInputStream(new FileInputStream(tmpFile));
    return nfsDetails;
  }
}

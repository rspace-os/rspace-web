package com.researchspace.netfiles.aws3s;

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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.List;

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
      String target, String nfsOrder, NfsFileStore selectedUserFolder) throws IOException {

    NfsFileTreeNode rootNode = new NfsFileTreeNode();
    NfsFileTreeOrderType order = NfsFileTreeOrderType.parseOrderTypeString(nfsOrder);
    rootNode.setOrderType(order);

    List<S3FolderContentItem> s3FolderContentItems = s3Utilities.listFolderContents(target);
    for (S3FolderContentItem item : s3FolderContentItems) {
      rootNode.addNode(getNodeFromS3Item(item, order));
    }
    rootNode.setNodePath(target);

    return rootNode;
  }

  private NfsFileTreeNode getNodeFromS3Item(S3FolderContentItem item, NfsFileTreeOrderType order) {

    NfsFileTreeNode node = new NfsFileTreeNode();
    node.setOrderType(order);
    node.calculateFileName(item.getName());
    node.setIsFolder(item.isFolder());
    if (!item.isFolder()) {
      node.setFileSize("" + item.getSizeInBytes());
      node.setFileSizeBytes(item.getSizeInBytes());
    }
    node.calculateLogicPath(item.getName(), null);

    node.setNodePath(item.getName());
    node.setModificationDateMillis((new Date()).getTime()); // FIXME
    node.calculateLogicPath(item.getName(), null);

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
}

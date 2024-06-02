package com.researchspace.webapp.integrations.box;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFileVersion;
import com.box.sdk.BoxFolder;
import java.util.Collection;

/** The class is collecting methods making real calls to Box API, to facilitate testing. */
public class BoxConnector {

  private static final String[] FILE_FILEDS =
      new String[] {
        "id", "name", "description", "shared_link", "owned_by",
        "sha1", "size", "created_at", "file_version", "version_number"
      };
  private static final String[] FOLDER_FIELDS =
      new String[] {"id", "name", "description", "shared_link", "owned_by"};

  public com.box.sdk.BoxFile.Info getBoxFileInfo(BoxAPIConnection apiConnection, String boxId)
      throws BoxAPIException {
    BoxFile boxFile = new BoxFile(apiConnection, boxId);
    return boxFile.getInfo(FILE_FILEDS);
  }

  public com.box.sdk.BoxFolder.Info getBoxFolderInfo(BoxAPIConnection apiConnection, String boxId)
      throws BoxAPIException {
    BoxFolder boxFolder = new BoxFolder(apiConnection, boxId);
    return boxFolder.getInfo(FOLDER_FIELDS);
  }

  public Collection<BoxFileVersion> getBoxFileVersionHistory(BoxFile boxFile)
      throws BoxAPIException {
    return boxFile.getVersions();
  }

  public BoxAPIConnection createBoxAPIConnection(
      String clientId, String clientSecret, String authorizationCode) throws BoxAPIException {
    return new BoxAPIConnection(clientId, clientSecret, authorizationCode);
  }

  public BoxAPIConnection restoreBoxAPIConnection(
      String clientId, String clientSecret, String apiConnectionState) {
    return BoxAPIConnection.restore(clientId, clientSecret, apiConnectionState);
  }

  public boolean testBoxConnection(BoxAPIConnection apiConnection) throws BoxAPIException {
    BoxFolder rootFolder = BoxFolder.getRootFolder(apiConnection);
    rootFolder.getInfo().getName();
    return true;
  }
}

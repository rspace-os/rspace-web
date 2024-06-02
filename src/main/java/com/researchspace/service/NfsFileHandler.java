package com.researchspace.service;

import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import java.io.File;
import java.io.IOException;

/** Methods for accessing files on institutional filestores. */
public interface NfsFileHandler {

  /**
   * Downloads file to RSpace server
   *
   * @return details of downloaded file (with localFile pointing to File on RSpace server)
   * @throws IOException if file can't be retrieved or doesn't exist
   */
  NfsFileDetails downloadNfsFileToRSpace(NfsTarget nfsTarget, NfsClient nfsClient)
      throws IOException;

  NfsFileDetails downloadNfsFileToRSpace(NfsTarget nfsTarget, NfsClient nfsClient, File targetDir)
      throws IOException;

  NfsFileDetails getCurrentPath(NfsTarget nfsTarget, NfsClient nfsClient);

  /**
   * @return folder details
   * @throws IOException if folder can't be retrieved or doesn't exist
   */
  NfsFolderDetails retireveNfsFolderDetails(NfsTarget nfsTarget, NfsClient nfsClient)
      throws IOException;

  File createTempDirForNfsDownload() throws IOException;
}

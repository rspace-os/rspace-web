package com.researchspace.service.impl;

import com.researchspace.core.util.IoUtils;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.NfsFileHandler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@Component("nfsFileHandler")
public class NfsFileHandlerImpl implements NfsFileHandler {

  private static final String NFS_DOWNLOAD_TEMP_DIR_PREFIX = "rspace_nfs";

  @Override
  public NfsFileDetails downloadNfsFileToRSpace(NfsTarget nfsTarget, NfsClient nfsClient)
      throws IOException {
    return downloadNfsFileToRSpace(nfsTarget, nfsClient, null);
  }

  @Override
  public NfsFileDetails downloadNfsFileToRSpace(
      NfsTarget nfsTarget, NfsClient nfsClient, File targetDir) throws IOException {
    if (targetDir == null) {
      targetDir = createTempDirForNfsDownload();
    }

    NfsFileDetails nfsFileDetails = null;
    InputStream is = null;
    synchronized (nfsClient) {
      try {
        nfsFileDetails = nfsClient.queryNfsFileForDownload(nfsTarget);
        is = nfsFileDetails.getRemoteInputStream();

        File targetFile = new File(targetDir, nfsFileDetails.getName());
        FileUtils.copyInputStreamToFile(is, targetFile);
        nfsFileDetails.setLocalFile(targetFile);

      } finally {
        IOUtils.closeQuietly(is);
        nfsClient.releaseNfsFileAfterDownload(nfsFileDetails);
      }
    }
    return nfsFileDetails;
  }

  @Override
  public NfsFileDetails getCurrentPath(NfsTarget nfsTarget, NfsClient nfsClient) {
    return nfsClient.queryForNfsFile(nfsTarget);
  }

  @Override
  public NfsFolderDetails retireveNfsFolderDetails(NfsTarget nfsTarget, NfsClient nfsClient)
      throws IOException {
    return nfsClient.queryForNfsFolder(nfsTarget);
  }

  @Override
  public File createTempDirForNfsDownload() throws IOException {
    Path secureTmpDir = IoUtils.createOrGetSecureTempDirectory();
    return Files.createTempDirectory(secureTmpDir, NFS_DOWNLOAD_TEMP_DIR_PREFIX).toFile();
  }
}

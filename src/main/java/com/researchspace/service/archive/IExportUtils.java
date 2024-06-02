package com.researchspace.service.archive;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.model.User;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletResponse;

/** Utility methods for export functionality. */
public interface IExportUtils {

  /**
   * Method to display a exported file to HTTP response stream.
   *
   * @param fileStoreFilename The filestore name of the exported file (<em>not</em> the display
   *     name).
   * @param user The authenticated user
   * @param res The HttpServletResponse to write to.
   */
  void display(String fileStoreFilename, User user, HttpServletResponse res)
      throws IOException, URISyntaxException;

  /**
   * Creates a thumbnail image for the exported file
   *
   * @param fileStoreFilename The filestore name of the exported file (<em>not</em> the display
   *     name)
   * @param user The authenticated user
   * @return An {@link ConversionResult} of the thumbnail, which may or may not have succeeded.
   * @throws URISyntaxException
   * @throws IOException
   */
  ConversionResult createThumbnailForExport(String fileStoreFilename, User user)
      throws URISyntaxException, IOException;

  File createFolder(String path) throws IOException;

  void createFolder(File dir) throws IOException;
}

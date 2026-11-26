package com.researchspace.export.pdf;

import java.io.IOException;

public interface ImageRetrieverHelper {

  /**
   * Given an img src tag from an RSpace document, will retrieve it as a byte [].
   *
   * @param imgSrcValue
   * @param exportConfig
   * @return
   */
  byte[] getImageBytesFromImgSrc(String imgSrcValue, ExportToFileConfig exportConfig)
      throws IOException;

  /**
   * @return the image exported in place of one that cannot be retrieved, or {@code null} if even
   *     that cannot be loaded
   */
  byte[] getPlaceholderImage();
}

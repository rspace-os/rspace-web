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
}

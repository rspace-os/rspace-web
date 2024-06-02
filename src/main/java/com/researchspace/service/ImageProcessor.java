package com.researchspace.service;

import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import java.io.IOException;
import java.util.Optional;

/** Top-level handler for image processing operations */
public interface ImageProcessor {

  /**
   * Rotates and persists changes to an ECatImage, handling thumbnails, working copies and original
   * files.
   *
   * @param ecatImage
   * @param timesToRotate
   * @param subject
   * @throws IOException
   */
  Optional<EcatImage> rotate(EcatImage ecatImage, byte timesToRotate, User subject)
      throws IOException;

  /**
   * Converts thumbnails and working images from ImageBlobs to FileProperties, then sets ImageBlobs
   * to null
   *
   * @param originalFileName
   * @param user
   * @param image
   * @throws IOException
   */
  public void transformImageBlobToFileProperty(String originalFileName, User user, EcatImage image)
      throws IOException;
}
